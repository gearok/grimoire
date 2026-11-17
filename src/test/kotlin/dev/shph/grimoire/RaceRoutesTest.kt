package dev.shph.grimoire

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class RaceRoutesTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private val slugs = PathMatchingResourcePatternResolver()
        .getResources("classpath:templates/races/pages/*.html")
        .map { it.filename!!.removeSuffix(".html") }

    @Test
    fun `race index links every race page with header switch`() {
        assertEquals(84, slugs.size)

        mockMvc.get("/races").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Расы</title>")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/races\" class=\"active\"")) }
            slugs.forEach { content { string(org.hamcrest.Matchers.containsString("href=\"/races/$it\"")) } }
        }
    }

    @Test
    fun `race index keeps the dnd su grouping without homebrew`() {
        mockMvc.get("/races").andExpect {
            status { isOk() }
            content {
                string(
                    org.hamcrest.Matchers.stringContainsInOrder(
                        "<h2>Расы / 50</h2>",
                        "<h2>Происхождения / 4</h2>",
                        "<h2>Расы Mordenkainen Presents: Monsters of the Multiverse / 30</h2>",
                    ),
                )
            }
            listOf("Homebrew", "Unearthed Arcana", "Plane Shift", "Midgard").forEach {
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(it))) }
            }
        }
    }

    @Test
    fun `races dnd su tags as homebrew are left out`() {
        listOf("glitchling-ua", "naga-psa", "people-of-innistrad-psi", "bearfolk-mhh", "elf-mhh").forEach { slug ->
            mockMvc.get("/races/$slug").andExpect { status { isNotFound() } }
        }
    }

    @Test
    fun `races from other books get their own pages and sources`() {
        mapOf(
            "aarakocra-mpmm" to "MPMM",
            "autognome" to "SAS",
            "hexblood" to "VRGR",
        ).forEach { (slug, source) ->
            mockMvc.get("/races/$slug").andExpect {
                status { isOk() }
                content { string(org.hamcrest.Matchers.containsString("ИСТОЧНИК=$source /")) }
            }
        }
    }

    @Test
    fun `every race page renders inside the layout`() {
        slugs.forEach { slug ->
            mockMvc.get("/races/$slug").andExpect {
                status { isOk() }
                content { string(org.hamcrest.Matchers.containsString("href=\"/races\" class=\"active\"")) }
                content { string(org.hamcrest.Matchers.containsString("← НАЗАД / ВСЕ РАСЫ")) }
                content { string(org.hamcrest.Matchers.containsString("ИСТОЧНИК=")) }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Homebrew"))) }
            }
        }
    }

    @Test
    fun `race page has traits and collapsed subraces including unearthed arcana`() {
        mockMvc.get("/races/elf").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Эльф · Гримуар</title>")) }
            content { string(org.hamcrest.Matchers.containsString("<h3 id=\"osobennosti_jelfov\">Особенности эльфов</h3>")) }
            content { string(org.hamcrest.Matchers.containsString("<strong><em>Увеличение характеристик</em>.</strong>")) }
            content { string(org.hamcrest.Matchers.containsString("<summary>Высший эльф</summary>")) }
            content { string(org.hamcrest.Matchers.containsString("<summary>Таблицы эльфов (MToF)</summary>")) }
            content { string(org.hamcrest.Matchers.containsString("Разновидности эльфов Unearthed Arcana")) }
            content { string(org.hamcrest.Matchers.containsString("<summary>Авариэль</summary>")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<details class=\"fold\" open"))) }
            content { string(org.hamcrest.Matchers.containsString("ИСТОЧНИК=PH14")) }
            content { string(org.hamcrest.Matchers.containsString("https://dnd.su/race/79-elf/")) }
        }
    }

    @Test
    fun `homebrew subraces are left out`() {
        mockMvc.get("/races/gnome").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<h3 id=\"lesnye_gnomy\">Лесные гномы</h3>")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Лоргалан"))) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Chronicles of Eberron"))) }
        }
    }

    @Test
    fun `unknown race slug returns 404 page`() {
        listOf("/races/zzz", "/races/index", "/races/79").forEach { url ->
            mockMvc.get(url).andExpect {
                status { isNotFound() }
                content { string(org.hamcrest.Matchers.containsString("Вернуться к расам")) }
            }
        }
    }
}
