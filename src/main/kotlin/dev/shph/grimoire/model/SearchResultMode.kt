package dev.shph.grimoire.model

enum class SearchResultMode(
    val queryValue: String,
    val pageSize: Int,
) {
    INDEX("index", 1_000),
    CARDS("cards", 30),
    ;

    companion object {
        fun fromQueryValue(value: String?): SearchResultMode? =
            entries.firstOrNull { it.queryValue.equals(value, ignoreCase = true) }
    }
}
