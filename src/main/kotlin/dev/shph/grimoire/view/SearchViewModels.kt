package dev.shph.grimoire.view

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SearchFormView(
    val id: String,
    val action: String,
    val resultsTarget: String,
    val query: String = "",
    val suggestionsUrl: String,
    val suggestionPrefix: String,
    val suggestionsLabel: String,
    val filters: List<SearchFilterView>,
)

data class SearchFilterView(
    val id: String,
    val name: String,
    val label: String,
    val emptyLabel: String,
    val options: List<SelectOption>,
)

data class SelectOption(
    val value: String,
    val label: String,
    val selected: Boolean,
)

data class PaginationView(
    val label: String,
    val previousUrl: String?,
    val nextUrl: String?,
) {
    companion object {
        fun create(
            total: Long,
            page: Int,
            pageSize: Int,
            urlForPage: (Int) -> String,
        ): PaginationView? {
            val totalPages = ((total + pageSize - 1) / pageSize).toInt()
            if (totalPages <= 1) return null
            return PaginationView(
                label = "Страница $page из $totalPages",
                previousUrl = (page - 1).takeIf { it >= 1 }?.let(urlForPage),
                nextUrl = (page + 1).takeIf { it <= totalPages }?.let(urlForPage),
            )
        }
    }
}

data class SuggestionView(
    val id: String,
    val nameRu: String,
    val nameEn: String,
)

data class FactView(
    val label: String,
    val value: String,
)

internal fun searchPageUrl(
    path: String,
    parameters: List<Pair<String, String>>,
    targetPage: Int,
): String {
    val query = buildList {
        addAll(parameters)
        add("page" to targetPage.toString())
    }.joinToString("&") { (name, value) -> "${name.urlEncode()}=${value.urlEncode()}" }
    return "$path?$query"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
