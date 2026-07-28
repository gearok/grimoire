package dev.shph.grimoire

import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult
import dev.shph.grimoire.repository.SpellRepository
import dev.shph.grimoire.seed.SpellSeeder
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SpellSeederTest {
    @Test
    fun `resource seeds are inserted once and never overwrite existing spells`() = kotlinx.coroutines.runBlocking {
        val repository = SeedTestRepository()
        val seeder = SpellSeeder.fromResource(
            repository = repository,
            json = Json { ignoreUnknownKeys = true },
        )

        val firstRun = seeder.seedMissing()
        val secondRun = seeder.seedMissing()

        assertEquals(5, firstRun.inserted)
        assertEquals(0, firstRun.existing)
        assertEquals(0, secondRun.inserted)
        assertEquals(5, secondRun.existing)
        assertEquals(5, repository.spells.size)
    }
}

private class SeedTestRepository : SpellRepository {
    val spells = mutableMapOf<String, Spell>()

    override suspend fun search(criteria: SpellSearch) =
        SpellSearchResult(spells.values.toList(), spells.size.toLong(), criteria.page, criteria.pageSize)

    override suspend fun findById(id: String): Spell? = spells[id]

    override suspend fun save(spell: Spell) {
        spells[spell.id] = spell
    }
}
