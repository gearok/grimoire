package dev.shph.grimoire.view

import dev.shph.grimoire.model.SearchResultMode
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellComponents
import dev.shph.grimoire.model.SpellFacets
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
    val resultMode: SearchResultMode,
    val hasResults: Boolean,
    val hasFilters: Boolean,
    val pageLabel: String?,
    val previousUrl: String?,
    val nextUrl: String?,
) {
    val cardsView: Boolean get() = resultMode == SearchResultMode.CARDS
}

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
    resultMode: SearchResultMode = SearchResultMode.INDEX,
): SpellIndexView {
    val totalPages = ((total + pageSize - 1) / pageSize).toInt()
    val cards = if (resultMode == SearchResultMode.CARDS) spells.map(Spell::toCardView) else emptyList()
    val groups = if (resultMode == SearchResultMode.INDEX) spells.toLinkGroups() else emptyList()
    return SpellIndexView(
        query = criteria.query.orEmpty(),
        levels = SpellFacets.levels.options.map { option ->
            SelectOption(
                value = option.value,
                label = option.label,
                selected = option.item in criteria.levels,
            )
        },
        schools = SpellFacets.schools.options.map { option ->
            SelectOption(option.value, option.label, option.item in criteria.schools)
        },
        characterClasses = SpellFacets.characterClasses.options.map { option ->
            SelectOption(
                option.value,
                option.label,
                option.item in criteria.characterClasses,
            )
        },
        total = total,
        spells = cards,
        spellGroups = groups,
        resultMode = resultMode,
        hasResults = spells.isNotEmpty(),
        hasFilters = criteria.query != null ||
            criteria.levels.isNotEmpty() ||
            criteria.schools.isNotEmpty() ||
            criteria.characterClasses.isNotEmpty(),
        pageLabel = if (totalPages > 1) "Страница $page из $totalPages" else null,
        previousUrl = if (page > 1) criteria.urlForPage(page - 1, resultMode) else null,
        nextUrl = if (page < totalPages) criteria.urlForPage(page + 1, resultMode) else null,
    )
}

private fun List<Spell>.toLinkGroups(): List<SpellLinkGroupView> =
    sortedBy { it.name.ru.lowercase() }
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
        }

fun Spell.toDetailView() = SpellDetailView(
    pageTitle = "${name.ru} · Гримуар",
    query = "",
    levels = SpellFacets.levels.options.map { option ->
        SelectOption(
            value = option.value,
            label = option.label,
            selected = false,
        )
    },
    schools = SpellFacets.schools.options.map { option ->
        SelectOption(option.value, option.label, selected = false)
    },
    characterClasses = SpellFacets.characterClasses.options.map { option ->
        SelectOption(
            option.value,
            option.label,
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
    description = description,
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

private fun SpellSearch.urlForPage(targetPage: Int, resultMode: SearchResultMode): String {
    val params = buildList {
        query?.let { add("q=${it.urlEncode()}") }
        levels.sorted().forEach { add("level=${SpellFacets.levels.serialize(it)}") }
        schools.sortedBy(SpellFacets.schools::serialize)
            .forEach { add("school=${SpellFacets.schools.serialize(it)}") }
        characterClasses.sortedBy(SpellFacets.characterClasses::serialize)
            .forEach { add("class=${SpellFacets.characterClasses.serialize(it).urlEncode()}") }
        add("view=${resultMode.queryValue}")
        add("page=$targetPage")
    }
    return "/spells?${params.joinToString("&")}"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
