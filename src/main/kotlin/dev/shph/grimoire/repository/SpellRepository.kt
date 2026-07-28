package dev.shph.grimoire.repository

import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult

interface SpellRepository {
    suspend fun search(criteria: SpellSearch): SpellSearchResult
    suspend fun findById(id: String): Spell?
    suspend fun save(spell: Spell)
}

class ElasticsearchUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
