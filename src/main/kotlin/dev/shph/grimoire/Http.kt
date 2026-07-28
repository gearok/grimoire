package dev.shph.grimoire

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.freemarker.FreeMarker
import io.ktor.server.freemarker.FreeMarkerContent
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.http.content.staticResources
import kotlinx.serialization.json.Json
import dev.shph.grimoire.model.MagicSchool
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.repository.ElasticsearchUnavailableException
import dev.shph.grimoire.repository.SpellRepository
import dev.shph.grimoire.view.toDetailView
import dev.shph.grimoire.view.toIndexView
import freemarker.cache.ClassTemplateLoader
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.Serializable

fun Application.configureHttp(repository: SpellRepository, json: Json = Json) {
    install(CallLogging)
    install(AutoHeadResponse)
    install(Compression) { gzip() }
    install(ContentNegotiation) { json(json) }
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request body"))
        }
        exception<ElasticsearchUnavailableException> { call, cause ->
            call.application.environment.log.error("Elasticsearch request failed", cause)
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse("Spell storage is temporarily unavailable"),
            )
        }
    }

    routing {
        staticResources("/static", "static")

        get("/") {
            call.respondRedirect("/spells")
        }

        route("/spells") {
            get {
                val criteria = call.searchCriteria()
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid spell search parameters"),
                )
                val result = repository.search(criteria)
                val view = result.toIndexView(criteria)
                if (call.request.headers["HX-Request"].equals("true", ignoreCase = true)) {
                    call.response.header("Vary", "HX-Request")
                    call.respond(FreeMarkerContent("spells/results.html", mapOf("view" to view)))
                } else {
                    call.respond(FreeMarkerContent("spells/index.html", mapOf("view" to view)))
                }
            }

            get("/{id}") {
                val id = call.parameters["id"]?.takeIf { it.isNotBlank() }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing spell id"))
                val spell = repository.findById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Spell not found"))
                call.respond(
                    FreeMarkerContent(
                        "spells/detail.html",
                        mapOf("view" to spell.toDetailView()),
                    ),
                )
            }
        }

        route("/api/spells") {
            post {
                val spell = call.receive<Spell>()
                .also(::validateSpell)
                repository.save(spell)
                call.response.header(HttpHeaders.Location, "/spells/${spell.id}")
                call.respond(HttpStatusCode.Created, spell)
            }

            get("/{id}") {
                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing spell id"))
                repository.findById(id)?.let { call.respond(it) }
                    ?: call.respond(HttpStatusCode.NotFound, ErrorResponse("Spell not found"))
            }
        }
    }
}

private fun ApplicationCall.searchCriteria(): SpellSearch? {
    val levelValues = queryValues("level")
    val levels = levelValues.mapNotNull(String::toIntOrNull)
    if (levels.size != levelValues.size || levels.any { it !in 0..9 }) return null

    val pageValue = request.queryParameters["page"]?.takeIf(String::isNotBlank)
    val page = pageValue?.toIntOrNull() ?: 1
    if (pageValue != null && pageValue.toIntOrNull() == null) return null
    if (page < 1) return null

    val schoolValues = queryValues("school")
    val schools = schoolValues.mapNotNull(MagicSchool::fromSlug)
    if (schools.size != schoolValues.size) return null

    return SpellSearch(
        query = request.queryParameters["q"]?.trim()?.takeIf(String::isNotEmpty),
        levels = levels.toSet(),
        schools = schools.toSet(),
        characterClasses = queryValues("class").map(String::lowercase).toSet(),
        page = page,
    )
}

private fun ApplicationCall.queryValues(name: String): List<String> =
    request.queryParameters.getAll(name)
        .orEmpty()
        .flatMap { it.split(',') }
        .map(String::trim)
        .filter(String::isNotEmpty)

private fun validateSpell(spell: Spell) {
    require(spell.sourceUrl.startsWith("https://")) { "sourceUrl must be an HTTPS URL" }
    require(spell.components.material || spell.components.materialDescription == null) {
        "materialDescription requires a material component"
    }
}

@Serializable
data class ErrorResponse(val error: String)
