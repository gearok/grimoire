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
    fun `monster text search only uses russian and english names`() {
        val bool = repository.buildSearchQuery(MonsterSearch(query = "гобл")).query!!.bool()

        assertEquals(2, bool.should().size)
        assertEquals(TextQueryType.BoolPrefix, bool.should()[0].multiMatch().type())
        assertEquals(listOf("name.ru^12", "name.en^10"), bool.should()[0].multiMatch().fields())
        assertEquals("AUTO", bool.should()[1].multiMatch().fuzziness())
        assertEquals(
            listOf("name.ru^8", "name.en^7"),
            bool.should()[1].multiMatch().fields(),
        )
        assertEquals(Sort.Direction.DESC, repository.buildSearchQuery(MonsterSearch(query = "гобл"))
            .pageable.sort.getOrderFor("_score")?.direction)
    }

    @Test
    fun `monster suggestions only use russian and english names`() {
        val bool = repository.buildSuggestionQuery("гобл", 10).query!!.bool()

        assertEquals(2, bool.should().size)
        assertEquals(listOf("name.ru^12", "name.en^10"), bool.should()[0].multiMatch().fields())
        assertEquals(listOf("name.ru^8", "name.en^7"), bool.should()[1].multiMatch().fields())
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
