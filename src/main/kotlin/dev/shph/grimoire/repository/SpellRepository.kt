package dev.shph.grimoire.repository

import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult

interface SpellRepository {
    fun search(criteria: SpellSearch): SpellSearchResult
    fun findById(id: String): Spell?
    fun save(spell: Spell)
}

class ElasticsearchUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
