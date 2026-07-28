package dev.shph.grimoire.scraper

import dev.shph.grimoire.model.Spell
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val config = try {
        ScraperConfig.parse(args)
    } catch (_: HelpRequested) {
        printUsage()
        return
    } catch (failure: IllegalArgumentException) {
        System.err.println("Configuration error: ${failure.message}")
        printUsage()
        exitProcess(2)
    }

    val client = DndSuClient(config.userAgent)
    val parser = DndSuSpellParser()
    val indexer = ElasticsearchSpellIndexer(config.elasticsearchUrl, config.indexName)
    val urls = client.spellUrls(config.sitemapUrl).let { urls ->
        config.limit?.let(urls::take) ?: urls
    }
    require(urls.isNotEmpty()) { "The sitemap did not contain official spell URLs" }

    println("Found ${urls.size} official spell page(s); destination index is '${config.indexName}'.")
    indexer.ensureIndex()
    val deleted = indexer.clearIndex()
    println("Cleared $deleted existing document(s) from '${config.indexName}'.")

    val batch = ArrayList<Spell>(config.batchSize)
    val failures = mutableListOf<String>()
    var indexed = 0
    urls.forEachIndexed { position, url ->
        val spell = try {
            parser.parse(client.fetch(url), url)
        } catch (failure: Exception) {
            failures += "$url: ${failure.message}"
            System.err.println("Skipped $url: ${failure.message}")
            null
        }
        if (spell != null) {
            batch += spell
        }
        if (batch.size == config.batchSize) {
            indexer.index(batch)
            indexed += batch.size
            batch.clear()
            println("Indexed $indexed/${urls.size} spells.")
        }
        if (position < urls.lastIndex && config.delayMs > 0) Thread.sleep(config.delayMs)
    }
    indexer.index(batch)
    indexed += batch.size
    indexer.refreshIndex()
    println("Done: indexed $indexed spell(s), ${failures.size} failed.")
    if (failures.isNotEmpty()) exitProcess(1)
}

private fun printUsage() {
    println(
        """
        Usage: ./gradlew :scraper:run --args='[options]'

          --sitemap=URL             Source sitemap (default: https://dnd.su/sitemap.xml)
          --elasticsearch-url=URL   Elasticsearch endpoint (default: http://localhost:9200)
          --index=NAME              Destination index (default: spells-v1)
          --delay-ms=N              Delay between detail requests (default: 500)
          --batch-size=N            Documents per bulk request (default: 100)
          --limit=N                 Import only the first N spells (useful for a smoke test)
          --user-agent=VALUE        HTTP User-Agent identifying the importer

        ELASTICSEARCH_URL, ELASTICSEARCH_INDEX, DND_SU_SITEMAP_URL,
        SCRAPER_DELAY_MS, SCRAPER_BATCH_SIZE, and SCRAPER_USER_AGENT are also supported.
        """.trimIndent(),
    )
}
