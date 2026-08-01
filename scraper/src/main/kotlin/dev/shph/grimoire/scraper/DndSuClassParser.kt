package dev.shph.grimoire.scraper

import dev.shph.grimoire.model.CharacterClass
import dev.shph.grimoire.model.ClassProficiencies
import dev.shph.grimoire.model.LocalizedName
import dev.shph.grimoire.model.Rule
import dev.shph.grimoire.model.RuleSection

class DndSuClassParser {
    fun parse(html: String, requestedUrl: String): CharacterClass {
        val article = DndSuArticle(html, requestedUrl, "class")
        val sections = article.sections()
        // The mechanical fields live as labelled rules (Кость Хитов, Доспехи, Оружие, …)
        // under the "Хиты, владение и снаряжение" block, which dnd.su splits into ХИТЫ /
        // ВЛАДЕНИЕ / СНАРЯЖЕНИЕ sub-headings. Gather every such section's rules together.
        val basics = sections
            .filter { BASICS_SECTION.containsMatchIn(it.title) }
            .flatMap { it.entries }

        return CharacterClass(
            id = article.id,
            slug = article.slug,
            name = article.name,
            hitDie = basics.value(HIT_DIE_TRAIT)?.let { HIT_DIE.find(it)?.groupValues?.get(1)?.toIntOrNull() },
            primaryAbilities = emptyList(),
            savingThrows = splitValues(basics.value(SAVING_THROWS)),
            proficiencies = ClassProficiencies(
                armor = splitValues(basics.value(ARMOR)),
                weapons = splitValues(basics.value(WEAPONS)),
                tools = splitValues(basics.value(TOOLS)).filterNot { it == "нет" },
                skills = skillChoices(basics.value(SKILLS)),
            ),
            subclasses = parseSubclasses(sections),
            sections = sections.filterNot { it.title.equals("Описание", ignoreCase = true) },
            description = article.description(),
            sources = article.sources("class"),
            sourceUrl = article.sourceUrl,
        )
    }

    private fun List<Rule>.value(name: Regex): String? =
        firstOrNull { rule -> rule.name?.let(name::containsMatchIn) == true }?.text

    private fun parseSubclasses(sections: List<RuleSection>): List<LocalizedName> =
        sections.mapNotNull { section ->
            NAME.matchEntire(section.title.trim())?.let {
                LocalizedName(it.groupValues[1].trim(), it.groupValues[2].trim())
            }
        }.distinctBy { it.en.lowercase() }

    private fun skillChoices(value: String?): List<String> =
        value?.let { SKILL_CHOICE.find(it)?.groupValues?.get(1) ?: it }
            ?.let(::splitValues).orEmpty()

    private fun splitValues(value: String?): List<String> =
        value.orEmpty().split(",", ";").map { it.trim().trim('.', ':').trim().lowercase() }
            .filter(String::isNotEmpty)

    private companion object {
        val NAME = Regex("""(.+?)\s*\[([^\]]+)]""")
        val BASICS_SECTION = Regex("хиты|владение|снаряжение", RegexOption.IGNORE_CASE)
        val HIT_DIE = Regex("""[кdд]\s*(\d+)""", RegexOption.IGNORE_CASE)
        val HIT_DIE_TRAIT = Regex("кост[ьи] хитов", RegexOption.IGNORE_CASE)
        val ARMOR = Regex("доспех", RegexOption.IGNORE_CASE)
        val WEAPONS = Regex("оружие", RegexOption.IGNORE_CASE)
        val TOOLS = Regex("инструмент", RegexOption.IGNORE_CASE)
        val SAVING_THROWS = Regex("спасбро", RegexOption.IGNORE_CASE)
        val SKILLS = Regex("навык", RegexOption.IGNORE_CASE)
        val SKILL_CHOICE = Regex("""из следующих:\s*(.+)""", RegexOption.IGNORE_CASE)
    }
}
