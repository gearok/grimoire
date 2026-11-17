package dev.shph.grimoire.scraper

data class ScraperConfig(
    val content: Set<ScraperContent> = setOf(ScraperContent.SPELLS, ScraperContent.MONSTERS),
    val sitemapUrl: String = env("DND_SU_SITEMAP_URL") ?: "https://dnd.su/sitemap.xml",
    val elasticsearchUrl: String = env("ELASTICSEARCH_URL") ?: "http://localhost:9200",
    val spellsIndexName: String = env("ELASTICSEARCH_INDEX") ?: "spells-v1",
    val monstersIndexName: String = env("ELASTICSEARCH_MONSTERS_INDEX") ?: "monsters-v1",
    val delayMs: Long = env("SCRAPER_DELAY_MS")?.toLongOrNull() ?: 500L,
    val concurrency: Int = env("SCRAPER_CONCURRENCY")?.toIntOrNull() ?: 4,
    val batchSize: Int = env("SCRAPER_BATCH_SIZE")?.toIntOrNull() ?: 100,
    val userAgent: String = env("SCRAPER_USER_AGENT") ?: "GrimoireSpellImporter/1.0",
) {
    init {
        require(delayMs >= 0) { "delay must not be negative" }
        require(concurrency in 1..32) { "concurrency must be between 1 and 32" }
        require(batchSize in 1..1_000) { "batch size must be between 1 and 1000" }
        require(spellsIndexName.matches(INDEX_NAME)) { "invalid spells Elasticsearch index name" }
        require(monstersIndexName.matches(INDEX_NAME)) { "invalid monsters Elasticsearch index name" }
        require(content.size < 2 || spellsIndexName != monstersIndexName) {
            "spells and monsters must use different Elasticsearch indexes"
        }
    }

    companion object {
        private val INDEX_NAME = Regex("[a-z0-9._-]+")

        fun parse(args: Array<String>): ScraperConfig {
            if (args.any { it == "--help" || it == "-h" }) throw HelpRequested
            val values = args.map {
                require(it.startsWith("--") && '=' in it) { "expected --name=value, got '$it'" }
                it.substringAfter("--").substringBefore("=") to it.substringAfter("=")
            }.toMap()
            val known = setOf(
                "content",
                "sitemap",
                "elasticsearch-url",
                "index",
                "spells-index",
                "monsters-index",
                "delay-ms",
                "concurrency",
                "batch-size",
                "user-agent",
            )
            require(values.keys.all { it in known }) {
                "unknown option(s): ${values.keys.filterNot { it in known }.joinToString()}"
            }
            val content = values["content"]?.let(::parseContent)
                ?: setOf(ScraperContent.SPELLS, ScraperContent.MONSTERS)
            require(content.size == 1 || "index" !in values) {
                "--index cannot be used with multiple content types; use --spells-index and --monsters-index"
            }
            require("index" !in values || "spells-index" !in values && "monsters-index" !in values) {
                "--index cannot be combined with --spells-index or --monsters-index"
            }
            val sharedIndex = values["index"]
            return ScraperConfig(
                content = content,
                sitemapUrl = values["sitemap"] ?: env("DND_SU_SITEMAP_URL") ?: "https://dnd.su/sitemap.xml",
                elasticsearchUrl = values["elasticsearch-url"] ?: env("ELASTICSEARCH_URL") ?: "http://localhost:9200",
                spellsIndexName = values["spells-index"]
                    ?: sharedIndex.takeIf { content.singleOrNull() == ScraperContent.SPELLS }
                    ?: env("ELASTICSEARCH_INDEX")
                    ?: "spells-v1",
                monstersIndexName = values["monsters-index"]
                    ?: sharedIndex.takeIf { content.singleOrNull() == ScraperContent.MONSTERS }
                    ?: env("ELASTICSEARCH_MONSTERS_INDEX")
                    ?: "monsters-v1",
                delayMs = values["delay-ms"]?.toLong()
                    ?: env("SCRAPER_DELAY_MS")?.toLongOrNull()
                    ?: 500L,
                concurrency = values["concurrency"]?.toInt()
                    ?: env("SCRAPER_CONCURRENCY")?.toIntOrNull()
                    ?: 4,
                batchSize = values["batch-size"]?.toInt()
                    ?: env("SCRAPER_BATCH_SIZE")?.toIntOrNull()
                    ?: 100,
                userAgent = values["user-agent"] ?: env("SCRAPER_USER_AGENT") ?: "GrimoireSpellImporter/1.0",
            )
        }

        private fun parseContent(value: String): Set<ScraperContent> {
            val names = value.split(',').map(String::trim)
            require(names.none(String::isEmpty)) {
                "content must be a comma-separated list containing 'spells' and/or 'monsters'"
            }
            return names.map(ScraperContent::fromCli).toSet()
        }

        private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
    }
}

enum class ScraperContent {
    SPELLS,
    MONSTERS;

    companion object {
        fun fromCli(value: String): ScraperContent = when (value.lowercase()) {
            "spells" -> SPELLS
            "monsters" -> MONSTERS
            else -> throw IllegalArgumentException("unknown content type '$value'; expected 'spells' or 'monsters'")
        }
    }
}

data object HelpRequested : RuntimeException()
