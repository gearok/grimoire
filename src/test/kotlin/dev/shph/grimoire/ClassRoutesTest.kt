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
    fun `unknown class slug returns 404 page`() {
        listOf("/classes/zzz", "/classes/index", "/classes/87").forEach { url ->
            mockMvc.get(url).andExpect {
                status { isNotFound() }
                content { string(org.hamcrest.Matchers.containsString("Вернуться к классам")) }
            }
        }
    }
}
