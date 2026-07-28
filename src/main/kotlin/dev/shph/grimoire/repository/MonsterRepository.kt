package dev.shph.grimoire.repository

import dev.shph.grimoire.model.Monster
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.model.MonsterSearchResult

interface MonsterRepository {
    fun search(criteria: MonsterSearch): MonsterSearchResult
    fun suggest(query: String, limit: Int = 8): List<Monster>
    fun findById(id: String): Monster?
}
