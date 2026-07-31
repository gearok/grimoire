package dev.shph.grimoire.repository

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import dev.shph.grimoire.model.Monster
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.model.MonsterSearchResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.stereotype.Repository

@Repository
class ElasticsearchMonsterRepository(
    private val operations: ElasticsearchOperations,
    @Value("\${elasticsearch.monsters-index:monsters-v1}") indexName: String,
) : MonsterRepository {
    private val index = IndexCoordinates.of(indexName)

    override fun search(criteria: MonsterSearch): MonsterSearchResult {
        val hits = operations.search(buildSearchQuery(criteria), Monster::class.java, index)
        return MonsterSearchResult(
            monsters = hits.searchHits.map { it.content },
            total = hits.totalHits,
            page = criteria.page,
            pageSize = criteria.pageSize,
        )
    }

    override fun suggest(query: String, limit: Int): List<Monster> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        return operations.search(buildSuggestionQuery(normalized, limit), Monster::class.java, index)
            .searchHits.map { it.content }
    }

    override fun findById(id: String): Monster? = operations.get(id, Monster::class.java, index)

    internal fun buildSuggestionQuery(query: String, limit: Int): NativeQuery =
        NativeQuery.builder()
            .withQuery(
                Query.of {
                    it.bool { bool ->
                        bool.should(
                            multiMatchQuery(query, TextQueryType.BoolPrefix, NAME_FIELDS),
                            multiMatchQuery(query, TextQueryType.BestFields, NAME_FUZZY_FIELDS, "AUTO", 1),
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

    internal fun buildSearchQuery(criteria: MonsterSearch): NativeQuery {
        val text = criteria.query?.trim()?.takeIf(String::isNotEmpty)
        val scoring = text?.let {
            listOf(
                multiMatchQuery(it, TextQueryType.BoolPrefix, NAME_FIELDS),
                multiMatchQuery(it, TextQueryType.BestFields, NAME_FUZZY_FIELDS, "AUTO", 1),
            )
        }.orEmpty()
        val filters = buildList {
            if (criteria.sizes.isNotEmpty()) {
                add(termsQuery("size", criteria.sizes.sortedBy { it.slug }.map { FieldValue.of(it.slug) }))
            }
            if (criteria.types.isNotEmpty()) {
                add(termsQuery("type", criteria.types.sortedBy { it.slug }.map { FieldValue.of(it.slug) }))
            }
            if (criteria.challenges.isNotEmpty()) {
                add(termsQuery("challenge.value", criteria.challenges.sorted().map(FieldValue::of)))
            }
        }
        val query = searchQuery(scoring, filters)
        val sort = buildList {
            if (text != null) add(Sort.Order.desc("_score"))
            add(Sort.Order.asc("challenge.value"))
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

    private companion object {
        val NAME_FIELDS = listOf("name.ru^12", "name.en^10")
        val NAME_FUZZY_FIELDS = listOf("name.ru^8", "name.en^7")
    }
}
