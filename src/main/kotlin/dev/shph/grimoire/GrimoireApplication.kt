package dev.shph.grimoire

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import dev.shph.grimoire.repository.ElasticsearchSpellRepository
import dev.shph.grimoire.seed.SpellSeeder

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    val client = HttpClient(CIO) {
        install(ClientContentNegotiation) { json(json) }
    }
    val repository = ElasticsearchSpellRepository(
        client = client,
        json = json,
        baseUrl = System.getenv("ELASTICSEARCH_URL")
            ?: environment.config.propertyOrNull("elasticsearch.url")?.getString()
            ?: "http://localhost:9200",
        indexName = System.getenv("ELASTICSEARCH_INDEX")
            ?: environment.config.propertyOrNull("elasticsearch.index")?.getString()
            ?: "spells-v1",
    )
    val seedDataEnabled = System.getenv("SEED_DATA_ENABLED")?.toBooleanStrictOrNull()
        ?: environment.config.propertyOrNull("seed.enabled")?.getString()?.toBooleanStrictOrNull()
        ?: true

    if (seedDataEnabled) {
        val seeder = SpellSeeder.fromResource(repository, json)
        monitor.subscribe(ApplicationStarted) {
            launch {
                runCatching { seeder.seedMissing() }
                    .onSuccess { result ->
                        environment.log.info(
                            "Spell seed complete: {} inserted, {} already present",
                            result.inserted,
                            result.existing,
                        )
                    }
                    .onFailure { cause ->
                        environment.log.error(
                            "Could not seed spells; the application will continue without seed data",
                            cause,
                        )
                    }
            }
        }
    }
    monitor.subscribe(ApplicationStopped) { client.close() }
    configureHttp(repository, json)
}
