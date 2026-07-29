package dev.shph.grimoire

import dev.shph.grimoire.controller.BadRequestException
import dev.shph.grimoire.controller.MonsterSearchRequest
import dev.shph.grimoire.controller.SpellSearchRequest
import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import dev.shph.grimoire.model.FacetCatalog
import dev.shph.grimoire.model.MagicSchool
import dev.shph.grimoire.model.MonsterFacets
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.model.MonsterSearchResult
import dev.shph.grimoire.model.SearchResultMode
import dev.shph.grimoire.model.SpellFacets
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult
import dev.shph.grimoire.view.toIndexView
import org.junit.jupiter.api.Test
import org.springframework.util.LinkedMultiValueMap
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchFacetsTest {
    @Test
    fun `every supported spell facet round trips through its catalog`() {
        assertCatalogRoundTrips(SpellFacets.levels)
        assertCatalogRoundTrips(SpellFacets.schools)
        assertCatalogRoundTrips(SpellFacets.characterClasses)
    }

    @Test
    fun `every supported monster facet round trips through its catalog`() {
        assertCatalogRoundTrips(MonsterFacets.sizes)
        assertCatalogRoundTrips(MonsterFacets.types)
        assertCatalogRoundTrips(MonsterFacets.challenges)
    }

    @Test
    fun `spell request accepts repeated and comma separated facets`() {
        val parameters = LinkedMultiValueMap<String, String>().apply {
            add("q", "  огонь  ")
            add("level", "1, 3")
            add("level", "9")
            add("school", "evocation, enchantment")
            add("class", "ВОЛШЕБНИК")
            add("class", "чародей, волшебник")
            add("page", "2")
            add("view", "CARDS")
        }

        assertEquals(
            SpellSearchRequest(
                query = "огонь",
                levels = setOf(1, 3, 9),
                schools = setOf(MagicSchool.EVOCATION, MagicSchool.ENCHANTMENT),
                characterClasses = setOf("волшебник", "чародей"),
                page = 2,
                resultMode = SearchResultMode.CARDS,
            ),
            SpellSearchRequest.from(parameters),
        )
    }

    @Test
    fun `monster request accepts repeated comma separated and equivalent numeric facets`() {
        val parameters = LinkedMultiValueMap<String, String>().apply {
            add("size", "small, medium")
            add("type", "humanoid")
            add("type", "dragon,beast")
            add("challenge", "0.25,1.0")
            add("challenge", "30")
        }

        assertEquals(
            MonsterSearchRequest(
                query = null,
                sizes = setOf(CreatureSize.SMALL, CreatureSize.MEDIUM),
                types = setOf(CreatureType.HUMANOID, CreatureType.DRAGON, CreatureType.BEAST),
                challenges = setOf(0.25, 1.0, 30.0),
                page = 1,
                resultMode = SearchResultMode.INDEX,
            ),
            MonsterSearchRequest.from(parameters),
        )
    }

    @Test
    fun `invalid spell facets and pages are rejected`() {
        listOf(
            parameters("level", "10"),
            parameters("school", "fire"),
            parameters("class", "маг"),
            parameters("page", "zero"),
            parameters("page", "0"),
        ).forEach { parameters ->
            assertFailsWith<BadRequestException> { SpellSearchRequest.from(parameters) }
        }
    }

    @Test
    fun `invalid monster facets and pages are rejected`() {
        listOf(
            parameters("size", "colossal"),
            parameters("type", "swarm"),
            parameters("challenge", "31"),
            parameters("challenge", "1/4"),
            parameters("page", "-1"),
        ).forEach { parameters ->
            assertFailsWith<BadRequestException> { MonsterSearchRequest.from(parameters) }
        }
    }

    @Test
    fun `spell pagination uses canonical catalog values`() {
        val criteria = SpellSearch(
            query = "огонь и лёд",
            levels = setOf(3, 1),
            schools = setOf(MagicSchool.EVOCATION, MagicSchool.ENCHANTMENT),
            characterClasses = setOf("чародей", "волшебник"),
            page = 2,
            pageSize = 10,
        )

        val view = SpellSearchResult(emptyList(), 30, 2, 10)
            .toIndexView(criteria, resultMode = SearchResultMode.CARDS)

        assertEquals(
            "/spells?q=%D0%BE%D0%B3%D0%BE%D0%BD%D1%8C+%D0%B8+%D0%BB%D1%91%D0%B4" +
                "&level=1&level=3&school=enchantment&school=evocation" +
                "&class=%D0%B2%D0%BE%D0%BB%D1%88%D0%B5%D0%B1%D0%BD%D0%B8%D0%BA" +
                "&class=%D1%87%D0%B0%D1%80%D0%BE%D0%B4%D0%B5%D0%B9&view=cards&page=3",
            view.pagination?.nextUrl,
        )
    }

    @Test
    fun `monster pagination canonicalizes numeric facet values`() {
        val criteria = MonsterSearch(
            sizes = setOf(CreatureSize.MEDIUM, CreatureSize.SMALL),
            types = setOf(CreatureType.HUMANOID, CreatureType.DRAGON),
            challenges = setOf(1.0, 0.25),
            page = 2,
            pageSize = 10,
        )

        val view = MonsterSearchResult(emptyList(), 30, 2, 10).toIndexView(criteria)

        assertEquals(
            "/monsters?size=medium&size=small&type=dragon&type=humanoid" +
                "&challenge=0.25&challenge=1&view=index&page=3",
            view.pagination?.nextUrl,
        )
    }

    private fun parameters(name: String, value: String) =
        LinkedMultiValueMap<String, String>().apply { add(name, value) }

    private fun <T> assertCatalogRoundTrips(catalog: FacetCatalog<T>) {
        catalog.options.forEach { option ->
            assertEquals(option.item, catalog.parse(option.value))
            assertEquals(option.value, catalog.serialize(option.item))
        }
    }
}
