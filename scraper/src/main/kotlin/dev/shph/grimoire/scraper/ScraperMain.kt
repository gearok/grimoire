package dev.shph.grimoire.scraper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

fun main(args: Array<String>) = runBlocking {
    val config = try {
        ScraperConfig.parse(args)
    } catch (_: HelpRequested) {
        printUsage()
        return@runBlocking
    } catch (failure: IllegalArgumentException) {
        System.err.println("Configuration error: ${failure.message}")
        printUsage()
        exitProcess(2)
    }

    val failures = DndSuClient(config.userAgent, config.delayMs).use { client ->
        var failures = 0
        config.content.forEach { content ->
            failures += when (content) {
                ScraperContent.SPELLS -> importSpells(config, client)
                ScraperContent.MONSTERS -> importMonsters(config, client)
            }
        }
        failures
    }
    if (failures > 0) exitProcess(1)
}

private suspend fun importSpells(config: ScraperConfig, client: DndSuClient): Int {
    val parser = DndSuSpellParser()
    val indexer = ElasticsearchSpellIndexer(config.elasticsearchUrl, config.spellsIndexName)
    val urls = limited(client.spellUrls(config.sitemapUrl), config.limit)
    require(urls.isNotEmpty()) { "The sitemap did not contain official spell URLs" }
    println("Found ${urls.size} official spell page(s); destination index is '${config.spellsIndexName}'.")
    withContext(Dispatchers.IO) {
        indexer.ensureIndex()
        println("Cleared ${indexer.clearIndex()} existing document(s) from '${config.spellsIndexName}'.")
    }
    var indexed = 0
    val failures = scrapeConcurrently(
        urls = urls,
        config = config,
        scrape = { url -> parser.parse(client.fetch(url), url) },
        indexBatch = { batch ->
            withContext(Dispatchers.IO) { indexer.index(batch) }
            indexed += batch.size
            println("Indexed $indexed/${urls.size} spells.")
        },
    )
    withContext(Dispatchers.IO) { indexer.refreshIndex() }
    println("Done: indexed $indexed spell(s), $failures failed.")
    return failures
}

private suspend fun importMonsters(config: ScraperConfig, client: DndSuClient): Int {
    val parser = DndSuMonsterParser()
    val indexer = ElasticsearchMonsterIndexer(config.elasticsearchUrl, config.monstersIndexName)
    val urls = limited(client.monsterUrls(config.sitemapUrl), config.limit)
    require(urls.isNotEmpty()) { "The sitemap did not contain official monster URLs" }
    println("Found ${urls.size} official monster page(s); destination index is '${config.monstersIndexName}'.")
    withContext(Dispatchers.IO) {
        indexer.ensureIndex()
        println("Cleared ${indexer.clearIndex()} existing document(s) from '${config.monstersIndexName}'.")
    }
    var indexed = 0
    val failures = scrapeConcurrently(
        urls = urls,
        config = config,
        scrape = { url -> parser.parse(client.fetch(url), url) },
        indexBatch = { batch ->
            withContext(Dispatchers.IO) { indexer.index(batch) }
            indexed += batch.size
            println("Indexed $indexed/${urls.size} monsters.")
        },
    )
    withContext(Dispatchers.IO) { indexer.refreshIndex() }
    println("Done: indexed $indexed monster(s), $failures failed.")
    return failures
}

private fun <T> limited(values: List<T>, limit: Int?) = limit?.let(values::take) ?: values

internal suspend fun <T> scrapeConcurrently(
    urls: List<String>,
    config: ScraperConfig,
    scrape: suspend (String) -> T,
    indexBatch: suspend (List<T>) -> Unit,
): Int = coroutineScope {
    val work = Channel<String>()
    val results = Channel<ScrapeResult<T>>(config.concurrency)
    launch {
        urls.forEach { work.send(it) }
        work.close()
    }
    val workers = List(config.concurrency) {
        launch(Dispatchers.Default) {
            for (url in work) {
                val result: ScrapeResult<T> = try {
                    ScrapeResult(url, scrape(url), null)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    ScrapeResult(url, null, failure)
                }
                results.send(result)
            }
        }
    }
    launch {
        workers.joinAll()
        results.close()
    }

    var failures = 0
    val batch = ArrayList<T>(config.batchSize)
    for (result in results) {
        if (result.failure != null) {
            failures++
            System.err.println("Skipped ${result.url}: ${result.failure.message}")
        } else {
            batch += requireNotNull(result.value)
            if (batch.size == config.batchSize) {
                indexBatch(batch.toList())
                batch.clear()
            }
        }
    }
    if (batch.isNotEmpty()) indexBatch(batch)
    failures
}

private data class ScrapeResult<T>(val url: String, val value: T?, val failure: Exception?)

private fun printUsage() {
    println(
        """
        Usage: java -jar grimoire-scraper.jar [options]

          --content=TYPES           Comma-separated list: spells, monsters (default: spells)
          --sitemap=URL             Source sitemap (default: https://dnd.su/sitemap.xml)
          --elasticsearch-url=URL   Elasticsearch endpoint (default: http://localhost:9200)
          --index=NAME              Destination index for a spells-only or monsters-only run
          --spells-index=NAME       Spells index (default: ELASTICSEARCH_INDEX or spells-v1)
          --monsters-index=NAME     Monsters index (default: ELASTICSEARCH_MONSTERS_INDEX or monsters-v1)
          --delay-ms=N              Minimum interval between all request starts (default: 500)
          --concurrency=N           Maximum concurrent detail requests (default: 4, max: 32)
          --batch-size=N            Documents per bulk request (default: 100)
          --limit=N                 Import only the first N items of each selected content type
          --user-agent=VALUE        HTTP User-Agent identifying the importer

        ELASTICSEARCH_URL, ELASTICSEARCH_INDEX, ELASTICSEARCH_MONSTERS_INDEX, DND_SU_SITEMAP_URL,
        SCRAPER_DELAY_MS, SCRAPER_CONCURRENCY, SCRAPER_BATCH_SIZE, and SCRAPER_USER_AGENT are also supported.
        """.trimIndent(),
    )
}
