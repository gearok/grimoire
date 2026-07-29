package dev.shph.grimoire.controller

import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import dev.shph.grimoire.model.MagicSchool
import dev.shph.grimoire.model.MonsterFacets
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.model.SearchResultMode
import dev.shph.grimoire.model.SpellFacets
import dev.shph.grimoire.model.SpellSearch
import org.springframework.util.MultiValueMap

data class SpellSearchRequest(
    val query: String?,
    val levels: Set<Int>,
    val schools: Set<MagicSchool>,
    val characterClasses: Set<String>,
    val page: Int,
    val resultMode: SearchResultMode,
) {
    fun toSearch() = SpellSearch(
        query = query,
        levels = levels,
        schools = schools,
        characterClasses = characterClasses,
        page = page,
        pageSize = resultMode.pageSize,
    )

    companion object {
        fun from(parameters: MultiValueMap<String, String>): SpellSearchRequest {
            val query = SearchQueryParameters(parameters)
            return SpellSearchRequest(
                query = query.text(),
                levels = query.facets("level", SpellFacets.levels),
                schools = query.facets("school", SpellFacets.schools),
                characterClasses = query.facets("class", SpellFacets.characterClasses),
                page = query.page(),
                resultMode = query.resultMode(),
            )
        }
    }
}

data class MonsterSearchRequest(
    val query: String?,
    val sizes: Set<CreatureSize>,
    val types: Set<CreatureType>,
    val challenges: Set<Double>,
    val page: Int,
    val resultMode: SearchResultMode,
) {
    fun toSearch() = MonsterSearch(
        query = query,
        sizes = sizes,
        types = types,
        challenges = challenges,
        page = page,
        pageSize = resultMode.pageSize,
    )

    companion object {
        fun from(parameters: MultiValueMap<String, String>): MonsterSearchRequest {
            val query = SearchQueryParameters(parameters)
            return MonsterSearchRequest(
                query = query.text(),
                sizes = query.facets("size", MonsterFacets.sizes),
                types = query.facets("type", MonsterFacets.types),
                challenges = query.facets("challenge", MonsterFacets.challenges),
                page = query.page(),
                resultMode = query.resultMode(),
            )
        }
    }
}

private class SearchQueryParameters(private val parameters: MultiValueMap<String, String>) {
    fun first(name: String): String? = parameters.getFirst(name)

    fun text(): String? = first("q")?.trim()?.takeIf(String::isNotEmpty)

    fun page(): Int {
        val value = first("page")?.takeIf(String::isNotBlank) ?: return 1
        return value.toIntOrNull()?.takeIf { it >= 1 }
            ?: throw BadRequestException("Invalid search parameters")
    }

    fun resultMode(): SearchResultMode {
        val values = parameters["view"].orEmpty().filter(String::isNotBlank)
        if (values.isEmpty()) return SearchResultMode.INDEX
        val modes = values.map { value ->
            SearchResultMode.fromQueryValue(value)
                ?: throw BadRequestException("Invalid search result mode")
        }
        return if (SearchResultMode.CARDS in modes) SearchResultMode.CARDS else SearchResultMode.INDEX
    }

    fun <T> facets(name: String, catalog: dev.shph.grimoire.model.FacetCatalog<T>): Set<T> =
        values(name).map { value ->
            catalog.parse(value) ?: throw BadRequestException("Invalid search parameters")
        }.toSet()

    private fun values(name: String): List<String> =
        parameters[name].orEmpty()
            .flatMap { it.split(',') }
            .map(String::trim)
            .filter(String::isNotEmpty)
}
