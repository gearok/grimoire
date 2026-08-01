package dev.shph.grimoire.repository

import dev.shph.grimoire.model.CharacterClass
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Repository
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

/**
 * Serves the bundled class dataset scraped from dnd.su. Classes are a small, fixed set, so
 * rather than live in Elasticsearch they are shipped as committed JSON (`data/classes.json`)
 * and loaded into memory once at startup.
 */
@Repository
class JsonClassRepository(resourcePath: String = "data/classes.json") : ClassRepository {
    private val all: List<CharacterClass>
    private val byId: Map<String, CharacterClass>

    init {
        val mapper = jacksonObjectMapper()
        val classes: List<CharacterClass> = ClassPathResource(resourcePath).inputStream.use(mapper::readValue)
        all = classes.sortedBy { it.name.ru.lowercase() }
        byId = all.associateBy { it.id }
    }

    override fun findAll(): List<CharacterClass> = all

    override fun findById(id: String): CharacterClass? = byId[id]
}
