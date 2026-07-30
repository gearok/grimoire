package dev.shph.grimoire.scraper

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import kotlin.time.Duration.Companion.nanoseconds

class DndSuClient(
    private val userAgent: String,
    delayMs: Long,
    private val http: HttpClient = HttpClient(CIO) {
        followRedirects = true
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 45_000
        }
    },
) : AutoCloseable {
    private val requestRateLimiter = RequestRateLimiter(delayMs)

    suspend fun spellUrls(sitemapUrl: String): List<String> {
        val sitemap = Jsoup.parse(fetch(sitemapUrl), "", Parser.xmlParser())
        return sitemap.select("url > loc")
            .map { it.text().trim() }
            .filter(SPELL_URL::matches)
            .distinct()
    }

    suspend fun monsterUrls(sitemapUrl: String): List<String> {
        val sitemap = Jsoup.parse(fetch(sitemapUrl), "", Parser.xmlParser())
        return sitemap.select("url > loc")
            .map { it.text().trim() }
            .filter(MONSTER_URL::matches)
            .distinct()
    }

    suspend fun fetch(url: String): String {
        var lastFailure: Exception? = null
        repeat(3) { attempt ->
            requestRateLimiter.awaitTurn()
            try {
                val response = http.get(url) {
                    header(HttpHeaders.UserAgent, userAgent)
                    header(HttpHeaders.Accept, "text/html, application/xml;q=0.9")
                }
                val body = response.bodyAsText()
                val status = response.status.value
                if (status in 200..299) return body
                if (status !in RETRYABLE_STATUSES) throw NonRetryableHttpException(url, status)
                lastFailure = IOException("GET $url returned HTTP $status")
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: NonRetryableHttpException) {
                throw failure
            } catch (failure: Exception) {
                lastFailure = failure
            }
            if (attempt < 2) delay(1_000L shl attempt)
        }
        throw lastFailure ?: IOException("GET $url failed")
    }

    override fun close() {
        http.close()
    }

    private companion object {
        val SPELL_URL = Regex("""https://dnd\.su/spells/\d+-[a-z0-9_-]+/""")
        val MONSTER_URL = Regex("""https://dnd\.su/bestiary/\d+-[a-z0-9_-]+/""")
        val RETRYABLE_STATUSES = setOf(429, 500, 502, 503, 504)
    }
}

private class NonRetryableHttpException(url: String, status: Int) :
    IOException("GET $url returned HTTP $status")

private class RequestRateLimiter(delayMs: Long) {
    private val intervalNanos = delayMs * NANOS_PER_MILLISECOND
    private val mutex = Mutex()
    private var nextRequestAtNanos = 0L

    suspend fun awaitTurn() {
        mutex.withLock {
            val waitNanos = nextRequestAtNanos - System.nanoTime()
            if (waitNanos > 0) delay(waitNanos.nanoseconds)
            nextRequestAtNanos = System.nanoTime() + intervalNanos
        }
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
