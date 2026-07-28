package dev.shph.grimoire

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.Json
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

class SpellRoutesTest {
    private val repository = InMemorySpellRepository(listOf(FIREBALL))

    @Test
    fun `spell index renders a searchable htmx page`() = testApplication {
        application { configureHttp(repository, Json { encodeDefaults = true }) }

        val response = client.get("/spells?q=fire")

        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertContains(html, "Огненный шар")
        assertContains(html, "hx-get=\"/spells\"")
        assertContains(html, "id=\"spell-results\"")
        assertContains(html, "data-theme-toggle")
        assertContains(html, "/static/theme.js")
    }

    @Test
    fun `htmx request returns only the result fragment`() = testApplication {
        application { configureHttp(repository) }

        val response = client.get("/spells") { header("HX-Request", "true") }

        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertContains(html, "spell-grid")
        assertFalse(html.contains("<html"))
    }

    @Test
    fun `unknown spell is a 404`() = testApplication {
        application { configureHttp(repository) }

        assertEquals(HttpStatusCode.NotFound, client.get("/spells/missing").status)
    }

    @Test
    fun `spell detail is rendered from its resource template`() = testApplication {
        application { configureHttp(repository) }

        val response = client.get("/spells/205")

        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertContains(html, "<title>Огненный шар · Гримуар</title>")
        assertContains(html, "На больших уровнях")
        assertContains(html, "PH14")
    }

    @Test
    fun `invalid search filters are a 400`() = testApplication {
        application { configureHttp(repository) }

        assertEquals(HttpStatusCode.BadRequest, client.get("/spells?level=third").status)
    }

    @Test
    fun `filter selects accept and retain multiple values`() = testApplication {
        application { configureHttp(repository) }

        val response = client.get(
            "/spells?level=1&level=3&school=evocation&school=enchantment&" +
                "class=%D0%B2%D0%BE%D0%BB%D1%88%D0%B5%D0%B1%D0%BD%D0%B8%D0%BA",
        )

        assertEquals(HttpStatusCode.OK, response.status)
        val html = response.bodyAsText()
        assertContains(html, """<select id="level" name="level" multiple""")
        assertContains(html, """value="1" selected""")
        assertContains(html, """value="3" selected""")
        assertContains(html, """value="evocation" selected""")
        assertContains(html, """value="enchantment" selected""")
        assertContains(html, """value="волшебник" selected""")
    }
}

private class InMemorySpellRepository(initial: List<Spell>) : SpellRepository {
    private val spells = initial.associateBy(Spell::id).toMutableMap()

    override suspend fun search(criteria: SpellSearch): SpellSearchResult {
        val matches = spells.values.filter {
            criteria.query == null ||
                it.name.ru.contains(criteria.query, ignoreCase = true) ||
                it.name.en.contains(criteria.query, ignoreCase = true) ||
                it.description.contains(criteria.query, ignoreCase = true)
        }
        return SpellSearchResult(matches, matches.size.toLong(), criteria.page, criteria.pageSize)
    }

    override suspend fun findById(id: String): Spell? = spells[id]

    override suspend fun save(spell: Spell) {
        spells[spell.id] = spell
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
