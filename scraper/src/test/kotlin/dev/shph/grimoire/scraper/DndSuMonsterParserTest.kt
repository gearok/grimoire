package dev.shph.grimoire.scraper

import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import kotlin.test.Test
import kotlin.test.assertEquals

class DndSuMonsterParserTest {
    @Test
    fun `parses a bestiary stat block into the monster index model`() {
        val monster = DndSuMonsterParser().parse(GOBLIN, "https://dnd.su/bestiary/4-goblin/")

        assertEquals("4", monster.id)
        assertEquals("goblin", monster.slug)
        assertEquals("Гоблин", monster.name.ru)
        assertEquals(CreatureSize.SMALL, monster.size)
        assertEquals(CreatureType.HUMANOID, monster.type)
        assertEquals("Гоблиноид", monster.subtype)
        assertEquals(15, monster.armorClass.value)
        assertEquals(7, monster.hitPoints.average)
        assertEquals(14, monster.abilities.dexterity)
        assertEquals(0.25, monster.challenge.value)
        assertEquals(50, monster.challenge.experience)
        assertEquals("Ловкий побег", monster.sections.single().entries.single().name)
        assertEquals(listOf("MM14"), monster.sources.map { it.code })
    }

    private companion object {
        val GOBLIN = """
            <html><head><meta property="og:url" content="https://dnd.su/bestiary/4-goblin/"></head><body>
            <div class="card" data-id="bestiary:4">
              <h2 class="card-title"><span data-copy="Гоблин [Goblin]"></span>
                <span class="source-plaque" title="Monster Manual">MM14</span>
              </h2>
              <ul class="card__article-body">
                <li class="size-type-alignment">Маленький Гуманоид (Гоблиноид), нейтрально-злой</li>
                <li><strong>Класс Доспеха</strong> 15 (кожаный доспех, щит)</li>
                <li><strong>Хиты</strong> 7 (2к6)</li>
                <li><strong>Скорость</strong> 30 футов</li>
                <li class="abilities">
                  <div class="stat">Сил 8 (-1)</div><div class="stat">Лов 14 (+2)</div>
                  <div class="stat">Тел 10 (+0)</div><div class="stat">Инт 10 (+0)</div>
                  <div class="stat">Мдр 8 (-1)</div><div class="stat">Хар 8 (-1)</div>
                </li>
                <li><strong>Навыки</strong> Скрытность +6</li>
                <li><strong>Чувства</strong> тёмное зрение 60 футов</li>
                <li><strong>Языки</strong> Общий, Гоблинский</li>
                <li><strong>Опасность</strong> 1/4 (50 опыта)</li>
                <li><strong>Бонус мастерства +2</strong></li>
                <li><strong>Местность обитания</strong> лес, подземье</li>
                <li class="subsection desc"><div><p><strong>Ловкий побег.</strong> Гоблин может совершить Отход.</p></div></li>
              </ul>
            </div></body></html>
        """.trimIndent()
    }
}
