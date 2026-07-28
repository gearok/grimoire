package dev.shph.grimoire.seed

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.repository.SpellRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

class SpellSeeder(
    private val repository: SpellRepository,
    private val spells: List<Spell>,
) {
    fun seedMissing(): SeedResult {
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
            objectMapper: ObjectMapper,
            resourcePath: String = "seed/spells.json",
        ): SpellSeeder {
            val resource = ClassPathResource(resourcePath)
            check(resource.exists()) { "Seed resource '$resourcePath' was not found" }
            val spells = resource.inputStream.use {
                objectMapper.readValue(it, object : TypeReference<List<Spell>>() {})
            }
            return SpellSeeder(repository, spells)
        }
    }
}

@Component
@ConditionalOnProperty(prefix = "seed", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SpellSeedRunner(
    repository: SpellRepository,
    objectMapper: ObjectMapper,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)
    private val seeder = SpellSeeder.fromResource(repository, objectMapper)

    override fun run(args: ApplicationArguments) {
        runCatching { seeder.seedMissing() }
            .onSuccess {
                log.info("Spell seed complete: {} inserted, {} already present", it.inserted, it.existing)
            }
            .onFailure {
                log.error("Could not seed spells; the application will continue without seed data", it)
            }
    }
}

data class SeedResult(
    val inserted: Int,
    val existing: Int,
)
