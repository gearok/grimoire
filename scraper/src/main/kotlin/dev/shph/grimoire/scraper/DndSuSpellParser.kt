package dev.shph.grimoire.scraper

import dev.shph.grimoire.model.CastingTime
import dev.shph.grimoire.model.CastingTimeType
import dev.shph.grimoire.model.ClassAccess
import dev.shph.grimoire.model.LocalizedName
import dev.shph.grimoire.model.MagicSchool
import dev.shph.grimoire.model.SourceReference
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellComponents
import dev.shph.grimoire.model.SubclassAccess
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

class DndSuSpellParser {
    fun parse(html: String, requestedUrl: String): Spell {
        val document = Jsoup.parse(html, requestedUrl)
        val card = document.selectFirst("div.card[data-id^=spells:]")
            ?: error("spell card is missing at $requestedUrl")
        val title = card.selectFirst(".card-title [data-copy]")?.attr("data-copy")?.trim()
            ?: error("spell title is missing at $requestedUrl")
        val names = NAME.matchEntire(title)
            ?: error("expected a bilingual spell title, got '$title'")
        val id = card.attr("data-id").substringAfter(":")
        val canonicalUrl = document.selectFirst("meta[property=og:url]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: requestedUrl
        val slug = slugFrom(URI(requestedUrl).path)

        val body = card.selectFirst(".card__article-body") ?: error("spell body is missing")
        val directItems = body.children().filter { it.tagName() == "li" }
        val summary = directItems.firstOrNull { it.hasClass("size-type-alignment") }?.text()?.trim()
            ?: error("spell level and school are missing")
        val (level, school, ritual) = parseSummary(summary)
        val parameters = directItems
            .filterNot { it.hasClass("size-type-alignment") || it.hasClass("subsection") }
            .associate { parameter(it) }
        val duration = required(parameters, "Длительность")
        val descriptionElement = directItems.firstOrNull { it.hasClass("subsection") }
            ?.selectFirst("[itemprop=description]")
            ?: error("spell description is missing")
        val (description, higherLevels) = parseDescription(descriptionElement)
        val components = parseComponents(required(parameters, "Компоненты"))

        return Spell(
            id = id,
            slug = slug,
            name = LocalizedName(names.groupValues[1].trim(), names.groupValues[2].trim()),
            level = level,
            school = school,
            castingTime = parseCastingTime(required(parameters, "Время накладывания")),
            range = required(parameters, "Дистанция"),
            components = components,
            duration = duration,
            concentration = duration.lowercase().startsWith("концентрация"),
            ritual = ritual,
            classes = parseClasses(parameters["Классы"].orEmpty()),
            subclasses = parseSubclasses(parameters["Подклассы"].orEmpty()),
            description = description,
            higherLevels = higherLevels,
            damageTypes = detectDamageTypes("$description ${higherLevels.orEmpty()}"),
            sources = parseSources(card),
            sourceUrl = canonicalUrl,
        )
    }

    private fun parseSummary(value: String): Summary {
        val normalized = value.lowercase()
        val level = if (normalized.startsWith("заговор")) {
            0
        } else {
            LEVEL.find(normalized)?.groupValues?.get(1)?.toInt()
                ?: error("unknown spell level '$value'")
        }
        val school = MagicSchool.entries.firstOrNull {
            normalized.contains(it.russianName.lowercase())
        } ?: error("unknown magic school '$value'")
        return Summary(level, school, normalized.contains("ритуал"))
    }

    private fun parameter(element: Element): Pair<String, String> {
        val label = element.selectFirst("strong")?.text()?.trim()?.removeSuffix(":")
            ?: error("unlabelled spell parameter '${element.text()}'")
        return label to element.text().removePrefix(element.selectFirst("strong")!!.text()).trim()
    }

    private fun parseCastingTime(value: String): CastingTime {
        val normalized = value.lowercase()
        val type = when {
            "бонусн" in normalized && "действ" in normalized -> CastingTimeType.BONUS_ACTION
            "реакц" in normalized -> CastingTimeType.REACTION
            "действ" in normalized -> CastingTimeType.ACTION
            "минут" in normalized -> CastingTimeType.MINUTE
            "час" in normalized -> CastingTimeType.HOUR
            else -> CastingTimeType.OTHER
        }
        return CastingTime(
            text = value,
            type = type,
            reactionTrigger = if (type == CastingTimeType.REACTION) {
                value.substringAfter(",", "").trim().takeIf { it.isNotEmpty() }
            } else {
                null
            },
        )
    }

    private fun parseComponents(value: String): SpellComponents {
        val flags = value.substringBefore("(").uppercase()
        val material = MATERIAL.find(value)?.groupValues?.get(1)?.trim()
        val cost = material?.let { GOLD_COST.find(it)?.groupValues?.get(1)?.replace(" ", "")?.toIntOrNull() }
        return SpellComponents(
            verbal = COMPONENT_V.containsMatchIn(flags),
            somatic = COMPONENT_S.containsMatchIn(flags),
            material = COMPONENT_M.containsMatchIn(flags),
            materialDescription = material,
            materialCostGp = cost,
            materialConsumed = material?.lowercase()?.let {
                "расходу" in it || "поглоща" in it
            } ?: false,
        )
    }

    private fun parseClasses(value: String): List<ClassAccess> =
        value.split(",").mapNotNull { item ->
            val normalized = item.trim()
            if (normalized.isEmpty()) return@mapNotNull null
            val sourceCode = OPTIONAL_CODE.find(normalized)?.groupValues?.get(1)
            ClassAccess(
                name = normalized.replace(OPTIONAL_CODE, "").trim().lowercase(),
                optional = sourceCode != null,
                sourceCode = sourceCode,
            )
        }

    private fun parseSubclasses(value: String): List<SubclassAccess> =
        value.split(",").mapNotNull { item ->
            SUBCLASS.matchEntire(item.trim())?.let {
                SubclassAccess(
                    name = it.groupValues[1].trim().lowercase(),
                    parentClass = it.groupValues[2].trim().lowercase(),
                )
            }
        }

    private fun parseDescription(description: Element): Pair<String, String?> {
        val sections = description.children().map { it.text().trim() }.filter { it.isNotEmpty() }
        val higher = sections.firstOrNull { HIGHER_LEVELS.containsMatchIn(it) }
        val main = sections.filterNot { it == higher }.joinToString("\n\n")
        require(main.isNotBlank()) { "spell description is empty" }
        return main to higher?.replace(HIGHER_LEVELS, "")?.trim()
    }

    private fun parseSources(card: Element): List<SourceReference> {
        val sources = card.select(".card-title .source-plaque")
            .filterNot { it.hasAttr("href") && "next.dnd.su" in it.attr("href") }
            .mapNotNull {
                val code = it.text().trim()
                val title = it.attr("title").trim()
                if (code.isEmpty() || title.isEmpty()) null else SourceReference(code, title)
            }
        require(sources.isNotEmpty()) { "spell source is missing" }
        return sources.distinctBy { it.code }
    }

    private fun detectDamageTypes(text: String): List<String> {
        val normalized = text.lowercase()
        return DAMAGE_TYPES.filterValues { words -> words.any { it in normalized } }.keys.toList()
    }

    private fun slugFrom(path: String): String =
        path.trim('/').substringAfterLast('/').substringAfter("-")
            .lowercase()
            .replace("_", "-")
            .replace(Regex("[^a-z0-9-]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

    private fun required(parameters: Map<String, String>, name: String): String =
        parameters[name]?.takeIf { it.isNotBlank() } ?: error("spell parameter '$name' is missing")

    private data class Summary(val level: Int, val school: MagicSchool, val ritual: Boolean)

    private companion object {
        val NAME = Regex("""(.+?)\s*\[([^\]]+)]""")
        val LEVEL = Regex("""(\d)\s+уров""")
        val MATERIAL = Regex("""\((.*)\)""")
        val GOLD_COST = Regex("""(\d[\d ]*)\s*зм(?![\p{L}\p{N}_])""", RegexOption.IGNORE_CASE)
        val COMPONENT_V = Regex("""(?:^|[,\s])В(?:$|[,\s])""")
        val COMPONENT_S = Regex("""(?:^|[,\s])С(?:$|[,\s])""")
        val COMPONENT_M = Regex("""(?:^|[,\s])М(?:$|[,\s])""")
        val OPTIONAL_CODE = Regex("""([A-Z][A-Z0-9]{1,5})""")
        val SUBCLASS = Regex("""(.+?)\s*\((.+?)\)""")
        val HIGHER_LEVELS = Regex("""^На (?:больших|более высоких) уровнях\.\s*""", RegexOption.IGNORE_CASE)
        val DAMAGE_TYPES = linkedMapOf(
            "дробящий" to listOf("дробящ"),
            "колющий" to listOf("колющ"),
            "рубящий" to listOf("рубящ"),
            "звук" to listOf("урона звуком", "урон звуком"),
            "излучение" to listOf("урона излучением", "урон излучением"),
            "кислота" to listOf("урона кислотой", "урон кислотой"),
            "некротическая энергия" to listOf("некротическ"),
            "огонь" to listOf("урона огнём", "урон огнём"),
            "психическая энергия" to listOf("психическ"),
            "силовое поле" to listOf("урона силовым полем", "урон силовым полем"),
            "холод" to listOf("урона холодом", "урон холодом"),
            "электричество" to listOf("урона электричеством", "урон электричеством"),
            "яд" to listOf("урона ядом", "урон ядом"),
        )
    }
}
