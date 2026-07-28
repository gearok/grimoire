package dev.shph.grimoire.scraper

import dev.shph.grimoire.model.AbilityScores
import dev.shph.grimoire.model.ArmorClass
import dev.shph.grimoire.model.ChallengeRating
import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import dev.shph.grimoire.model.HitPoints
import dev.shph.grimoire.model.LocalizedName
import dev.shph.grimoire.model.Monster
import dev.shph.grimoire.model.MonsterRule
import dev.shph.grimoire.model.MonsterSection
import dev.shph.grimoire.model.MonsterSpeed
import dev.shph.grimoire.model.NamedModifier
import dev.shph.grimoire.model.SourceReference
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

class DndSuMonsterParser {
    fun parse(html: String, requestedUrl: String): Monster {
        val document = Jsoup.parse(html, requestedUrl)
        val card = document.selectFirst("div.card[data-id^=bestiary:]")
            ?: error("monster card is missing at $requestedUrl")
        val title = card.selectFirst(".card-title [data-copy]")?.attr("data-copy")?.trim()
            ?: error("monster title is missing at $requestedUrl")
        val names = NAME.matchEntire(title)
            ?: error("expected a bilingual monster title, got '$title'")
        val id = card.attr("data-id").substringAfter(":")
        val body = card.selectFirst(".card__article-body") ?: error("monster body is missing")
        val directItems = body.children().filter { it.tagName() == "li" }
        val summaryText = directItems.firstOrNull { it.hasClass("size-type-alignment") }?.text()?.trim()
            ?: error("monster size, type and alignment are missing")
        val summary = parseSummary(summaryText)
        val parameters = directItems
            .filterNot { it.hasClass("size-type-alignment") || it.hasClass("subsection") || it.hasClass("abilities") }
            .mapNotNull(::parameter)
            .toMap()
        val sections = parseSections(directItems.filter { it.hasClass("subsection") })
        val description = sections.firstOrNull { it.title.equals("Описание", ignoreCase = true) }
            ?.entries?.joinToString("\n\n") { it.text }
        val ruleSections = sections.filterNot { it.title.equals("Описание", ignoreCase = true) }
        val canonicalUrl = document.selectFirst("meta[property=og:url]")?.attr("content")
            ?.takeIf(String::isNotBlank) ?: requestedUrl

        return Monster(
            id = id,
            slug = slugFrom(URI(requestedUrl).path),
            name = LocalizedName(names.groupValues[1].trim(), names.groupValues[2].trim()),
            size = summary.size,
            type = summary.type,
            subtype = summary.subtype,
            alignment = summary.alignment,
            armorClass = parseArmorClass(required(parameters, "Класс Доспеха")),
            hitPoints = parseHitPoints(required(parameters, "Хиты")),
            speeds = parseSpeeds(required(parameters, "Скорость")),
            abilities = parseAbilities(directItems.firstOrNull { it.hasClass("abilities") }),
            savingThrows = parseModifiers(parameters["Спасброски"]),
            skills = parseModifiers(parameters["Навыки"]),
            damageVulnerabilities = splitValues(parameters["Уязвимость к урону"]),
            damageResistances = splitValues(parameters["Сопротивление урону"]),
            damageImmunities = splitValues(parameters["Иммунитет к урону"]),
            conditionImmunities = splitValues(parameters["Иммунитет к состояниям"]),
            senses = parameters["Чувства"],
            languages = splitValues(parameters["Языки"]),
            challenge = parseChallenge(required(parameters, "Опасность")),
            proficiencyBonus = PROFICIENCY.find(parameters["Бонус мастерства"].orEmpty())
                ?.groupValues?.get(1)?.toIntOrNull(),
            environments = splitValues(parameters["Местность обитания"]),
            namedNpc = card.hasAttr("data-npc") || card.classNames().any { "npc" in it.lowercase() },
            sections = ruleSections,
            description = description,
            sources = parseSources(card),
            sourceUrl = canonicalUrl,
        )
    }

    private fun parseSummary(value: String): Summary {
        val clean = value.replace("?", "").trim()
        val size = CreatureSize.fromRussian(clean) ?: error("unknown creature size '$value'")
        val afterSize = clean.substringAfter(" ", "").trim()
        val typeText = afterSize.substringBefore(",").trim()
        val type = CreatureType.fromRussian(typeText) ?: error("unknown creature type '$value'")
        val subtype = SUBTYPE.find(typeText)?.groupValues?.get(1)?.trim()
        val alignment = afterSize.substringAfter(",", "без мировоззрения").trim()
        return Summary(size, type, subtype, alignment)
    }

    private fun parameter(element: Element): Pair<String, String>? {
        val strong = element.children().firstOrNull { it.tagName() == "strong" } ?: return null
        val label = strong.text().trim().removeSuffix(":")
        if (label.startsWith("Бонус мастерства")) {
            return "Бонус мастерства" to label.removePrefix("Бонус мастерства").trim()
        }
        return label to element.text().removePrefix(strong.text()).trim()
    }

    private fun parseArmorClass(value: String): ArmorClass {
        val match = LEADING_NUMBER.find(value) ?: error("invalid armor class '$value'")
        return ArmorClass(
            value = match.value.toInt(),
            description = PARENTHESES.find(value)?.groupValues?.get(1)?.trim(),
        )
    }

