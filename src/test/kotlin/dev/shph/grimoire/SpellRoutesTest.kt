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
import dev.shph.grimoire.view.SpellIndexView
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class SpellRoutesTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var repository: RecordingSpellRepository

    @TestConfiguration
    class RepositoryConfiguration {
        @Bean
        @Primary
        fun spellRepository() = RecordingSpellRepository()
    }

    @Test
    fun `spell index renders a searchable htmx page`() {
        mockMvc.get("/spells") { param("q", "fire") }
            .andExpect {
                status { isOk() }
                header { string(HttpHeaders.VARY, "HX-Request") }
                content { string(org.hamcrest.Matchers.containsString("Огненный шар")) }
                content { string(org.hamcrest.Matchers.containsString("<body hx-boost=\"true\">")) }
                content { string(org.hamcrest.Matchers.containsString("hx-get=\"/spells\"")) }
                content { string(org.hamcrest.Matchers.containsString("name=\"htmx-config\"")) }
                content { string(org.hamcrest.Matchers.containsString("\"[45]..\"")) }
                content { string(org.hamcrest.Matchers.containsString("hx-sync=\"this:replace\"")) }
                content { string(org.hamcrest.Matchers.containsString("id=\"spell-results\"")) }
                content { string(org.hamcrest.Matchers.containsString("class=\"clear-link\"")) }
                content { string(org.hamcrest.Matchers.containsString("hx-target=\"body\"")) }
                content { string(org.hamcrest.Matchers.containsString("href=\"/spells\" class=\"active\"")) }
                content { string(org.hamcrest.Matchers.containsString(">КНИГА ЗАКЛИНАНИЙ</a>")) }
                content { string(org.hamcrest.Matchers.containsString("href=\"/monsters\"")) }
                content { string(org.hamcrest.Matchers.containsString("rel=\"icon\" type=\"image/png\" sizes=\"400x400\" href=\"/static/grimoire.png\"")) }
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
                content { string(org.hamcrest.Matchers.containsString("id=\"spell-result-mode\"")) }
                content { string(org.hamcrest.Matchers.containsString("value=\"index\"")) }
                content { string(org.hamcrest.Matchers.containsString("form=\"spell-filters\"")) }
                content { string(org.hamcrest.Matchers.containsString("hx-get=\"/spells/suggestions\"")) }
                content { string(org.hamcrest.Matchers.containsString("hx-trigger=\"input changed delay:150ms\"")) }
                content { string(org.hamcrest.Matchers.containsString("hx-target=\"#spell-suggestions\"")) }
                content { string(org.hamcrest.Matchers.containsString("hx-sync=\"this:replace\"")) }
                content { string(org.hamcrest.Matchers.containsString(">filter_alt</span>")) }
                content { string(org.hamcrest.Matchers.containsString("class=\"material-icons-outlined search-submit-icon\"")) }
                content { string(org.hamcrest.Matchers.containsString("name=\"view\"")) }
                content { string(org.hamcrest.Matchers.containsString("value=\"cards\"")) }
                content { string(org.hamcrest.Matchers.containsString("spell-link-index")) }
                content { string(org.hamcrest.Matchers.containsString("class=\"spell-link-level\"")) }
                content { string(org.hamcrest.Matchers.containsString("class=\"spell-link-school\"")) }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("spell-card"))) }
            }

        assertEquals(1_000, repository.lastSearch.pageSize)
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
                content { string(org.hamcrest.Matchers.containsString("value=\"index\"")) }
                content { string(org.hamcrest.Matchers.containsString("form=\"spell-filters\"")) }
            }
            .andReturn()

        assertNotNull(result.response.contentAsString)
        assertEquals(1_000, repository.lastSearch.pageSize)
    }

    @Test
    fun `boosted htmx request returns the complete page for navigation and clear filters`() {
        mockMvc.get("/spells") {
            header("HX-Request", "true")
            header("HX-Boosted", "true")
        }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.VARY, "HX-Request") }
            content { string(org.hamcrest.Matchers.containsString("<html")) }
            content { string(org.hamcrest.Matchers.containsString("<title>Заклинания</title>")) }
            content { string(org.hamcrest.Matchers.containsString("<body hx-boost=\"true\">")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/spells\" class=\"active\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"spell-filters\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"spell-results\"")) }
        }
    }

    @Test
    fun `submitting search renders spell cards`() {
        mockMvc.get("/spells") {
            param("q", "fire")
            param("view", "index", "cards")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("spell-grid")) }
            content { string(org.hamcrest.Matchers.containsString("spell-card")) }
            content { string(org.hamcrest.Matchers.containsString("<div class=\"card-heading\">")) }
            content { string(org.hamcrest.Matchers.containsString("<h2>Огненный шар</h2>")) }
            content { string(org.hamcrest.Matchers.containsString("<span class=\"level-badge\">[3 уровень]</span>")) }
            content { string(org.hamcrest.Matchers.containsString("<span>ВОПЛОЩЕНИЕ</span>")) }
            content { string(org.hamcrest.Matchers.containsString("<span>ВРЕМЯ=1 действие</span>")) }
            content { string(org.hamcrest.Matchers.containsString("Конец полного описания.")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("spell-link-index"))) }
        }

        assertEquals(30, repository.lastSearch.pageSize)
    }

    @Test
    fun `automatic htmx filtering retains card mode and its page size`() {
        val result = mockMvc.get("/spells") {
            header("HX-Request", "true")
            param("q", "fire")
            param("level", "3")
            param("view", "cards")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("spell-grid")) }
            content { string(org.hamcrest.Matchers.containsString("value=\"cards\"")) }
            content { string(org.hamcrest.Matchers.containsString("form=\"spell-filters\"")) }
            content { string(org.hamcrest.Matchers.containsString("view=cards&amp;page=2")) }
            content { string(org.hamcrest.Matchers.containsString("hx-swap=\"innerHTML show:top\"")) }
        }.andReturn()

        val view = result.modelAndView!!.model["view"] as SpellIndexView
        assertEquals(30, repository.lastSearch.pageSize)
        assertTrue(view.spellGroups.isEmpty())
        assertFalse(view.spells.isEmpty())
    }

    @Test
    fun `spell pagination retains explicit index mode and page size`() {
        val result = mockMvc.get("/spells") {
            param("page", "2")
            param("view", "index")
        }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("view=index&amp;page=1")) }
        }.andReturn()

        val view = result.modelAndView!!.model["view"] as SpellIndexView
        assertEquals(1_000, repository.lastSearch.pageSize)
        assertTrue(view.spells.isEmpty())
        assertFalse(view.spellGroups.isEmpty())
    }

    @Test
    fun `unknown spell is a 404`() {
        mockMvc.get("/spells/missing").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString("404")) }
            content { string(org.hamcrest.Matchers.containsString("Spell not found")) }
            content { string(org.hamcrest.Matchers.containsString("Вернуться к заклинаниям")) }
            content { string(org.hamcrest.Matchers.containsString("<html")) }
        }
    }

    @Test
    fun `spell detail is rendered from its resource template`() {
        mockMvc.get("/spells/205").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Огненный шар · Гримуар</title>")) }
            content { string(org.hamcrest.Matchers.containsString("На больших уровнях")) }
            content { string(org.hamcrest.Matchers.containsString("class=\"level-badge\"")) }
            content { string(org.hamcrest.Matchers.containsString(">[3 УРОВЕНЬ]</span>")) }
            content { string(org.hamcrest.Matchers.containsString("<th class=\"width-min\">Школа</th>")) }
            content { string(org.hamcrest.Matchers.containsString("<td class=\"width-auto\">Воплощение</td>")) }
            content { string(org.hamcrest.Matchers.containsString("PH14")) }
            content { string(org.hamcrest.Matchers.containsString("class=\"search-panel detail-search-panel\"")) }
            content { string(org.hamcrest.Matchers.containsString("action=\"/spells\"")) }
            content { string(org.hamcrest.Matchers.containsString("hx-push-url=\"true\"")) }
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
            .andExpect {
                status { isBadRequest() }
                content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
                content { string(org.hamcrest.Matchers.containsString("Invalid search parameters")) }
                content { string(org.hamcrest.Matchers.containsString("<html")) }
            }
    }

    @Test
    fun `htmx page error is an html fragment with its error status`() {
        mockMvc.get("/spells") {
            param("level", "third")
            header("HX-Request", "true")
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString("role=\"alert\"")) }
            content { string(org.hamcrest.Matchers.containsString("Invalid search parameters")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))) }
        }
    }

    @Test
    fun `page storage failure is a 503 html response`() {
        mockMvc.get("/spells") { param("q", "unavailable") }.andExpect {
            status { isServiceUnavailable() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString("Search storage is temporarily unavailable")) }
            content { string(org.hamcrest.Matchers.containsString("<html")) }
        }
    }

    @Test
    fun `api errors retain json status and shape`() {
        mockMvc.get("/api/spells/missing") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("Spell not found") }
        }

        mockMvc.get("/api/spells/{id}", " ") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("Missing spell id") }
        }

        mockMvc.get("/api/spells/unavailable") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isServiceUnavailable() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error") { value("Search storage is temporarily unavailable") }
        }
    }

    @Test
    fun `invalid spell result mode is a 400`() {
        mockMvc.get("/spells") { param("view", "tiles") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `unsupported character class is a 400`() {
        mockMvc.get("/spells") { param("class", "маг") }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `invalid spell page is a 400`() {
        mockMvc.get("/spells") { param("page", "0") }
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
    fun `spell html suggestions render reusable option markup`() {
        mockMvc.get("/spells/suggestions") {
            param("q", "fire")
            accept = MediaType.TEXT_HTML
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString("id=\"spell-suggestions\"")) }
            content { string(org.hamcrest.Matchers.containsString("role=\"listbox\"")) }
            content { string(org.hamcrest.Matchers.containsString("id=\"spell-suggestion-0\"")) }
            content { string(org.hamcrest.Matchers.containsString("role=\"option\"")) }
            content { string(org.hamcrest.Matchers.containsString("data-suggestion-value=\"Огненный шар\"")) }
            content { string(org.hamcrest.Matchers.containsString("Огненный шар")) }
            content { string(org.hamcrest.Matchers.containsString("Fireball")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))) }
        }
    }

    @Test
    fun `empty spell suggestion query returns a closed list`() {
        mockMvc.get("/spells/suggestions").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("id=\"spell-suggestions\"")) }
            content { string(org.hamcrest.Matchers.containsString("hidden=\"hidden\"")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("role=\"option\""))) }
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

class RecordingSpellRepository : SpellRepository {
    lateinit var lastSearch: SpellSearch

    override fun search(criteria: SpellSearch): SpellSearchResult {
        lastSearch = criteria
        if (criteria.query == "unavailable") {
            throw DataAccessResourceFailureException("Test storage failure")
        }
        return SpellSearchResult(listOf(FIREBALL), 1_001, criteria.page, criteria.pageSize)
    }

    override fun suggest(query: String, limit: Int): List<Spell> =
        listOf(FIREBALL).filter {
            it.name.ru.contains(query, ignoreCase = true) ||
                it.name.en.contains(query, ignoreCase = true)
        }.take(limit)

    override fun findById(id: String): Spell? {
        if (id == "unavailable") {
            throw DataAccessResourceFailureException("Test storage failure")
        }
        return FIREBALL.takeIf { it.id == id }
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
