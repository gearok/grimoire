package dev.shph.grimoire.scraper

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
    val failures = when (config.content) {
        ScraperContent.SPELLS -> importSpells(config, client)
        ScraperContent.MONSTERS -> importMonsters(config, client)
    }
    if (failures > 0) exitProcess(1)
}

private fun importSpells(config: ScraperConfig, client: DndSuClient): Int {
    val parser = DndSuSpellParser()
    val indexer = ElasticsearchSpellIndexer(config.elasticsearchUrl, config.indexName)
    val urls = limited(client.spellUrls(config.sitemapUrl), config.limit)
    require(urls.isNotEmpty()) { "The sitemap did not contain official spell URLs" }
    println("Found ${urls.size} official spell page(s); destination index is '${config.indexName}'.")
    indexer.ensureIndex()
    println("Cleared ${indexer.clearIndex()} existing document(s) from '${config.indexName}'.")
    val failures = mutableListOf<String>()
    val batch = ArrayList<dev.shph.grimoire.model.Spell>(config.batchSize)
    var indexed = 0
    urls.forEachIndexed { position, url ->
        val spell = runCatching { parser.parse(client.fetch(url), url) }.getOrElse {
            failures += "$url: ${it.message}"
            System.err.println("Skipped $url: ${it.message}")
            null
        }
        if (spell != null) batch += spell
        if (batch.size == config.batchSize) {
            indexer.index(batch)
            indexed += batch.size
            batch.clear()
            println("Indexed $indexed/${urls.size} spells.")
        }
        delay(config, position, urls)
    }
    indexer.index(batch)
    indexed += batch.size
    indexer.refreshIndex()
    println("Done: indexed $indexed spell(s), ${failures.size} failed.")
    return failures.size
}

private fun importMonsters(config: ScraperConfig, client: DndSuClient): Int {
    val parser = DndSuMonsterParser()
    val indexer = ElasticsearchMonsterIndexer(config.elasticsearchUrl, config.indexName)
    val urls = limited(client.monsterUrls(config.sitemapUrl), config.limit)
    require(urls.isNotEmpty()) { "The sitemap did not contain official monster URLs" }
    println("Found ${urls.size} official monster page(s); destination index is '${config.indexName}'.")
    indexer.ensureIndex()
    println("Cleared ${indexer.clearIndex()} existing document(s) from '${config.indexName}'.")
    val failures = mutableListOf<String>()
    val batch = ArrayList<dev.shph.grimoire.model.Monster>(config.batchSize)
    var indexed = 0
    urls.forEachIndexed { position, url ->
        val monster = runCatching { parser.parse(client.fetch(url), url) }.getOrElse {
            failures += "$url: ${it.message}"
            System.err.println("Skipped $url: ${it.message}")
            null
        }
        if (monster != null) batch += monster
        if (batch.size == config.batchSize) {
            indexer.index(batch)
            indexed += batch.size
            batch.clear()
            println("Indexed $indexed/${urls.size} monsters.")
        }
        delay(config, position, urls)
    }
    indexer.index(batch)
    indexed += batch.size
    indexer.refreshIndex()
    println("Done: indexed $indexed monster(s), ${failures.size} failed.")
    return failures.size
}

private fun <T> limited(values: List<T>, limit: Int?) = limit?.let(values::take) ?: values

private fun delay(config: ScraperConfig, position: Int, urls: List<String>) {
    if (position < urls.lastIndex && config.delayMs > 0) Thread.sleep(config.delayMs)
}

private fun printUsage() {
    println(
        """
        Usage: java -jar grimoire-scraper.jar [options]

          --content=TYPE            spells or monsters (default: spells)
          --sitemap=URL             Source sitemap (default: https://dnd.su/sitemap.xml)
          --elasticsearch-url=URL   Elasticsearch endpoint (default: http://localhost:9200)
          --index=NAME              Destination index (default: spells-v1 or monsters-v1)
          --delay-ms=N              Delay between detail requests (default: 500)
          --batch-size=N            Documents per bulk request (default: 100)
          --limit=N                 Import only the first N spells (useful for a smoke test)
          --user-agent=VALUE        HTTP User-Agent identifying the importer

        ELASTICSEARCH_URL, ELASTICSEARCH_INDEX, ELASTICSEARCH_MONSTERS_INDEX, DND_SU_SITEMAP_URL,
        SCRAPER_DELAY_MS, SCRAPER_BATCH_SIZE, and SCRAPER_USER_AGENT are also supported.
        """.trimIndent(),
    )
}
