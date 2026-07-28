package dev.shph.grimoire.repository

import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult

interface SpellRepository {
    fun search(criteria: SpellSearch): SpellSearchResult
    fun suggest(query: String, limit: Int = 8): List<Spell>
    fun findById(id: String): Spell?
}
