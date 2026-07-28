package dev.shph.grimoire

import dev.shph.grimoire.model.CastingTime
import dev.shph.grimoire.model.CastingTimeType
import dev.shph.grimoire.model.ClassAccess
import dev.shph.grimoire.model.LocalizedName
import dev.shph.grimoire.model.MagicSchool
import dev.shph.grimoire.model.SourceReference
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellComponents
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult
import dev.shph.grimoire.repository.SpellRepository
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
import org.springframework.test.web.servlet.post
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
class SpellRoutesTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @TestConfiguration
    class RepositoryConfiguration {
        @Bean
        @Primary
        fun spellRepository(): SpellRepository = object : SpellRepository {
            override fun search(criteria: SpellSearch) =
                SpellSearchResult(listOf(FIREBALL), 1, criteria.page, criteria.pageSize)

            override fun suggest(query: String, limit: Int): List<Spell> =
                listOf(FIREBALL).filter {
                    it.name.ru.contains(query, ignoreCase = true) ||
                        it.name.en.contains(query, ignoreCase = true)
                }.take(limit)

            override fun findById(id: String): Spell? = FIREBALL.takeIf { it.id == id }

        }
    }

    @Test
    fun `spell index renders a searchable htmx page`() {
        mockMvc.get("/spells") { param("q", "fire") }
            .andExpect {
                status { isOk() }
                content { string(org.hamcrest.Matchers.containsString("Огненный шар")) }
                content { string(org.hamcrest.Matchers.containsString("hx-get=\"/spells\"")) }
                content { string(org.hamcrest.Matchers.containsString("id=\"spell-results\"")) }
                content { string(org.hamcrest.Matchers.containsString("href=\"/spells\" class=\"active\"")) }
                content { string(org.hamcrest.Matchers.containsString(">КНИГА ЗАКЛИНАНИЙ</a>")) }
                content { string(org.hamcrest.Matchers.containsString("href=\"/monsters\"")) }
                content { string(org.hamcrest.Matchers.containsString("data-theme-toggle")) }
                content { string(org.hamcrest.Matchers.containsString("data-theme-icon")) }
                content { string(org.hamcrest.Matchers.containsString(">wb_sunny</span>")) }
                content { string(org.hamcrest.Matchers.containsString("Material+Icons+Outlined")) }
                content { string(org.hamcrest.Matchers.containsString("/static/theme.js")) }
                content { string(org.hamcrest.Matchers.containsString("/static/multi-select.js")) }
                content { string(org.hamcrest.Matchers.containsString("/static/filter-panel.js")) }
                content { string(org.hamcrest.Matchers.containsString("/static/spell-suggestions.js")) }
                content { string(org.hamcrest.Matchers.containsString("data-filters-toggle")) }
                content { string(org.hamcrest.Matchers.containsString("data-spell-suggest")) }
                content { string(org.hamcrest.Matchers.containsString("role=\"combobox\"")) }
                content { string(org.hamcrest.Matchers.containsString(">filter_alt</span>")) }
                content { string(org.hamcrest.Matchers.containsString("class=\"material-icons-outlined search-submit-icon\"")) }
                content { string(org.hamcrest.Matchers.containsString("name=\"view\"")) }
                content { string(org.hamcrest.Matchers.containsString("value=\"cards\"")) }
                content { string(org.hamcrest.Matchers.containsString("spell-link-index")) }
                content { string(org.hamcrest.Matchers.containsString("class=\"spell-link-level\"")) }
                content { string(org.hamcrest.Matchers.containsString("class=\"spell-link-school\"")) }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("spell-card"))) }
            }
    }

    @Test
    fun `htmx request returns only the result fragment`() {
        val result = mockMvc.get("/spells") { header("HX-Request", "true") }
            .andExpect {
                status { isOk() }
                header { string(HttpHeaders.VARY, "HX-Request") }
                content { string(org.hamcrest.Matchers.containsString("spell-link-index")) }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("spell-card"))) }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))) }
            }
            .andReturn()

        assertNotNull(result.response.contentAsString)
    }

    @Test
    fun `submitting search renders spell cards`() {
        mockMvc.get("/spells") {
            param("q", "fire")
            param("view", "cards")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("spell-grid")) }
            content { string(org.hamcrest.Matchers.containsString("spell-card")) }
            content { string(org.hamcrest.Matchers.containsString("Конец полного описания.")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("spell-link-index"))) }
        }
    }

    @Test
    fun `unknown spell is a 404`() {
        mockMvc.get("/spells/missing").andExpect { status { isNotFound() } }
    }

    @Test
    fun `spell detail is rendered from its resource template`() {
        mockMvc.get("/spells/205").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Огненный шар · Гримуар</title>")) }
            content { string(org.hamcrest.Matchers.containsString("На больших уровнях")) }
            content { string(org.hamcrest.Matchers.containsString("class=\"level-badge\"")) }
            content { string(org.hamcrest.Matchers.containsString(">3 УРОВЕНЬ</span>")) }
            content { string(org.hamcrest.Matchers.containsString("<th class=\"width-min\">Школа</th>")) }
            content { string(org.hamcrest.Matchers.containsString("<td class=\"width-auto\">Воплощение</td>")) }
            content { string(org.hamcrest.Matchers.containsString("PH14")) }
            content { string(org.hamcrest.Matchers.containsString("class=\"search-panel detail-search-panel\"")) }
            content { string(org.hamcrest.Matchers.containsString("action=\"/spells\"")) }
            content { string(org.hamcrest.Matchers.containsString("data-spell-suggest")) }
            content { string(org.hamcrest.Matchers.containsString("name=\"q\"")) }
            content { string(org.hamcrest.Matchers.containsString("data-filters-toggle")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"detail-level-value\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"detail-school-value\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"detail-class-value\"")) }
        }
    }

    @Test
    fun `invalid search filters are a 400`() {
        mockMvc.get("/spells") { param("level", "third") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `spell api retains the existing lowercase wire format`() {
        mockMvc.get("/api/spells/205") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.school") { value("evocation") }
            jsonPath("$.castingTime.type") { value("action") }
            jsonPath("$.name.ru") { value("Огненный шар") }
        }
    }

    @Test
    fun `spell suggestions return compact bilingual matches`() {
        mockMvc.get("/api/spells/suggestions") {
            param("q", "fire")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value("205") }
            jsonPath("$[0].nameRu") { value("Огненный шар") }
            jsonPath("$[0].nameEn") { value("Fireball") }
            jsonPath("$[0].description") { doesNotExist() }
        }
    }

    @Test
    fun `spell api is read only`() {
        mockMvc.post("/api/spells") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `filter selects accept and retain multiple values`() {
        mockMvc.get("/spells") {
            param("level", "1", "3")
            param("school", "evocation", "enchantment")
            param("class", "волшебник")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("id=\"level-value\"")) }
            content { string(org.hamcrest.Matchers.containsString("name=\"level\"")) }
            content { string(org.hamcrest.Matchers.containsString("value=\"1\" checked=\"checked\"")) }
            content { string(org.hamcrest.Matchers.containsString("value=\"3\" checked=\"checked\"")) }
            content { string(org.hamcrest.Matchers.containsString("value=\"evocation\" checked=\"checked\"")) }
            content { string(org.hamcrest.Matchers.containsString("value=\"enchantment\" checked=\"checked\"")) }
            content { string(org.hamcrest.Matchers.containsString("value=\"волшебник\" checked=\"checked\"")) }
        }
    }
}

private val FIREBALL = Spell(
    id = "205",
    slug = "fireball",
    name = LocalizedName(ru = "Огненный шар", en = "Fireball"),
    level = 3,
    school = MagicSchool.EVOCATION,
    castingTime = CastingTime("1 действие", CastingTimeType.ACTION),
    range = "150 футов",
    components = SpellComponents(
        verbal = true,
        somatic = true,
        material = true,
        materialDescription = "крошечный шарик из гуано летучей мыши и серы",
    ),
    duration = "Мгновенная",
    classes = listOf(ClassAccess("волшебник"), ClassAccess("чародей")),
    description = """
        Яркая вспышка превращается во взрыв пламени. Все существа в области действия
        совершают спасбросок Ловкости, получая урон огнём при провале или половину этого
        урона при успехе. Пламя огибает углы и поджигает горючие предметы. Конец полного описания.
    """.trimIndent(),
    higherLevels = "Урон увеличивается при использовании ячейки более высокого уровня.",
    damageTypes = listOf("огонь"),
    sources = listOf(SourceReference("PH14", "Player's Handbook")),
    sourceUrl = "https://dnd.su/spells/205-fireball/",
)
