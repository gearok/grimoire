package dev.shph.grimoire.scraper

import dev.shph.grimoire.model.CastingTimeType
import dev.shph.grimoire.model.MagicSchool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DndSuSpellParserTest {
    private val parser = DndSuSpellParser()

    @Test
    fun `parses a representative spell card into the application model`() {
        val spell = parser.parse(FIREBALL, "https://dnd.su/spells/205-fireball/")

        assertEquals("205", spell.id)
        assertEquals("fireball", spell.slug)
        assertEquals("Огненный шар", spell.name.ru)
        assertEquals("Fireball", spell.name.en)
        assertEquals(3, spell.level)
        assertEquals(MagicSchool.EVOCATION, spell.school)
        assertEquals(CastingTimeType.ACTION, spell.castingTime.type)
        assertEquals("крошечный шарик стоимостью 50 зм, расходуемый заклинанием", spell.components.materialDescription)
        assertEquals(50, spell.components.materialCostGp)
        assertTrue(spell.components.materialConsumed)
        assertFalse(spell.concentration)
        assertEquals(listOf("огонь"), spell.damageTypes)
        assertEquals("При использовании ячейки выше третьего урон увеличивается.", spell.higherLevels)
        assertEquals(listOf("PH14"), spell.sources.map { it.code })
    }

    @Test
    fun `parses concentration optional classes subclasses and rituals`() {
        val spell = parser.parse(AURA, "https://dnd.su/spells/3-aura_of_vitality/")

        assertTrue(spell.concentration)
        assertTrue(spell.ritual)
        assertEquals(0, spell.level)
        assertEquals("aura-of-vitality", spell.slug)
        assertEquals(true, spell.classes.first { it.name == "друид" }.optional)
        assertEquals("TCE", spell.classes.first { it.name == "друид" }.sourceCode)
        assertEquals("жрец", spell.subclasses.single().parentClass)
    }

    @Test
    fun `keeps sitemap word separators in the slug`() {
        val spell = parser.parse(
            FIREBALL.replace("spells:205", "spells:2"),
            "https://dnd.su/spells/2-antipathy_sympathy/",
        )

        assertEquals("antipathy-sympathy", spell.slug)
    }

    private companion object {
        val FIREBALL = """
            <html><head><meta property="og:url" content="https://dnd.su/spells/205-fireball/"></head><body>
            <div class="card" data-id="spells:205" data-cardlink="/spells/205-fireball/">
              <h2 class="card-title"><span data-copy="Огненный шар [Fireball]"></span>
                <span class="source-plaque" title="Player's Handbook">PH14</span>
                <a class="source-plaque" href="https://next.dnd.su/spells/1" title="Player's Handbook 2024">PH24</a>
              </h2>
              <ul class="card__article-body">
                <li class="size-type-alignment">3 уровень, воплощение</li>
                <li><strong>Время накладывания:</strong> 1 действие</li>
                <li><strong>Дистанция:</strong> 150 футов</li>
                <li><strong>Компоненты:</strong> В, С, М (крошечный шарик стоимостью 50 зм, расходуемый заклинанием)</li>
                <li><strong>Длительность:</strong> Мгновенная</li>
                <li><strong>Классы:</strong> волшебник, чародей</li>
                <li class="subsection"><div itemprop="description">
                  <p>Цель получает 8к6 урона огнём.</p>
                  <p><strong><em>На больших уровнях.</em></strong> При использовании ячейки выше третьего урон увеличивается.</p>
                </div></li>
              </ul>
            </div></body></html>
        """.trimIndent()

        val AURA = """
            <html><body>
            <div class="card" data-id="spells:3" data-cardlink="/spells/3-aura_of_vitality/">
              <h2 class="card-title"><span data-copy="Аура живучести [Aura of vitality]"></span>
                <span class="source-plaque" title="Player's Handbook">PH14</span>
              </h2>
              <ul class="card__article-body">
                <li class="size-type-alignment">Заговор, воплощение (ритуал)</li>
                <li><strong>Время накладывания:</strong> 1 реакция, когда союзник падает</li>
                <li><strong>Дистанция:</strong> На себя</li>
                <li><strong>Компоненты:</strong> В</li>
                <li><strong>Длительность:</strong> Концентрация, вплоть до 1 минуты</li>
                <li><strong>Классы:</strong> друид<sup>TCE</sup>, паладин</li>
                <li><strong>Подклассы:</strong> домен сумерек (жрец)</li>
                <li class="subsection"><div itemprop="description"><p>От вас исходит аура.</p></div></li>
              </ul>
            </div></body></html>
        """.trimIndent()
    }
}
