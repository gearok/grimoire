package dev.shph.grimoire

import dev.shph.grimoire.model.AbilityScores
import dev.shph.grimoire.model.ArmorClass
import dev.shph.grimoire.model.ChallengeRating
import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import dev.shph.grimoire.model.HitPoints
import dev.shph.grimoire.model.LocalizedName
import dev.shph.grimoire.model.Monster
import dev.shph.grimoire.model.MonsterRule
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.model.MonsterSearchResult
import dev.shph.grimoire.model.MonsterSection
import dev.shph.grimoire.model.MonsterSpeed
import dev.shph.grimoire.model.SourceReference
import dev.shph.grimoire.repository.MonsterRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class MonsterRoutesTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @TestConfiguration
    class RepositoryConfiguration {
        @Bean
        @Primary
        fun monsterRepository(): MonsterRepository = object : MonsterRepository {
            override fun search(criteria: MonsterSearch) =
                MonsterSearchResult(listOf(GOBLIN), 1, criteria.page, criteria.pageSize)

            override fun suggest(query: String, limit: Int) = listOf(GOBLIN).take(limit)
            override fun findById(id: String) = GOBLIN.takeIf { it.id == id }
        }
    }

    @Test
    fun `monster index has search filters autocomplete and header switch`() {
        mockMvc.get("/monsters").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Гоблин")) }
            content { string(org.hamcrest.Matchers.containsString("hx-get=\"/monsters\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"monster-results\"")) }
            content { string(org.hamcrest.Matchers.containsString("spell-link-index monster-link-index")) }
            content { string(org.hamcrest.Matchers.containsString("data-suggestions-url=\"/api/monsters/suggestions\"")) }
            content { string(org.hamcrest.Matchers.containsString("name=\"size\"")) }
            content { string(org.hamcrest.Matchers.containsString("name=\"type\"")) }
            content { string(org.hamcrest.Matchers.containsString("name=\"challenge\"")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/spells\"")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/monsters\" class=\"active\"")) }
            content { string(org.hamcrest.Matchers.containsString(">БЕСТИАРИЙ</a>")) }
        }
    }

    @Test
    fun `monster htmx result and detail views render`() {
        mockMvc.get("/monsters") { header("HX-Request", "true") }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.VARY, "HX-Request") }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))) }
        }
        mockMvc.get("/monsters/4").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Гоблин · Гримуар</title>")) }
            content { string(org.hamcrest.Matchers.containsString("Ловкий побег")) }
            content { string(org.hamcrest.Matchers.containsString("[ПО 1/4]")) }
            content { string(org.hamcrest.Matchers.containsString("class=\"monster-stats-layout\"")) }
            content { string(org.hamcrest.Matchers.containsString("monster-combat-stats")) }
            content { string(org.hamcrest.Matchers.containsString("monster-ability-stats")) }
            content { string(org.hamcrest.Matchers.containsString("<th>СИЛ</th>")) }
            content { string(org.hamcrest.Matchers.containsString("<td>8</td>")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(">Характеристики</th>"))) }
        }
    }

    @Test
    fun `monster card renders a truncated description`() {
        mockMvc.get("/monsters") { param("view", "cards") }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("class=\"card-description monster-card-summary\"")) }
            content { string(org.hamcrest.Matchers.containsString("class=\"card-description\"")) }
            content { string(org.hamcrest.Matchers.containsString("Гоблины — небольшие злобные гуманоиды")) }
            content { string(org.hamcrest.Matchers.containsString("…")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Конец полного описания."))) }
        }
    }

    @Test
    fun `invalid monster facet is rejected`() {
        mockMvc.get("/monsters") { param("challenge", "1/4") }
            .andExpect { status { isBadRequest() } }
    }
}

private val GOBLIN = Monster(
    id = "4",
    slug = "goblin",
    name = LocalizedName("Гоблин", "Goblin"),
    size = CreatureSize.SMALL,
    type = CreatureType.HUMANOID,
    subtype = "Гоблиноид",
    alignment = "нейтрально-злой",
    armorClass = ArmorClass(15, "кожаный доспех, щит"),
    hitPoints = HitPoints(7, "2к6"),
    speeds = listOf(MonsterSpeed("ходьба", 30)),
    abilities = AbilityScores(8, 14, 10, 10, 8, 8),
    languages = listOf("общий", "гоблинский"),
    challenge = ChallengeRating(0.25, "1/4", 50),
    environments = listOf("лес", "луг", "подземье", "холмы"),
    sections = listOf(
        MonsterSection("Особенности", listOf(MonsterRule("Ловкий побег", "Гоблин может совершить Отход."))),
    ),
    description = """
        Гоблины — небольшие злобные гуманоиды, которые селятся в тёмных пещерах и заброшенных
        руинах. Они действуют сообща, устраивают засады и стараются получить преимущество числом.
        При встрече с более сильным противником гоблины предпочитают отступить, перегруппироваться
        и напасть снова в более выгодный момент. Конец полного описания.
    """.trimIndent(),
    sources = listOf(SourceReference("MM14", "Monster Manual")),
    sourceUrl = "https://dnd.su/bestiary/4-goblin/",
)
