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
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter
import org.springframework.data.elasticsearch.core.document.Document
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@SpringBootTest(properties = ["seed.enabled=false"])
@AutoConfigureMockMvc
class SpellRoutesTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var elasticsearchConverter: ElasticsearchConverter

    @TestConfiguration
    class RepositoryConfiguration {
        @Bean
        @Primary
        fun spellRepository(): SpellRepository = object : SpellRepository {
            override fun search(criteria: SpellSearch) =
                SpellSearchResult(listOf(FIREBALL), 1, criteria.page, criteria.pageSize)

            override fun findById(id: String): Spell? = FIREBALL.takeIf { it.id == id }

            override fun save(spell: Spell) = Unit
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
                content { string(org.hamcrest.Matchers.containsString("class=\"header-path\">[ ГРИМУАР / ЗАКЛИНАНИЯ ]")) }
                content { string(org.hamcrest.Matchers.containsString("data-theme-toggle")) }
                content { string(org.hamcrest.Matchers.containsString("/static/theme.js")) }
                content { string(org.hamcrest.Matchers.containsString("/static/multi-select.js")) }
            }
    }

    @Test
    fun `htmx request returns only the result fragment`() {
        val result = mockMvc.get("/spells") { header("HX-Request", "true") }
            .andExpect {
                status { isOk() }
                header { string(HttpHeaders.VARY, "HX-Request") }
                content { string(org.hamcrest.Matchers.containsString("spell-grid")) }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))) }
            }
            .andReturn()

        assertNotNull(result.response.contentAsString)
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
            content { string(org.hamcrest.Matchers.containsString("PH14")) }
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
    fun `spring data writes the existing lowercase elasticsearch document format`() {
        val document = Document.create()

        elasticsearchConverter.write(FIREBALL, document)

        assertEquals("evocation", document["school"])
        assertEquals("action", (document["castingTime"] as Map<*, *>)["type"])
        assertFalse(document.containsKey("_class"))
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
    description = "Яркая вспышка превращается во взрыв пламени.",
    higherLevels = "Урон увеличивается при использовании ячейки более высокого уровня.",
    damageTypes = listOf("огонь"),
    sources = listOf(SourceReference("PH14", "Player's Handbook")),
    sourceUrl = "https://dnd.su/spells/205-fireball/",
)
