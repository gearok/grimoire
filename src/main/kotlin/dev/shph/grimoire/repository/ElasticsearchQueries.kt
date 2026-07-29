package dev.shph.grimoire.repository

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType

internal fun multiMatchQuery(
    query: String,
    type: TextQueryType,
    fields: List<String>,
    fuzziness: String? = null,
    prefixLength: Int? = null,
) = Query.of {
    it.multiMatch { match ->
        match.query(query).type(type).fields(fields)
        fuzziness?.let(match::fuzziness)
        prefixLength?.let(match::prefixLength)
        match
    }
}

internal fun termsQuery(field: String, values: List<FieldValue>) = Query.of {
    it.terms { terms -> terms.field(field).terms { it.value(values) } }
}

internal fun searchQuery(
    scoringQueries: List<Query>,
    filters: List<Query>,
): Query {
    if (scoringQueries.isEmpty() && filters.isEmpty()) {
        return Query.of { it.matchAll { matchAll -> matchAll } }
    }
    return Query.of {
        it.bool { bool ->
            if (scoringQueries.isNotEmpty()) {
                bool.should(scoringQueries).minimumShouldMatch("1")
            }
            if (filters.isNotEmpty()) bool.filter(filters)
            bool
        }
    }
}
