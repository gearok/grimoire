package dev.shph.grimoire.repository

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.RefreshPolicy
import org.springframework.data.elasticsearch.core.document.Document
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.stereotype.Repository
import java.util.concurrent.atomic.AtomicBoolean

@Repository
class ElasticsearchSpellRepository(
    private val operations: ElasticsearchOperations,
    @Value("\${elasticsearch.index:spells-v1}") indexName: String,
) : SpellRepository {
    private val index = IndexCoordinates.of(indexName)
    private val initialized = AtomicBoolean(false)
    private val initializationLock = Any()

    override fun search(criteria: SpellSearch): SpellSearchResult {
        ensureIndex()
        val hits = request("search spells") {
            operations.search(buildSearchQuery(criteria), Spell::class.java, index)
        }
        return SpellSearchResult(
            spells = hits.searchHits.map { it.content },
            total = hits.totalHits,
            page = criteria.page,
            pageSize = criteria.pageSize,
        )
    }

    override fun findById(id: String): Spell? {
        ensureIndex()
        return request("load spell") {
            operations.get(id, Spell::class.java, index)
        }
    }

    override fun save(spell: Spell) {
        ensureIndex()
        request("save spell") {
            operations.withRefreshPolicy(RefreshPolicy.WAIT_UNTIL).save(spell, index)
        }
    }

    private fun ensureIndex() {
        if (initialized.get()) return
        synchronized(initializationLock) {
            if (initialized.get()) return
            request("initialize spell index") {
                val indexOperations = operations.indexOps(index)
                if (!indexOperations.exists()) {
                    val created = indexOperations.create(
                        Document.parse(resourceText("elasticsearch/spells-settings.json")),
                        Document.parse(resourceText("elasticsearch/spells-mapping.json")),
                    )
                    if (!created) {
                        throw ElasticsearchUnavailableException("Elasticsearch did not create index ${index.indexName}")
                    }
                }
            }
            initialized.set(true)
        }
    }

    private fun <T> request(operation: String, block: () -> T): T =
        try {
            block()
        } catch (cause: ElasticsearchUnavailableException) {
            throw cause
        } catch (cause: RuntimeException) {
            throw ElasticsearchUnavailableException("Could not $operation", cause)
        }

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
        val query = if (scoringQueries.isEmpty() && filters.isEmpty()) {
            Query.of { it.matchAll { matchAll -> matchAll } }
        } else {
            Query.of {
                it.bool { bool ->
                    if (scoringQueries.isNotEmpty()) {
                        bool.should(scoringQueries).minimumShouldMatch("1")
                    }
                    if (filters.isNotEmpty()) bool.filter(filters)
                    bool
                }
            }
        }
        val sort = buildList {
            if (searchText != null) add(Sort.Order.desc("_score"))
            add(Sort.Order.asc("level"))
            add(Sort.Order.asc("name.ru.keyword"))
        }
        return NativeQuery.builder()
            .withQuery(query)
            .withPageable(PageRequest.of(criteria.page - 1, criteria.pageSize, Sort.by(sort)))
            .build()
    }

    private fun textQueries(query: String) = listOf(
        multiMatch(
            query = query,
            type = TextQueryType.BoolPrefix,
            fields = listOf("name.ru^12", "name.en^10", "aliases^6"),
        ),
        multiMatch(
            query = query,
            type = TextQueryType.BestFields,
            fields = listOf("name.ru^8", "name.en^7", "aliases^4"),
            fuzziness = "AUTO",
            prefixLength = 1,
        ),
        multiMatch(
            query = query,
            type = TextQueryType.BestFields,
            fields = listOf("description^1", "higherLevels^0.5"),
        ),
    )

    private fun multiMatch(
        query: String,
        type: TextQueryType,
        fields: List<String>,
        fuzziness: String? = null,
        prefixLength: Int? = null,
    ) = Query.of {
        it.multiMatch { multiMatch ->
            multiMatch.query(query).type(type).fields(fields)
            fuzziness?.let(multiMatch::fuzziness)
            prefixLength?.let(multiMatch::prefixLength)
            multiMatch
        }
    }

    private fun termsQuery(field: String, values: List<FieldValue>) =
        Query.of {
            it.terms { terms ->
                terms.field(field).terms { termValues -> termValues.value(values) }
            }
        }

    private fun resourceText(path: String): String =
        ClassPathResource(path).inputStream.bufferedReader().use { it.readText() }
}