    private fun parseHitPoints(value: String): HitPoints {
        val average = LEADING_NUMBER.find(value)?.value?.toInt()
            ?: error("invalid hit points '$value'")
        return HitPoints(average, PARENTHESES.find(value)?.groupValues?.get(1)?.replace(" ", ""))
    }

    private fun parseSpeeds(value: String): List<MonsterSpeed> =
        value.split(",").mapNotNull { item ->
            val distance = FEET.find(item)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val normalized = item.lowercase()
            val type = when {
                "лет" in normalized -> "полёт"
                "плав" in normalized -> "плавание"
                "лаз" in normalized -> "лазание"
                "коп" in normalized -> "копание"
                else -> "ходьба"
            }
            MonsterSpeed(type, distance, "пар" in normalized)
        }.ifEmpty { error("invalid monster speed '$value'") }

    private fun parseAbilities(element: Element?): AbilityScores {
        val scores = element?.select(".stat")?.mapNotNull {
            ABILITY_SCORE.find(it.text())?.groupValues?.get(1)?.toIntOrNull()
        }.orEmpty()
        require(scores.size == 6) { "expected six monster ability scores" }
        return AbilityScores(scores[0], scores[1], scores[2], scores[3], scores[4], scores[5])
    }

    private fun parseModifiers(value: String?): List<NamedModifier> =
        value.orEmpty().split(",").mapNotNull { item ->
            MODIFIER.matchEntire(item.trim())?.let {
                NamedModifier(it.groupValues[1].trim().lowercase(), it.groupValues[2].toInt())
            }
        }

    private fun parseChallenge(value: String): ChallengeRating {
        val label = CHALLENGE.find(value)?.groupValues?.get(1) ?: error("invalid challenge '$value'")
        val numeric = when {
            "/" in label -> label.substringBefore("/").toDouble() / label.substringAfter("/").toDouble()
            else -> label.toDouble()
        }
        val experience = EXPERIENCE.find(value)?.groupValues?.get(1)
            ?.replace(Regex("""[^\d]"""), "")?.toIntOrNull()
        return ChallengeRating(numeric, label, experience)
    }

    private fun parseSections(elements: List<Element>): List<MonsterSection> {
        val sections = mutableListOf<MonsterSection>()
        elements.forEach { element ->
            val title = element.children().firstOrNull { it.hasClass("subsection-title") }?.text()?.trim()
                ?: "Особенности"
            val container = element.children().firstOrNull { it.tagName() == "div" } ?: element
            val rules = container.children().filter { it.tagName() == "p" }.mapNotNull { paragraph ->
                val text = paragraph.text().trim()
                if (text.isEmpty()) return@mapNotNull null
                val name = paragraph.selectFirst("strong")?.text()?.trim()?.removeSuffix(".")
                MonsterRule(
                    name = name,
                    text = name?.let { text.removePrefix("$it.").trim() } ?: text,
                )
            }.ifEmpty {
                container.text().trim().takeIf(String::isNotEmpty)?.let { listOf(MonsterRule(text = it)) }
                    .orEmpty()
            }
            if (rules.isEmpty()) return@forEach
            val existing = sections.indexOfFirst { it.title == title }
            if (existing >= 0) {
                sections[existing] = sections[existing].copy(entries = sections[existing].entries + rules)
            } else {
                sections += MonsterSection(title, rules)
            }
        }
        return sections
    }

    private fun parseSources(card: Element): List<SourceReference> {
        val sources = card.select(".card-title .source-plaque")
            .filterNot { it.hasAttr("href") && "next.dnd.su" in it.attr("href") }
            .mapNotNull {
                val code = it.text().trim()
                val title = it.attr("title").trim()
                if (code.isEmpty() || title.isEmpty()) null else SourceReference(code, title)
            }
        require(sources.isNotEmpty()) { "monster source is missing" }
        return sources.distinctBy { it.code }
    }

    private fun splitValues(value: String?): List<String> =
        value.orEmpty().split(",", ";").map { it.trim().lowercase() }.filter(String::isNotEmpty)

    private fun slugFrom(path: String): String =
        path.trim('/').substringAfterLast('/').substringAfter("-")
            .lowercase().replace("_", "-").replace(Regex("[^a-z0-9-]+"), "-")
            .replace(Regex("-+"), "-").trim('-')

    private fun required(parameters: Map<String, String>, name: String): String =
        parameters[name]?.takeIf(String::isNotBlank) ?: error("monster parameter '$name' is missing")

    private data class Summary(
        val size: CreatureSize,
        val type: CreatureType,
        val subtype: String?,
        val alignment: String,
    )

    private companion object {
        val NAME = Regex("""(.+?)\s*\[([^\]]+)]""")
        val SUBTYPE = Regex("""\(([^)]+)\)""")
        val LEADING_NUMBER = Regex("""^\s*(\d+)""")
        val PARENTHESES = Regex("""\(([^)]+)\)""")
        val FEET = Regex("""(\d+)\s*фут""", RegexOption.IGNORE_CASE)
        val ABILITY_SCORE = Regex("""(\d+)\s*\(""")
        val MODIFIER = Regex("""(.+?)\s+([+-]\d+)""")
        val CHALLENGE = Regex("""(\d+(?:/\d+)?)""")
        val EXPERIENCE = Regex("""\(([\d\s.,]+)\s*опыт""", RegexOption.IGNORE_CASE)
        val PROFICIENCY = Regex("""([+-]?\d+)""")
    }
}
