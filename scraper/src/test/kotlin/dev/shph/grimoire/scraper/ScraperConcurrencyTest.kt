package dev.shph.grimoire.scraper

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class ScraperConcurrencyTest {
    @Test
    fun `never runs more scrapes than the configured concurrency`() = runBlocking {
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val indexed = mutableListOf<String>()
        val config = ScraperConfig(concurrency = 3, batchSize = 5, delayMs = 0)

        val failures = scrapeConcurrently(
            urls = (1..12).map { "page-$it" },
            config = config,
            scrape = { url ->
                val current = active.incrementAndGet()
                maximumActive.accumulateAndGet(current, ::maxOf)
                try {
                    delay(20)
                    url
                } finally {
                    active.decrementAndGet()
                }
            },
            indexBatch = indexed::addAll,
        )

        assertEquals(0, failures)
        assertEquals(3, maximumActive.get())
        assertEquals(12, indexed.size)
    }
}
