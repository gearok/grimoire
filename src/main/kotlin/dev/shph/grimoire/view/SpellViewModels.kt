package dev.shph.grimoire.view

import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellComponents
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SpellIndexView(
    val query: String,
    val levels: List<SelectOption>,
    val schools: List<SelectOption>,
    val characterClasses: List<SelectOption>,
    val total: Long,
    val spells: List<SpellCardView>,
    val spellGroups: List<SpellLinkGroupView>,
    val cardsView: Boolean,
    val hasFilters: Boolean,
    val pageLabel: String?,
    val previousUrl: String?,
    val nextUrl: String?,
)

data class SpellLinkGroupView(
    val initial: String,
    val spells: List<SpellLinkView>,
)

data class SpellLinkView(
    val id: String,
    val level: Int,
    val nameRu: String,
    val schoolName: String,
)

data class SelectOption(
    val value: String,
    val label: String,
    val selected: Boolean,
)

data class SpellCardView(
    val id: String,
    val levelLabel: String,
    val schoolName: String,
    val nameRu: String,
    val nameEn: String,
    val description: String,
    val castingTime: String,
    val concentration: Boolean,
    val ritual: Boolean,
)

data class SpellDetailView(
    val pageTitle: String,
    val query: String,
    val levels: List<SelectOption>,
    val schools: List<SelectOption>,
    val characterClasses: List<SelectOption>,
    val levelLabel: String,
    val schoolName: String,
    val nameRu: String,
    val nameEn: String,
    val facts: List<SpellFactView>,
    val description: String,
    val higherLevels: String?,
    val sourcesLabel: String,
    val sourceUrl: String,
)

data class SpellFactView(
    val label: String,
    val value: String,
)

fun SpellSearchResult.toIndexView(
    criteria: SpellSearch,
    cardsView: Boolean = false,
): SpellIndexView {
    val totalPages = ((total + pageSize - 1) / pageSize).toInt()
    val cards = spells.map(Spell::toCardView)
    return SpellIndexView(
        query = criteria.query.orEmpty(),
        levels = (0..9).map { level ->
            SelectOption(
                value = level.toString(),
                label = if (level == 0) "Заговор" else level.toString(),
                selected = level in criteria.levels,
            )
        },
        schools = dev.shph.grimoire.model.MagicSchool.entries.map { school ->
            SelectOption(school.slug, school.russianName, school in criteria.schools)
        },
        characterClasses = CHARACTER_CLASSES.map { characterClass ->
            SelectOption(
                characterClass,
                characterClass.replaceFirstChar { it.uppercase() },
                characterClass in criteria.characterClasses,
            )
        },
        total = total,
        spells = cards,
        spellGroups = spells
            .sortedBy { it.name.ru.lowercase() }
            .groupBy { it.name.ru.trim().first().uppercaseChar().toString() }
            .map { (initial, groupedSpells) ->
                SpellLinkGroupView(
                    initial = initial,
                    spells = groupedSpells.map {
                        SpellLinkView(
                            id = it.id,
                            level = it.level,
                            nameRu = it.name.ru,
                            schoolName = it.school.russianName,
                        )
                    },
                )
            },
        cardsView = cardsView,
        hasFilters = criteria.query != null ||
            criteria.levels.isNotEmpty() ||
            criteria.schools.isNotEmpty() ||
            criteria.characterClasses.isNotEmpty(),
        pageLabel = if (totalPages > 1) "Страница $page из $totalPages" else null,
        previousUrl = if (page > 1) criteria.urlForPage(page - 1, cardsView) else null,
        nextUrl = if (page < totalPages) criteria.urlForPage(page + 1, cardsView) else null,
    )
}

fun Spell.toDetailView() = SpellDetailView(
    pageTitle = "${name.ru} · Гримуар",
    query = "",
    levels = (0..9).map { level ->
        SelectOption(
            value = level.toString(),
            label = if (level == 0) "Заговор" else level.toString(),
            selected = false,
        )
    },
    schools = dev.shph.grimoire.model.MagicSchool.entries.map { school ->
        SelectOption(school.slug, school.russianName, selected = false)
    },
    characterClasses = CHARACTER_CLASSES.map { characterClass ->
        SelectOption(
            characterClass,
            characterClass.replaceFirstChar { it.uppercase() },
            selected = false,
        )
    },
    levelLabel = levelLabel(),
    schoolName = school.russianName,
    nameRu = name.ru,
    nameEn = name.en,
    facts = buildList {
        add(SpellFactView("Школа", school.russianName))
        add(SpellFactView("Время накладывания", castingTime.text))
        add(SpellFactView("Дистанция", range))
        add(SpellFactView("Компоненты", components.label()))
        add(SpellFactView("Длительность", duration))
        add(
            SpellFactView(
                "Классы",
                classes.joinToString { access ->
                    access.name + if (access.optional) " (опционально)" else ""
                },
            ),
        )
        if (subclasses.isNotEmpty()) {
            add(
                SpellFactView(
                    "Подклассы",
                    subclasses.joinToString { "${it.name} (${it.parentClass})" },
                ),
            )
        }
    },
    description = description,
    higherLevels = higherLevels,
    sourcesLabel = sources.joinToString { source ->
        source.code + (source.page?.let { ", стр. $it" } ?: "")
    },
    sourceUrl = sourceUrl,
)

private fun Spell.toCardView() = SpellCardView(
    id = id,
    levelLabel = levelLabel(),
    schoolName = school.russianName,
    nameRu = name.ru,
    nameEn = name.en,
    description = description.take(180).trimEnd() + if (description.length > 180) "…" else "",
    castingTime = castingTime.text,
    concentration = concentration,
    ritual = ritual,
)

private fun Spell.levelLabel() = if (level == 0) "Заговор" else "$level уровень"

private fun SpellComponents.label(): String {
    val labels = buildList {
        if (verbal) add("В")
        if (somatic) add("С")
        if (material) add("М")
    }.joinToString(", ")
    return materialDescription?.let { "$labels ($it)" } ?: labels
}

private fun SpellSearch.urlForPage(targetPage: Int, cardsView: Boolean): String {
    val params = buildList {
        query?.let { add("q=${it.urlEncode()}") }
        levels.sorted().forEach { add("level=$it") }
        schools.sortedBy { it.slug }.forEach { add("school=${it.slug}") }
        characterClasses.sorted().forEach { add("class=${it.urlEncode()}") }
        if (cardsView) add("view=cards")
        add("page=$targetPage")
    }
    return "/spells?${params.joinToString("&")}"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

private val CHARACTER_CLASSES = listOf(
    "бард",
    "волшебник",
    "друид",
    "жрец",
    "изобретатель",
    "колдун",
    "паладин",
    "следопыт",
    "чародей",
)
