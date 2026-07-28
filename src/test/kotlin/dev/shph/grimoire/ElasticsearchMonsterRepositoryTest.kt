package dev.shph.grimoire

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.repository.ElasticsearchMonsterRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import kotlin.test.assertEquals

class ElasticsearchMonsterRepositoryTest {
    private val repository = ElasticsearchMonsterRepository(
        mock(ElasticsearchOperations::class.java),
        "monsters-test",
    )

    @Test
    fun `monster text search mirrors spell prefix fuzzy and rules search`() {
        val bool = repository.buildSearchQuery(MonsterSearch(query = "гобл")).query!!.bool()

        assertEquals(3, bool.should().size)
        assertEquals(TextQueryType.BoolPrefix, bool.should()[0].multiMatch().type())
        assertEquals("AUTO", bool.should()[1].multiMatch().fuzziness())
        assertEquals(
            listOf("description^1", "sections.entries.name^1.5", "sections.entries.text^1"),
            bool.should()[2].multiMatch().fields(),
        )
        assertEquals(Sort.Direction.DESC, repository.buildSearchQuery(MonsterSearch(query = "гобл"))
            .pageable.sort.getOrderFor("_score")?.direction)
    }

    @Test
    fun `monster facets are independent terms filters`() {
        val filters = repository.buildSearchQuery(
            MonsterSearch(
                sizes = setOf(CreatureSize.SMALL),
                types = setOf(CreatureType.HUMANOID),
                challenges = setOf(0.25),
            ),
        ).query!!.bool().filter()

        assertEquals(listOf("size", "type", "challenge.value"), filters.map { it.terms().field() })
        assertEquals("small", filters[0].terms().terms().value().single().stringValue())
        assertEquals("humanoid", filters[1].terms().terms().value().single().stringValue())
        assertEquals(0.25, filters[2].terms().terms().value().single().doubleValue())
    }
}
