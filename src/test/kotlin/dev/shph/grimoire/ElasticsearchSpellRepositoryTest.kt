package dev.shph.grimoire

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import dev.shph.grimoire.model.MagicSchool
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.repository.ElasticsearchSpellRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElasticsearchSpellRepositoryTest {
    private val repository = ElasticsearchSpellRepository(
        operations = mock(ElasticsearchOperations::class.java),
        indexName = "spells-test",
    )

    @Test
    fun `text search combines name prefix fuzzy name and lower boosted description queries`() {
        val bool = repository.buildSearchQuery(SpellSearch(query = "огнен ша")).query!!.bool()
        val scoringQueries = bool.should()

        assertEquals(3, scoringQueries.size)
        assertEquals("1", bool.minimumShouldMatch())

        val prefix = scoringQueries[0].multiMatch()
        assertEquals(TextQueryType.BoolPrefix, prefix.type())
        assertEquals(
            listOf("name.ru^12", "name.en^10", "aliases^6"),
            prefix.fields(),
        )

        val fuzzy = scoringQueries[1].multiMatch()
        assertEquals("AUTO", fuzzy.fuzziness())
        assertEquals(1, fuzzy.prefixLength())

        val rulesText = scoringQueries[2].multiMatch()
        assertEquals(
            listOf("description^1", "higherLevels^0.5"),
            rulesText.fields(),
        )
    }

    @Test
    fun `text results are sorted by relevance before deterministic tie breakers`() {
        val textSort = repository.buildSearchQuery(SpellSearch(query = "fire")).pageable.sort
        val browseSort = repository.buildSearchQuery(SpellSearch()).pageable.sort

        assertEquals(Sort.Direction.DESC, textSort.getOrderFor("_score")?.direction)
        assertEquals(Sort.Direction.ASC, textSort.getOrderFor("level")?.direction)
        assertEquals(Sort.Direction.ASC, textSort.getOrderFor("name.ru.keyword")?.direction)
        assertFalse(browseSort.any { it.property == "_score" })
    }

    @Test
    fun `multiple values use terms filters with OR inside each filter group`() {
        val filters = repository.buildSearchQuery(
            SpellSearch(
                levels = setOf(1, 3),
                schools = setOf(MagicSchool.EVOCATION, MagicSchool.ENCHANTMENT),
                characterClasses = setOf("волшебник", "чародей"),
            ),
        ).query!!.bool().filter()

        assertEquals("level", filters[0].terms().field())
        assertEquals(
            listOf(1L, 3L),
            filters[0].terms().terms().value().map { it.longValue() },
        )
        assertEquals("school", filters[1].terms().field())
        assertEquals(
            setOf("enchantment", "evocation"),
            filters[1].terms().terms().value().map { it.stringValue() }.toSet(),
        )
        assertEquals("classes.name", filters[2].terms().field())
        assertEquals(
            setOf("волшебник", "чародей"),
            filters[2].terms().terms().value().map { it.stringValue() }.toSet(),
        )
        assertTrue(filters.all { it.isTerms })
    }
}
