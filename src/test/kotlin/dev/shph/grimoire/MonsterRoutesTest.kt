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
import dev.shph.grimoire.view.MonsterIndexView
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class MonsterRoutesTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var repository: RecordingMonsterRepository

    @TestConfiguration
    class RepositoryConfiguration {
        @Bean
        @Primary
        fun monsterRepository() = RecordingMonsterRepository()
    }

    @Test
    fun `monster index has search filters autocomplete and header switch`() {
        mockMvc.get("/monsters") { param("q", "goblin") }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.VARY, "HX-Request") }
            content { string(org.hamcrest.Matchers.containsString("Гоблин")) }
            content { string(org.hamcrest.Matchers.containsString("<body hx-boost=\"true\">")) }
            content { string(org.hamcrest.Matchers.containsString("hx-get=\"/monsters\"")) }
            content { string(org.hamcrest.Matchers.containsString("hx-sync=\"this:replace\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"monster-results\"")) }
            content { string(org.hamcrest.Matchers.containsString("class=\"clear-link\"")) }
            content { string(org.hamcrest.Matchers.containsString("hx-target=\"body\"")) }
            content { string(org.hamcrest.Matchers.containsString("spell-link-index monster-link-index")) }
            content { string(org.hamcrest.Matchers.containsString("hx-get=\"/monsters/suggestions\"")) }
            content { string(org.hamcrest.Matchers.containsString("hx-trigger=\"input changed delay:150ms\"")) }
            content { string(org.hamcrest.Matchers.containsString("hx-target=\"#monster-suggestions\"")) }
            content { string(org.hamcrest.Matchers.containsString("hx-sync=\"this:replace\"")) }
            content { string(org.hamcrest.Matchers.containsString("name=\"size\"")) }
            content { string(org.hamcrest.Matchers.containsString("name=\"type\"")) }
            content { string(org.hamcrest.Matchers.containsString("name=\"challenge\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"monster-result-mode\"")) }
            content { string(org.hamcrest.Matchers.containsString("value=\"index\"")) }
            content { string(org.hamcrest.Matchers.containsString("form=\"monster-filters\"")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/spells\"")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/monsters\" class=\"active\"")) }
            content { string(org.hamcrest.Matchers.containsString(">БЕСТИАРИЙ</a>")) }
        }

        assertEquals(1_000, repository.lastSearch.pageSize)
    }

    @Test
    fun `monster htmx result and detail views render`() {
        mockMvc.get("/monsters") { header("HX-Request", "true") }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.VARY, "HX-Request") }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))) }
            content { string(org.hamcrest.Matchers.containsString("value=\"index\"")) }
            content { string(org.hamcrest.Matchers.containsString("form=\"monster-filters\"")) }
        }
        assertEquals(1_000, repository.lastSearch.pageSize)
        mockMvc.get("/monsters/4").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Гоблин · Гримуар</title>")) }
            content { string(org.hamcrest.Matchers.containsString("hx-push-url=\"true\"")) }
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
    fun `boosted monster navigation returns title active section form and results`() {
        mockMvc.get("/monsters") {
            header("HX-Request", "true")
            header("HX-Boosted", "true")
        }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.VARY, "HX-Request") }
            content { string(org.hamcrest.Matchers.containsString("<html")) }
            content { string(org.hamcrest.Matchers.containsString("<title>Бестиарий</title>")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/monsters\" class=\"active\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"monster-filters\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"monster-results\"")) }
        }
    }

    @Test
    fun `monster card renders a truncated description`() {
        mockMvc.get("/monsters") { param("view", "index", "cards") }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<div class=\"card-heading\">")) }
            content { string(org.hamcrest.Matchers.containsString("<h2>Гоблин</h2>")) }
            content { string(org.hamcrest.Matchers.containsString("<span class=\"level-badge\">[ПО 1/4]</span>")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<span class=\"school-name\">ГУМАНОИД</span>"))) }
            content { string(org.hamcrest.Matchers.containsString("class=\"card-description monster-card-summary\"")) }
            content { string(org.hamcrest.Matchers.containsString("class=\"card-description\"")) }
            content { string(org.hamcrest.Matchers.containsString("Гоблины — небольшие злобные гуманоиды")) }
            content { string(org.hamcrest.Matchers.containsString("…")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Конец полного описания."))) }
        }

        assertEquals(30, repository.lastSearch.pageSize)
    }

    @Test
    fun `automatic monster htmx filtering retains card mode through pagination`() {
        val result = mockMvc.get("/monsters") {
            header("HX-Request", "true")
            param("type", "humanoid")
            param("view", "cards")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("spell-grid")) }
            content { string(org.hamcrest.Matchers.containsString("value=\"cards\"")) }
            content { string(org.hamcrest.Matchers.containsString("form=\"monster-filters\"")) }
            content { string(org.hamcrest.Matchers.containsString("view=cards&amp;page=2")) }
        }.andReturn()

        val view = result.modelAndView!!.model["view"] as MonsterIndexView
        assertEquals(30, repository.lastSearch.pageSize)
        assertTrue(view.monsterGroups.isEmpty())
        assertFalse(view.monsters.isEmpty())
    }

    @Test
    fun `monster pagination retains explicit index mode and page size`() {
        val result = mockMvc.get("/monsters") {
            param("page", "2")
            param("view", "index")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("view=index&amp;page=1")) }
        }.andReturn()

        val view = result.modelAndView!!.model["view"] as MonsterIndexView
        assertEquals(1_000, repository.lastSearch.pageSize)
        assertTrue(view.monsters.isEmpty())
        assertFalse(view.monsterGroups.isEmpty())
    }

    @Test
    fun `invalid monster facet is rejected`() {
        mockMvc.get("/monsters") { param("challenge", "1/4") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `invalid monster result mode is rejected`() {
        mockMvc.get("/monsters") { param("view", "tiles") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `invalid monster page is rejected`() {
        mockMvc.get("/monsters") { param("page", "nope") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `monster html suggestions render reusable option markup`() {
        mockMvc.get("/monsters/suggestions") {
            param("q", "gob")
            accept = MediaType.TEXT_HTML
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString("id=\"monster-suggestions\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"monster-suggestion-0\"")) }
            content { string(org.hamcrest.Matchers.containsString("role=\"option\"")) }
            content { string(org.hamcrest.Matchers.containsString("data-suggestion-value=\"Гоблин\"")) }
            content { string(org.hamcrest.Matchers.containsString("Гоблин")) }
            content { string(org.hamcrest.Matchers.containsString("Goblin")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))) }
        }
    }

    @Test
    fun `monster json suggestion api remains compatible`() {
        mockMvc.get("/api/monsters/suggestions") {
            param("q", "gob")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value("4") }
            jsonPath("$[0].nameRu") { value("Гоблин") }
            jsonPath("$[0].nameEn") { value("Goblin") }
        }
    }
}

class RecordingMonsterRepository : MonsterRepository {
    lateinit var lastSearch: MonsterSearch

    override fun search(criteria: MonsterSearch): MonsterSearchResult {
        lastSearch = criteria
        return MonsterSearchResult(listOf(GOBLIN), 1_001, criteria.page, criteria.pageSize)
    }

    override fun suggest(query: String, limit: Int) = listOf(GOBLIN).take(limit)
    override fun findById(id: String) = GOBLIN.takeIf { it.id == id }
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
