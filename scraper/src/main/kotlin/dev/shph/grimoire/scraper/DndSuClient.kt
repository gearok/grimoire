package dev.shph.grimoire.scraper

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DndSuClient(
    private val userAgent: String,
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) {
    fun spellUrls(sitemapUrl: String): List<String> {
        val sitemap = Jsoup.parse(fetch(sitemapUrl), "", Parser.xmlParser())
        return sitemap.select("url > loc")
            .map { it.text().trim() }
            .filter(SPELL_URL::matches)
            .distinct()
    }

    fun fetch(url: String): String {
        var lastFailure: Exception? = null
        repeat(3) { attempt ->
            try {
                val request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(45))
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html, application/xml;q=0.9")
                    .GET()
                    .build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() in 200..299) return response.body()
                if (response.statusCode() !in setOf(429, 500, 502, 503, 504)) {
                    throw IOException("GET $url returned HTTP ${response.statusCode()}")
                }
                lastFailure = IOException("GET $url returned HTTP ${response.statusCode()}")
            } catch (failure: IOException) {
                lastFailure = failure
            }
            if (attempt < 2) Thread.sleep(1_000L shl attempt)
        }
        throw lastFailure ?: IOException("GET $url failed")
    }

    private companion object {
        val SPELL_URL = Regex("""https://dnd\.su/spells/\d+-[a-z0-9_-]+/""")
    }
}
