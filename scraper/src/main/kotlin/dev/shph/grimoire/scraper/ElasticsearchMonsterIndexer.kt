package dev.shph.grimoire.scraper

import dev.shph.grimoire.model.Monster
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class ElasticsearchMonsterIndexer(
    elasticsearchUrl: String,
    private val indexName: String,
    private val mapper: ObjectMapper = jacksonObjectMapper(),
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) {
    private val baseUrl = elasticsearchUrl.trimEnd('/')

    fun ensureIndex() {
        val response = request("HEAD", "/${encoded(indexName)}")
        when (response.statusCode()) {
            200 -> return
            404 -> {
                val mapping = javaClass.getResource("/monsters-index.json")?.readText()
                    ?: error("monsters-index.json is missing")
                checkSuccess(request("PUT", "/${encoded(indexName)}", mapping), "create monster index")
            }
            else -> fail(response, "check monster index")
        }
    }

    fun clearIndex(): Long {
        val response = request(
            "POST",
            "/${encoded(indexName)}/_delete_by_query?conflicts=proceed&refresh=true",
            """{"query":{"match_all":{}}}""",
        )
        checkSuccess(response, "clear monster index")
        return mapper.readTree(response.body()).path("deleted").longValue()
    }

    fun index(monsters: List<Monster>) {
        if (monsters.isEmpty()) return
        val payload = buildString {
            monsters.forEach { monster ->
                append("""{"index":{"_index":${mapper.writeValueAsString(indexName)},"_id":${mapper.writeValueAsString(monster.id)}}}""")
                append('\n')
                append(mapper.writeValueAsString(monster))
                append('\n')
            }
        }
        val response = request("POST", "/_bulk", payload, "application/x-ndjson")
        checkSuccess(response, "bulk index monsters")
        val result = mapper.readTree(response.body())
        if (result.path("errors").asBoolean()) {
            val failures = result.path("items").mapNotNull { item ->
                val operation = item.path("index")
                operation.path("error").takeUnless(JsonNode::isMissingNode)?.let {
                    "${operation.path("_id").asString()}: ${it.path("reason").asString(it.toString())}"
                }
            }
            throw IOException("Elasticsearch rejected ${failures.size} monster(s): ${failures.take(5).joinToString("; ")}")
        }
    }

    fun refreshIndex() {
        checkSuccess(request("POST", "/${encoded(indexName)}/_refresh"), "refresh monster index")
    }

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        contentType: String = "application/json",
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("$baseUrl$path"))
            .timeout(Duration.ofSeconds(60)).header("Accept", "application/json")
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody())
        } else {
            builder.header("Content-Type", contentType)
                .method(method, HttpRequest.BodyPublishers.ofString(body))
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun checkSuccess(response: HttpResponse<String>, action: String) {
        if (response.statusCode() !in 200..299) fail(response, action)
    }

    private fun fail(response: HttpResponse<String>, action: String): Nothing =
        throw IOException("Could not $action: Elasticsearch returned HTTP ${response.statusCode()}: ${response.body()}")

    private fun encoded(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
