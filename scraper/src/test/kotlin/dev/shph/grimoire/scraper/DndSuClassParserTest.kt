package dev.shph.grimoire.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DndSuClassParserTest {
    private val parser = DndSuClassParser()

    @Test
    fun `parses a class article into the class index model`() {
        val characterClass = parser.parse(FIGHTER, "https://dnd.su/class/91-fighter/")

        assertEquals("91", characterClass.id)
        assertEquals("fighter", characterClass.slug)
        assertEquals("Воин", characterClass.name.ru)
        assertEquals("Fighter", characterClass.name.en)
        assertEquals(10, characterClass.hitDie)
        assertEquals(listOf("сила", "телосложение"), characterClass.savingThrows)
        assertTrue(characterClass.proficiencies.armor.contains("все доспехи"))
        assertTrue(characterClass.proficiencies.weapons.any { "воинское" in it })
        assertTrue(characterClass.proficiencies.tools.isEmpty())
        assertTrue(characterClass.proficiencies.skills.contains("акробатика"))
        assertEquals("Чемпион", characterClass.subclasses.single().ru)
        assertEquals(listOf("Player's Handbook"), characterClass.sources.map { it.title })
    }

    private companion object {
        val FIGHTER = """
            <html><head><meta property="og:url" content="https://dnd.su/class/91-fighter/"></head><body>
            <div class="card paper-1">
              <div class="card__header">
                <h2 class="card-title" itemprop="name"><a href="/class/91-fighter/">Воин [Fighter]</a></h2>
              </div>
              <ul class="params card__article-body">
                <li><strong>Источник:</strong> «<span>Player's Handbook</span>»</li>
              </ul>
              <div class="desc card__article-body" itemprop="articleBody">
                <p>Воин — мастер боевых искусств.</p>
                <h3>Хиты, владение и снаряжение</h3>
                <p><strong>Кость Хитов.</strong> 1к10 за каждый уровень воина.</p>
                <p><strong>Доспехи.</strong> Все доспехи, щиты.</p>
                <p><strong>Оружие.</strong> Простое оружие, воинское оружие.</p>
                <p><strong>Инструменты.</strong> Нет.</p>
                <p><strong>Спасброски.</strong> Сила, Телосложение.</p>
                <p><strong>Навыки.</strong> Выберите два навыка из следующих: Акробатика, Атлетика, Восприятие, Выживание, Запугивание.</p>
                <h2>Чемпион [Champion]</h2>
                <p>Архетип чемпиона делает упор на грубую силу.</p>
              </div>
              <div class="card__footer"></div>
            </div></body></html>
        """.trimIndent()
    }
}
