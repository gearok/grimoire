package dev.shph.grimoire.repository

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.stereotype.Repository

@Repository
class ElasticsearchSpellRepository(
    private val operations: ElasticsearchOperations,
    @Value("\${elasticsearch.spells-index:spells-v1}") indexName: String,
) : SpellRepository {
    private val index = IndexCoordinates.of(indexName)

    override fun search(criteria: SpellSearch): SpellSearchResult {
        val hits = operations.search(buildSearchQuery(criteria), Spell::class.java, index)
        return SpellSearchResult(
            spells = hits.searchHits.map { it.content },
            total = hits.totalHits,
            page = criteria.page,
            pageSize = criteria.pageSize,
        )
    }

    override fun suggest(query: String, limit: Int): List<Spell> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        return operations.search(
            buildSuggestionQuery(normalizedQuery, limit),
            Spell::class.java,
            index,
        ).searchHits.map { it.content }
    }

    override fun findById(id: String): Spell? =
        operations.get(id, Spell::class.java, index)

    internal fun buildSuggestionQuery(query: String, limit: Int): NativeQuery =
        NativeQuery.builder()
            .withQuery(
                Query.of {
                    it.bool { bool ->
                        bool.should(
                            multiMatchQuery(
                                query = query,
                                type = TextQueryType.BoolPrefix,
                                fields = listOf("name.ru^12", "name.en^10", "aliases^6"),
                            ),
                            multiMatchQuery(
                                query = query,
                                type = TextQueryType.BestFields,
                                fields = listOf("name.ru^8", "name.en^7", "aliases^4"),
                                fuzziness = "AUTO",
                                prefixLength = 1,
                            ),
                        ).minimumShouldMatch("1")
                    }
                },
            )
            .withPageable(
                PageRequest.of(
                    0,
                    limit.coerceIn(1, 20),
                    Sort.by(Sort.Order.desc("_score"), Sort.Order.asc("name.ru.keyword")),
                ),
            )
            .build()

    internal fun buildSearchQuery(criteria: SpellSearch): NativeQuery {
        val searchText = criteria.query?.trim()?.takeIf(String::isNotEmpty)
        val scoringQueries = searchText?.let(::textQueries).orEmpty()
        val filters = buildList {
            if (criteria.levels.isNotEmpty()) {
                add(termsQuery("level", criteria.levels.sorted().map { FieldValue.of(it.toLong()) }))
            }
            if (criteria.schools.isNotEmpty()) {
                add(
                    termsQuery(
                        "school",
                        criteria.schools.sortedBy { it.slug }.map { FieldValue.of(it.slug) },
                    ),
                )
            }
            if (criteria.characterClasses.isNotEmpty()) {
                add(
                    termsQuery(
                        "classes.name",
                        criteria.characterClasses.sorted().map { FieldValue.of(it.lowercase()) },
                    ),
                )
            }
        }
        val query = searchQuery(scoringQueries, filters)
        val sort = buildList {
            if (searchText != null) add(Sort.Order.desc("_score"))
            add(Sort.Order.asc("level"))
            add(Sort.Order.asc("name.ru.keyword"))
        }
        val sorting = Sort.by(sort)
        val pageable = criteria.pageSize
            ?.let { PageRequest.of(criteria.page - 1, it, sorting) }
            ?: Pageable.unpaged(sorting)
        return NativeQuery.builder()
            .withQuery(query)
            .withPageable(pageable)
            .build()
    }

    private fun textQueries(query: String) = listOf(
        multiMatchQuery(
            query = query,
            type = TextQueryType.BoolPrefix,
            fields = listOf("name.ru^12", "name.en^10", "aliases^6"),
        ),
        multiMatchQuery(
            query = query,
            type = TextQueryType.BestFields,
            fields = listOf("name.ru^8", "name.en^7", "aliases^4"),
            fuzziness = "AUTO",
            prefixLength = 1,
        ),
        multiMatchQuery(
            query = query,
            type = TextQueryType.BestFields,
            fields = listOf("description^1", "higherLevels^0.5"),
        ),
    )

}
