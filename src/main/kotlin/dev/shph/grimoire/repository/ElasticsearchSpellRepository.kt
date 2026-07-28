package dev.shph.grimoire.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.JsonObjectBuilder

class ElasticsearchSpellRepository(
    private val client: HttpClient,
    private val json: Json,
    baseUrl: String,
    private val indexName: String,
) : SpellRepository {
    private val baseUrl = baseUrl.trimEnd('/')
    private val initializationMutex = Mutex()

    @Volatile
    private var initialized = false

    override suspend fun search(criteria: SpellSearch): SpellSearchResult {
        ensureIndex()
        val response = request("search spells") {
            client.get("$baseUrl/$indexName/_search") {
                contentType(ContentType.Application.Json)
                setBody(buildSearchRequest(criteria))
            }
        }
        if (response.status != HttpStatusCode.OK) {
            throw ElasticsearchUnavailableException("Elasticsearch search returned ${response.status}")
        }
        val payload = json.decodeFromString<ElasticsearchSearchResponse>(response.body())
        return SpellSearchResult(
            spells = payload.hits.hits.map { it.source },
            total = payload.hits.total.value,
            page = criteria.page,
            pageSize = criteria.pageSize,
        )
    }

    override suspend fun findById(id: String): Spell? {
        ensureIndex()
        val response = request("load spell") { client.get("$baseUrl/$indexName/_doc/$id") }
        return when (response.status) {
            HttpStatusCode.OK -> json.decodeFromString<ElasticsearchGetResponse>(response.body()).source
            HttpStatusCode.NotFound -> null
            else -> throw ElasticsearchUnavailableException("Elasticsearch get returned ${response.status}")
        }
    }

    override suspend fun save(spell: Spell) {
        ensureIndex()
        val response = request("save spell") {
            client.put("$baseUrl/$indexName/_doc/${spell.id}") {
                parameter("refresh", "wait_for")
                contentType(ContentType.Application.Json)
                setBody(spell)
            }
        }
        if (response.status !in setOf(HttpStatusCode.OK, HttpStatusCode.Created)) {
            throw ElasticsearchUnavailableException("Elasticsearch index returned ${response.status}")
        }
    }

    private suspend fun ensureIndex() {
        if (initialized) return
        initializationMutex.withLock {
            if (initialized) return
            val head = request("check index") { client.head("$baseUrl/$indexName") }
            when (head.status) {
                HttpStatusCode.OK -> initialized = true
                HttpStatusCode.NotFound -> {
                    val create = request("create index") {
                        client.put("$baseUrl/$indexName") {
                            contentType(ContentType.Application.Json)
                            setBody(indexDefinition())
                        }
                    }
                    if (create.status !in setOf(HttpStatusCode.OK, HttpStatusCode.Created)) {
                        throw ElasticsearchUnavailableException("Elasticsearch index creation returned ${create.status}")
                    }
                    initialized = true
                }
                else -> throw ElasticsearchUnavailableException("Elasticsearch index check returned ${head.status}")
            }
        }
    }

    private suspend fun request(operation: String, block: suspend () -> HttpResponse) =
        try {
            block()
        } catch (cause: Exception) {
            throw ElasticsearchUnavailableException("Could not $operation", cause)
        }

    internal fun buildSearchRequest(criteria: SpellSearch): JsonObject {
        val searchText = criteria.query?.trim()?.takeIf(String::isNotEmpty)
        val scoringQueries = searchText?.let(::textQueries) ?: JsonArray(emptyList())
        val filters = buildJsonArray {
            if (criteria.levels.isNotEmpty()) {
                add(termsQuery("level", criteria.levels.sorted().map(::JsonPrimitive)))
            }
            if (criteria.schools.isNotEmpty()) {
                add(termsQuery(
                    "school",
                    criteria.schools.sortedBy { it.slug }.map { JsonPrimitive(it.slug) },
                ))
            }
            if (criteria.characterClasses.isNotEmpty()) {
                add(termsQuery(
                    "classes.name",
                    criteria.characterClasses.sorted().map { JsonPrimitive(it.lowercase()) },
                ))
            }
        }
        val query = if (scoringQueries.isEmpty() && filters.isEmpty()) {
            buildJsonObject { putJsonObject("match_all") {} }
        } else {
            buildJsonObject {
                putJsonObject("bool") {
                    if (scoringQueries.isNotEmpty()) {
                        put("should", scoringQueries)
                        put("minimum_should_match", 1)
                    }
                    if (filters.isNotEmpty()) put("filter", filters)
                }
            }
        }
        return buildJsonObject {
            put("from", criteria.offset)
            put("size", criteria.pageSize)
            put("query", query)
            put("sort", buildJsonArray {
                if (searchText != null) {
                    add(buildJsonObject { put("_score", "desc") })
                }
                add(buildJsonObject { put("level", "asc") })
                add(buildJsonObject { put("name.ru.keyword", "asc") })
            })
        }
    }

    private fun textQueries(query: String) = buildJsonArray {
        // Matches an incomplete final token, e.g. "огнен ша" -> "Огненный шар".
        add(multiMatch(
            query = query,
            type = "bool_prefix",
            fields = listOf("name.ru^12", "name.en^10", "aliases^6"),
        ))

        // Tolerates misspellings in a completed name.
        add(multiMatch(
            query = query,
            type = "best_fields",
            fields = listOf("name.ru^8", "name.en^7", "aliases^4"),
            fuzziness = "AUTO",
            prefixLength = 1,
        ))

        // Rules text remains searchable, but contributes much less to the score.
        add(multiMatch(
            query = query,
            type = "best_fields",
            fields = listOf("description^1", "higherLevels^0.5"),
        ))
    }

    private fun multiMatch(
        query: String,
        type: String,
        fields: List<String>,
        fuzziness: String? = null,
        prefixLength: Int? = null,
    ) = buildJsonObject {
        putJsonObject("multi_match") {
            put("query", query)
            put("type", type)
            fuzziness?.let { put("fuzziness", it) }
            prefixLength?.let { put("prefix_length", it) }
            putJsonArray("fields") {
                fields.forEach { add(JsonPrimitive(it)) }
            }
        }
    }

    private fun termsQuery(field: String, values: List<JsonPrimitive>) =
        buildJsonObject {
            putJsonObject("terms") {
                put(field, JsonArray(values))
            }
        }

    private fun indexDefinition() = buildJsonObject {
        putJsonObject("mappings") {
            put("dynamic", "strict")
            putJsonObject("properties") {
                keyword("id")
                keyword("slug")
                objectField("name") {
                    textWithKeyword("ru")
                    textWithKeyword("en")
                }
                text("aliases")
                integer("level")
                keyword("school")
                objectField("castingTime") {
                    text("text")
                    keyword("type")
                    text("reactionTrigger")
                }
                text("range")
                objectField("components") {
                    bool("verbal")
                    bool("somatic")
                    bool("material")
                    text("materialDescription")
                    integer("materialCostGp")
                    bool("materialConsumed")
                }
                text("duration")
                bool("concentration")
                bool("ritual")
                objectField("classes") {
                    keyword("name", normalizer = "lowercase")
                    bool("optional")
                    keyword("sourceCode")
                }
                objectField("subclasses") {
                    keyword("name", normalizer = "lowercase")
                    keyword("parentClass", normalizer = "lowercase")
                }
                text("description")
                text("higherLevels")
                keyword("damageTypes", normalizer = "lowercase")
                objectField("sources") {
                    keyword("code")
                    text("title")
                    integer("page")
                    keyword("edition")
                }
                keyword("sourceUrl")
            }
        }
        putJsonObject("settings") {
            putJsonObject("analysis") {
                putJsonObject("normalizer") {
                    putJsonObject("lowercase") {
                        put("type", "custom")
                        put("filter", JsonArray(listOf(JsonPrimitive("lowercase"))))
                    }
                }
            }
        }
    }

    private fun JsonObjectBuilder.keyword(
        name: String,
        normalizer: String? = null,
    ) = putJsonObject(name) {
        put("type", "keyword")
        normalizer?.let { put("normalizer", it) }
    }

    private fun JsonObjectBuilder.text(name: String) =
        putJsonObject(name) { put("type", "text") }

    private fun JsonObjectBuilder.textWithKeyword(name: String) =
        putJsonObject(name) {
            put("type", "text")
            putJsonObject("fields") {
                putJsonObject("keyword") { put("type", "keyword") }
            }
        }

    private fun JsonObjectBuilder.integer(name: String) =
        putJsonObject(name) { put("type", "integer") }

    private fun JsonObjectBuilder.bool(name: String) =
        putJsonObject(name) { put("type", "boolean") }

    private fun JsonObjectBuilder.objectField(
        name: String,
        properties: JsonObjectBuilder.() -> Unit,
    ) = putJsonObject(name) {
        put("type", "object")
        putJsonObject("properties", properties)
    }
}

@Serializable
private data class ElasticsearchSearchResponse(val hits: SearchHits)

@Serializable
private data class SearchHits(
    val total: TotalHits,
    val hits: List<SearchHit>,
)

@Serializable
private data class TotalHits(val value: Long)

@Serializable
private data class SearchHit(
    @SerialName("_source")
    val source: Spell,
)

@Serializable
private data class ElasticsearchGetResponse(
    @SerialName("_source")
    val source: Spell,
)
