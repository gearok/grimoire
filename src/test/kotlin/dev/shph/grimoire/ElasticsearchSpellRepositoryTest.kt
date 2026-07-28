package dev.shph.grimoire

import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.MagicSchool
import dev.shph.grimoire.repository.ElasticsearchSpellRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElasticsearchSpellRepositoryTest {
    private val repository = ElasticsearchSpellRepository(
        client = HttpClient(MockEngine { respondOk() }),
        json = Json,
        baseUrl = "http://elasticsearch.test",
        indexName = "spells-test",
    )

    @Test
    fun `text search combines name prefix fuzzy name and lower boosted description queries`() {
        val request = repository.buildSearchRequest(SpellSearch(query = "огнен ша"))
        val bool = request["query"]!!.jsonObject["bool"]!!.jsonObject
        val scoringQueries = bool["should"]!!.jsonArray

        assertEquals(3, scoringQueries.size)
        assertEquals(1, bool["minimum_should_match"]!!.jsonPrimitive.content.toInt())

        val prefix = scoringQueries[0].jsonObject["multi_match"]!!.jsonObject
        assertEquals("bool_prefix", prefix["type"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("name.ru^12", "name.en^10", "aliases^6"),
            prefix["fields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val fuzzy = scoringQueries[1].jsonObject["multi_match"]!!.jsonObject
        assertEquals("AUTO", fuzzy["fuzziness"]!!.jsonPrimitive.content)
        assertEquals(1, fuzzy["prefix_length"]!!.jsonPrimitive.content.toInt())

        val rulesText = scoringQueries[2].jsonObject["multi_match"]!!.jsonObject
        assertEquals(
            listOf("description^1", "higherLevels^0.5"),
            rulesText["fields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `text results are sorted by relevance before deterministic tie breakers`() {
        val textSort = repository.buildSearchRequest(SpellSearch(query = "fire"))["sort"]!!.jsonArray
        val browseSort = repository.buildSearchRequest(SpellSearch())["sort"]!!.jsonArray

        assertEquals("_score", textSort.first().jsonObject.keys.single())
        assertEquals("desc", textSort.first().jsonObject["_score"]!!.jsonPrimitive.content)
        assertTrue(textSort.drop(1).any { "level" in it.jsonObject })
        assertTrue(textSort.drop(1).any { "name.ru.keyword" in it.jsonObject })
        assertFalse(browseSort.any { "_score" in it.jsonObject })
    }

    @Test
    fun `multiple values use terms filters with OR inside each filter group`() {
        val request = repository.buildSearchRequest(
            SpellSearch(
                levels = setOf(1, 3),
                schools = setOf(MagicSchool.EVOCATION, MagicSchool.ENCHANTMENT),
                characterClasses = setOf("волшебник", "чародей"),
            ),
        )
        val filters = request["query"]!!
            .jsonObject["bool"]!!
            .jsonObject["filter"]!!
            .jsonArray

        assertEquals(
            listOf("1", "3"),
            filters[0].jsonObject["terms"]!!.jsonObject["level"]!!.jsonArray
                .map { it.jsonPrimitive.content },
        )
        assertEquals(
            setOf("enchantment", "evocation"),
            filters[1].jsonObject["terms"]!!.jsonObject["school"]!!.jsonArray
                .map { it.jsonPrimitive.content }
                .toSet(),
        )
        assertEquals(
            setOf("волшебник", "чародей"),
            filters[2].jsonObject["terms"]!!.jsonObject["classes.name"]!!.jsonArray
                .map { it.jsonPrimitive.content }
                .toSet(),
        )
    }
}
