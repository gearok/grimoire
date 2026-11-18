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
class ClassRoutesTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `class index links every class page with header switch`() {
        val slugs = PathMatchingResourcePatternResolver()
            .getResources("classpath:templates/classes/pages/*.html")
            .map { it.filename!!.removeSuffix(".html") }
        assertEquals(16, slugs.size)

        mockMvc.get("/classes").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Классы</title>")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/classes\" class=\"active\"")) }
            slugs.forEach { content { string(org.hamcrest.Matchers.containsString("href=\"/classes/$it\"")) } }
        }
    }

    @Test
    fun `every class page renders inside the layout`() {
        PathMatchingResourcePatternResolver()
            .getResources("classpath:templates/classes/pages/*.html")
            .forEach { page ->
                mockMvc.get("/classes/${page.filename!!.removeSuffix(".html")}").andExpect {
                    status { isOk() }
                    content { string(org.hamcrest.Matchers.containsString("href=\"/classes\" class=\"active\"")) }
                    content { string(org.hamcrest.Matchers.containsString("class=\"class-table\"")) }
                    content { string(org.hamcrest.Matchers.containsString("ИСТОЧНИК=")) }
                }
            }
    }

    @Test
    fun `class page has level table and collapsed subclasses`() {
        mockMvc.get("/classes/barbarian").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Варвар · Гримуар</title>")) }
            content { string(org.hamcrest.Matchers.containsString("back-link")) }
            content { string(org.hamcrest.Matchers.containsString("<th>Урон ярости</th>")) }
            content { string(org.hamcrest.Matchers.containsString("<h3 id=\"feature.rage\">ЯРОСТЬ</h3>")) }
            content { string(org.hamcrest.Matchers.containsString("<details class=\"fold\" id=\"primal.berserker\">")) }
            content { string(org.hamcrest.Matchers.containsString("<summary>Путь берсерка</summary>")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<details class=\"fold\" open"))) }
            content { string(org.hamcrest.Matchers.containsString("https://dnd.su/class/87-barbarian/")) }
        }
    }

    @Test
    fun `class page includes unearthed arcana but not homebrew subclasses`() {
        mockMvc.get("/classes/bard").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<h2 id=\"unofficial\">Unearthed Arcana &amp; Unofficial</h2>")) }
            content { string(org.hamcrest.Matchers.containsString("<details class=\"fold\" id=\"ua.college.satire\">")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Homebrew"))) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hb.college"))) }
        }
    }

    @Test
    fun `english class index links every english class page`() {
        val slugs = englishPages().map { it.filename!!.removeSuffix(".html") }
        assertEquals(13, slugs.size)

        mockMvc.get("/en/classes").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<html lang=\"en\">")) }
            content { string(org.hamcrest.Matchers.containsString("<title>Classes</title>")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/en/classes\" class=\"active\"")) }
            content { string(org.hamcrest.Matchers.containsString(">CLASSES</a>")) }
            content { string(org.hamcrest.Matchers.containsString("hreflang=\"ru\" href=\"http://localhost/classes\"")) }
            slugs.forEach { content { string(org.hamcrest.Matchers.containsString("href=\"/en/classes/$it\"")) } }
        }
    }

    @Test
    fun `every english class page renders inside the english layout`() {
        englishPages().forEach { page ->
            val slug = page.filename!!.removeSuffix(".html")
            mockMvc.get("/en/classes/$slug").andExpect {
                status { isOk() }
                content { string(org.hamcrest.Matchers.containsString("<html lang=\"en\">")) }
                content { string(org.hamcrest.Matchers.containsString("href=\"/en/classes\" class=\"active\"")) }
                content { string(org.hamcrest.Matchers.containsString("href=\"/en/classes\" class=\"back-link\"")) }
                content { string(org.hamcrest.Matchers.containsString("class=\"class-table\"")) }
                content { string(org.hamcrest.Matchers.containsString("SOURCE=")) }
                content { string(org.hamcrest.Matchers.containsString("hreflang=\"ru\" href=\"http://localhost/classes/$slug\"")) }
                content { string(org.hamcrest.Matchers.containsString("hreflang=\"en\" href=\"http://localhost/en/classes/$slug\"")) }
                content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ИСТОЧНИК="))) }
            }
        }
    }

    @Test
    fun `class pages link to their other language version`() {
        mockMvc.get("/classes/barbarian").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<html lang=\"ru\">")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/en/classes/barbarian\"")) }
        }
        mockMvc.get("/en/classes/barbarian").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("<title>Barbarian · Grimoire</title>")) }
            content { string(org.hamcrest.Matchers.containsString("<h3 id=\"feature.rage\">Rage</h3>")) }
            content { string(org.hamcrest.Matchers.containsString("<details class=\"fold\" id=\"primal.totem-warrior\">")) }
            content { string(org.hamcrest.Matchers.containsString("href=\"/classes/barbarian\"")) }
        }
    }

    @Test
    fun `class page without an english version disables the language link`() {
        mockMvc.get("/classes/warrior-sidekick").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("class=\"unavailable\"")) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/en/classes/warrior-sidekick"))) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hreflang=\"en\""))) }
        }
    }

    @Test
    fun `unknown english class slug returns english 404 page`() {
        listOf("/en/classes/zzz", "/en/classes/index", "/en/classes/warrior-sidekick").forEach { url ->
            mockMvc.get(url).andExpect {
                status { isNotFound() }
                content { string(org.hamcrest.Matchers.containsString("<html lang=\"en\">")) }
                content { string(org.hamcrest.Matchers.containsString("href=\"/en/classes\">Back to classes</a>")) }
            }
        }
    }

    @Test
    fun `unknown class slug returns 404 page`() {
        listOf("/classes/zzz", "/classes/index", "/classes/87").forEach { url ->
            mockMvc.get(url).andExpect {
                status { isNotFound() }
                content { string(org.hamcrest.Matchers.containsString("Вернуться к классам")) }
            }
        }
    }

    private fun englishPages() =
        PathMatchingResourcePatternResolver().getResources("classpath:templates/en/classes/pages/*.html").toList()
}
