package dev.shph.grimoire

import dev.shph.grimoire.model.CharacterClass
import dev.shph.grimoire.model.ClassProficiencies
import dev.shph.grimoire.model.LocalizedName
import dev.shph.grimoire.model.Rule
import dev.shph.grimoire.model.RuleSection
import dev.shph.grimoire.model.SourceReference
import dev.shph.grimoire.repository.ClassRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class ClassRoutesTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @TestConfiguration
    class RepositoryConfiguration {
        @Bean
        @Primary
        fun classRepository(): ClassRepository = FakeClassRepository()
    }

    @Test
    fun `class index lists classes alphabetically with header switch`() {
        mockMvc.get("/classes").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Классы</title>")) }
            content { string(org.hamcrest.Matchers.containsString(">КЛАССЫ</a>")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/classes\" class=\"active\"")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/classes/87\"")) }
            content { string(org.hamcrest.Matchers.containsString("Варвар")) }
            content { string(org.hamcrest.Matchers.containsString("Barbarian")) }
            content { string(org.hamcrest.Matchers.containsString("spell-link-index")) }
        }
    }

    @Test
    fun `class detail renders facts prose and source`() {
        mockMvc.get("/classes/87").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Варвар · Гримуар</title>")) }
            content { string(org.hamcrest.Matchers.containsString("back-link")) }
            content { string(org.hamcrest.Matchers.containsString("Кость хитов")) }
            content { string(org.hamcrest.Matchers.containsString("к12")) }
            content { string(org.hamcrest.Matchers.containsString("Спасброски")) }
            content { string(org.hamcrest.Matchers.containsString("Ярость")) }
            content { string(org.hamcrest.Matchers.containsString("ИСТОЧНИК=")) }
            content { string(org.hamcrest.Matchers.containsString("https://dnd.su/class/87-barbarian/")) }
        }
    }

    @Test
    fun `unknown class id returns 404 page`() {
        mockMvc.get("/classes/zzz").andExpect {
            status { isNotFound() }
            content { string(org.hamcrest.Matchers.containsString("Вернуться к классам")) }
        }
    }
}

private class FakeClassRepository : ClassRepository {
    override fun findAll() = listOf(BARBARIAN)
    override fun findById(id: String) = BARBARIAN.takeIf { it.id == id }
}

private val BARBARIAN = CharacterClass(
    id = "87",
    slug = "barbarian",
    name = LocalizedName("Варвар", "Barbarian"),
    hitDie = 12,
    savingThrows = listOf("сила", "телосложение"),
    proficiencies = ClassProficiencies(
        armor = listOf("лёгкие доспехи", "средние доспехи", "щиты"),
        weapons = listOf("простое оружие", "воинское оружие"),
        skills = listOf("атлетика", "запугивание"),
    ),
    sections = listOf(
        RuleSection("Ярость", listOf(Rule("Ярость", "В бою вы сражаетесь с первобытной яростью."))),
    ),
    sources = listOf(SourceReference("PHB", "Player's Handbook")),
    sourceUrl = "https://dnd.su/class/87-barbarian/",
)
