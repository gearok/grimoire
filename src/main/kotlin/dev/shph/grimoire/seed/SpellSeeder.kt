package dev.shph.grimoire.seed

import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.repository.SpellRepository
import kotlinx.serialization.json.Json

class SpellSeeder(
    private val repository: SpellRepository,
    private val spells: List<Spell>,
) {
    suspend fun seedMissing(): SeedResult {
        var inserted = 0
        var existing = 0

        spells.forEach { spell ->
            if (repository.findById(spell.id) == null) {
                repository.save(spell)
                inserted++
            } else {
                existing++
            }
        }

        return SeedResult(inserted = inserted, existing = existing)
    }

    companion object {
        fun fromResource(
            repository: SpellRepository,
            json: Json,
            resourcePath: String = "seed/spells.json",
            classLoader: ClassLoader = SpellSeeder::class.java.classLoader,
        ): SpellSeeder {
            val contents = classLoader.getResourceAsStream(resourcePath)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Seed resource '$resourcePath' was not found")

            return SpellSeeder(
                repository = repository,
                spells = json.decodeFromString<List<Spell>>(contents),
            )
        }
    }
}

data class SeedResult(
    val inserted: Int,
    val existing: Int,
)
