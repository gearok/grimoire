package dev.shph.grimoire.scraper

data class ScraperConfig(
    val content: ScraperContent = ScraperContent.SPELLS,
    val sitemapUrl: String = env("DND_SU_SITEMAP_URL") ?: "https://dnd.su/sitemap.xml",
    val elasticsearchUrl: String = env("ELASTICSEARCH_URL") ?: "http://localhost:9200",
    val indexName: String = env("ELASTICSEARCH_INDEX") ?: "spells-v1",
    val delayMs: Long = env("SCRAPER_DELAY_MS")?.toLongOrNull() ?: 500L,
    val batchSize: Int = env("SCRAPER_BATCH_SIZE")?.toIntOrNull() ?: 100,
    val limit: Int? = null,
    val userAgent: String = env("SCRAPER_USER_AGENT") ?: "GrimoireSpellImporter/1.0",
) {
    init {
        require(delayMs >= 0) { "delay must not be negative" }
        require(batchSize in 1..1_000) { "batch size must be between 1 and 1000" }
        require(indexName.matches(Regex("[a-z0-9._-]+"))) { "invalid Elasticsearch index name" }
        require(limit == null || limit > 0) { "limit must be positive" }
    }

    companion object {
        fun parse(args: Array<String>): ScraperConfig {
            if (args.any { it == "--help" || it == "-h" }) throw HelpRequested
            val values = args.map {
                require(it.startsWith("--") && '=' in it) { "expected --name=value, got '$it'" }
                it.substringAfter("--").substringBefore("=") to it.substringAfter("=")
            }.toMap()
            val known = setOf("content", "sitemap", "elasticsearch-url", "index", "delay-ms", "batch-size", "limit", "user-agent")
            require(values.keys.all { it in known }) {
                "unknown option(s): ${values.keys.filterNot { it in known }.joinToString()}"
            }
            val content = values["content"]?.let(ScraperContent::fromCli) ?: ScraperContent.SPELLS
            return ScraperConfig(
                content = content,
                sitemapUrl = values["sitemap"] ?: env("DND_SU_SITEMAP_URL") ?: "https://dnd.su/sitemap.xml",
                elasticsearchUrl = values["elasticsearch-url"] ?: env("ELASTICSEARCH_URL") ?: "http://localhost:9200",
                indexName = values["index"] ?: if (content == ScraperContent.MONSTERS) {
                    env("ELASTICSEARCH_MONSTERS_INDEX") ?: "monsters-v1"
                } else {
                    env("ELASTICSEARCH_INDEX") ?: "spells-v1"
                },
                delayMs = values["delay-ms"]?.toLong()
                    ?: env("SCRAPER_DELAY_MS")?.toLongOrNull()
                    ?: 500L,
                batchSize = values["batch-size"]?.toInt()
                    ?: env("SCRAPER_BATCH_SIZE")?.toIntOrNull()
                    ?: 100,
                limit = values["limit"]?.toInt(),
                userAgent = values["user-agent"] ?: env("SCRAPER_USER_AGENT") ?: "GrimoireSpellImporter/1.0",
            )
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
            else -> throw IllegalArgumentException("content must be 'spells' or 'monsters'")
        }
    }
}

data object HelpRequested : RuntimeException()
