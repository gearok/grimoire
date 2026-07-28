package dev.shph.grimoire.view

import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellComponents
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult
import io.ktor.http.encodeURLParameter

data class SpellIndexView(
    val query: String,
    val levels: List<SelectOption>,
    val schools: List<SelectOption>,
    val characterClasses: List<SelectOption>,
    val total: Long,
    val spells: List<SpellCardView>,
    val hasFilters: Boolean,
    val pageLabel: String?,
    val previousUrl: String?,
    val nextUrl: String?,
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

fun SpellSearchResult.toIndexView(criteria: SpellSearch): SpellIndexView {
    val totalPages = ((total + pageSize - 1) / pageSize).toInt()
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
        spells = spells.map(Spell::toCardView),
        hasFilters = criteria.query != null ||
            criteria.levels.isNotEmpty() ||
            criteria.schools.isNotEmpty() ||
            criteria.characterClasses.isNotEmpty(),
        pageLabel = if (totalPages > 1) "Страница $page из $totalPages" else null,
        previousUrl = if (page > 1) criteria.urlForPage(page - 1) else null,
        nextUrl = if (page < totalPages) criteria.urlForPage(page + 1) else null,
    )
}

fun Spell.toDetailView() = SpellDetailView(
    pageTitle = "${name.ru} · Гримуар",
    levelLabel = levelLabel(),
    schoolName = school.russianName,
    nameRu = name.ru,
    nameEn = name.en,
    facts = buildList {
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

private fun SpellSearch.urlForPage(targetPage: Int): String {
    val params = buildList {
        query?.let { add("q=${it.encodeURLParameter()}") }
        levels.sorted().forEach { add("level=$it") }
        schools.sortedBy { it.slug }.forEach { add("school=${it.slug}") }
        characterClasses.sorted().forEach { add("class=${it.encodeURLParameter()}") }
        add("page=$targetPage")
    }
    return "/spells?${params.joinToString("&")}"
}

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
