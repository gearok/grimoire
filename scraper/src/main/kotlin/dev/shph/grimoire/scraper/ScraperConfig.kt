package dev.shph.grimoire.scraper

data class ScraperConfig(
    val content: Set<ScraperContent> = ScraperContent.entries.toSet(),
    val sitemapUrl: String = env("DND_SU_SITEMAP_URL") ?: "https://dnd.su/sitemap.xml",
    val elasticsearchUrl: String = env("ELASTICSEARCH_URL") ?: "http://localhost:9200",
    val spellsIndexName: String = env("ELASTICSEARCH_INDEX") ?: "spells-v1",
    val monstersIndexName: String = env("ELASTICSEARCH_MONSTERS_INDEX") ?: "monsters-v1",
    val classesOutputPath: String = env("SCRAPER_CLASSES_OUT") ?: "scraper/build/classes.json",
    val racesOutputPath: String = env("SCRAPER_RACES_OUT") ?: "scraper/build/races.json",
    val delayMs: Long = env("SCRAPER_DELAY_MS")?.toLongOrNull() ?: 500L,
    val concurrency: Int = env("SCRAPER_CONCURRENCY")?.toIntOrNull() ?: 4,
    val batchSize: Int = env("SCRAPER_BATCH_SIZE")?.toIntOrNull() ?: 100,
    val userAgent: String = env("SCRAPER_USER_AGENT") ?: "GrimoireSpellImporter/1.0",
) {
    init {
        require(delayMs >= 0) { "delay must not be negative" }
        require(concurrency in 1..32) { "concurrency must be between 1 and 32" }
        require(batchSize in 1..1_000) { "batch size must be between 1 and 1000" }
        // Only spells and monsters are indexed into Elasticsearch; classes and races are
        // written to committed JSON, so their output is a file path, not an index name.
        val indexNames = content.filter { it.indexed }.map(::indexName)
        indexNames.forEach { require(it.matches(INDEX_NAME)) { "invalid Elasticsearch index name '$it'" } }
        require(indexNames.size == indexNames.toSet().size) {
            "each Elasticsearch content type must use a different index"
        }
    }

    /** The Elasticsearch index name for an ES-indexed content type. */
    fun indexName(content: ScraperContent): String = when (content) {
        ScraperContent.SPELLS -> spellsIndexName
        ScraperContent.MONSTERS -> monstersIndexName
        else -> error("${content.cli} is not indexed into Elasticsearch")
    }

    /** The JSON output path for a file-backed content type. */
    fun outputPath(content: ScraperContent): String = when (content) {
        ScraperContent.CLASSES -> classesOutputPath
        ScraperContent.RACES -> racesOutputPath
        else -> error("${content.cli} is not written to a JSON file")
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
                "out",
                "classes-out",
                "races-out",
                "delay-ms",
                "concurrency",
                "batch-size",
                "user-agent",
            )
            require(values.keys.all { it in known }) {
                "unknown option(s): ${values.keys.filterNot { it in known }.joinToString()}"
            }
            val content = values["content"]?.let(::parseContent) ?: ScraperContent.entries.toSet()

            val indexedTypes = content.filter { it.indexed }
            require(indexedTypes.size == 1 || "index" !in values) {
                "--index cannot be used with multiple indexed content types; use --<type>-index instead"
            }
            val perTypeIndexOptions = ScraperContent.entries.mapNotNull { it.indexOption }
            require("index" !in values || perTypeIndexOptions.none { it in values }) {
                "--index cannot be combined with a --<type>-index option"
            }
            val sharedIndex = values["index"]
            fun indexFor(type: ScraperContent): String =
                values[type.indexOption]
                    ?: sharedIndex.takeIf { indexedTypes.singleOrNull() == type }
                    ?: type.indexEnv?.let(::env)
                    ?: type.defaultIndex.orEmpty()

            val fileTypes = content.filter { !it.indexed }
            require(fileTypes.size == 1 || "out" !in values) {
                "--out cannot be used with multiple file content types; use --<type>-out instead"
            }
            val perTypeOutOptions = ScraperContent.entries.mapNotNull { it.outOption }
            require("out" !in values || perTypeOutOptions.none { it in values }) {
                "--out cannot be combined with a --<type>-out option"
            }
            val sharedOut = values["out"]
            fun outFor(type: ScraperContent): String =
                values[type.outOption]
                    ?: sharedOut.takeIf { fileTypes.singleOrNull() == type }
                    ?: type.outEnv?.let(::env)
                    ?: type.defaultOut.orEmpty()

            return ScraperConfig(
                content = content,
                sitemapUrl = values["sitemap"] ?: env("DND_SU_SITEMAP_URL") ?: "https://dnd.su/sitemap.xml",
                elasticsearchUrl = values["elasticsearch-url"] ?: env("ELASTICSEARCH_URL") ?: "http://localhost:9200",
                spellsIndexName = indexFor(ScraperContent.SPELLS),
                monstersIndexName = indexFor(ScraperContent.MONSTERS),
                classesOutputPath = outFor(ScraperContent.CLASSES),
                racesOutputPath = outFor(ScraperContent.RACES),
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
                "content must be a comma-separated list of: ${ScraperContent.entries.joinToString { it.cli }}"
            }
            return names.map(ScraperContent::fromCli).toSet()
        }

        private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
    }
}

enum class ScraperContent(
    val cli: String,
    val indexOption: String? = null,
    val indexEnv: String? = null,
    val defaultIndex: String? = null,
    val outOption: String? = null,
    val outEnv: String? = null,
    val defaultOut: String? = null,
) {
    SPELLS("spells", indexOption = "spells-index", indexEnv = "ELASTICSEARCH_INDEX", defaultIndex = "spells-v1"),
    MONSTERS(
        "monsters",
        indexOption = "monsters-index",
        indexEnv = "ELASTICSEARCH_MONSTERS_INDEX",
        defaultIndex = "monsters-v1",
    ),
    CLASSES("classes", outOption = "classes-out", outEnv = "SCRAPER_CLASSES_OUT", defaultOut = "scraper/build/classes.json"),
    RACES("races", outOption = "races-out", outEnv = "SCRAPER_RACES_OUT", defaultOut = "scraper/build/races.json");

    /** True when this content is indexed into Elasticsearch; false when written to a JSON file. */
    val indexed: Boolean get() = indexOption != null

    companion object {
        fun fromCli(value: String): ScraperContent = entries.firstOrNull { it.cli == value.lowercase() }
            ?: throw IllegalArgumentException(
                "unknown content type '$value'; expected one of: ${entries.joinToString { it.cli }}",
            )
    }
}

data object HelpRequested : RuntimeException()
