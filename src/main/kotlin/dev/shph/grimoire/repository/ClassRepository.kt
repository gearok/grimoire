package dev.shph.grimoire.repository

import dev.shph.grimoire.model.CharacterClass

interface ClassRepository {
    /** All classes, sorted alphabetically by their Russian name. */
    fun findAll(): List<CharacterClass>

    fun findById(id: String): CharacterClass?
}
