package dev.shph.grimoire.view

import dev.shph.grimoire.model.SearchResultMode
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellComponents
import dev.shph.grimoire.model.SpellFacets
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.model.SpellSearchResult

data class SpellIndexView(
    val searchForm: SearchFormView,
    val total: Long,
    val spells: List<SpellCardView>,
    val spellGroups: List<SpellLinkGroupView>,
    val resultMode: SearchResultMode,
    val hasResults: Boolean,
    val hasFilters: Boolean,
    val pagination: PaginationView?,
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
    val facts: List<FactView>,
    val description: String,
    val higherLevels: String?,
    val sourcesLabel: String,
    val sourceUrl: String,
)

fun SpellSearchResult.toIndexView(
    criteria: SpellSearch,
    resultMode: SearchResultMode = SearchResultMode.INDEX,
): SpellIndexView {
    val cards = if (resultMode == SearchResultMode.CARDS) spells.map(Spell::toCardView) else emptyList()
    val groups = if (resultMode == SearchResultMode.INDEX) spells.toLinkGroups() else emptyList()
    return SpellIndexView(
        searchForm = spellSearchForm(criteria),
        total = total,
        spells = cards,
        spellGroups = groups,
        resultMode = resultMode,
        hasResults = spells.isNotEmpty(),
        hasFilters = !criteria.isUnfiltered,
        pagination = pageSize?.let { size ->
            PaginationView.create(total, page, size) {
                criteria.urlForPage(it, resultMode)
            }
        },
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
    levelLabel = levelLabel(),
    schoolName = school.russianName,
    nameRu = name.ru,
    nameEn = name.en,
    facts = buildList {
        add(FactView("Школа", school.russianName))
        add(FactView("Время накладывания", castingTime.text))
        add(FactView("Дистанция", range))
        add(FactView("Компоненты", components.label()))
        add(FactView("Длительность", duration))
        add(
            FactView(
                "Классы",
                classes.joinToString { access ->
                    access.name + if (access.optional) " (опционально)" else ""
                },
            ),
        )
        if (subclasses.isNotEmpty()) {
            add(
                FactView(
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

fun spellSearchForm(criteria: SpellSearch = SpellSearch()) = SearchFormView(
    id = "spell-filters",
    action = "/spells",
    resultsTarget = "#spell-results",
    query = criteria.query.orEmpty(),
    suggestionsUrl = "/spells/suggestions",
    suggestionPrefix = "spell",
    suggestionsLabel = "Подсказки заклинаний",
    filters = listOf(
        SearchFilterView(
            id = "level",
            name = "level",
            label = "Уровень",
            emptyLabel = "Уровни",
            options = SpellFacets.levels.options.map { option ->
                SelectOption(option.value, option.label, option.item in criteria.levels)
            },
        ),
        SearchFilterView(
            id = "school",
            name = "school",
            label = "Школа",
            emptyLabel = "Школы",
            options = SpellFacets.schools.options.map { option ->
                SelectOption(option.value, option.label, option.item in criteria.schools)
            },
        ),
        SearchFilterView(
            id = "class",
            name = "class",
            label = "Класс",
            emptyLabel = "Классы",
            options = SpellFacets.characterClasses.options.map { option ->
                SelectOption(option.value, option.label, option.item in criteria.characterClasses)
            },
        ),
    ),
)

private fun SpellSearch.urlForPage(targetPage: Int, resultMode: SearchResultMode) = searchPageUrl(
    path = "/spells",
    parameters = buildList {
        query?.let { add("q" to it) }
        levels.sorted().forEach { add("level" to SpellFacets.levels.serialize(it)) }
        schools.sortedBy(SpellFacets.schools::serialize)
            .forEach { add("school" to SpellFacets.schools.serialize(it)) }
        characterClasses.sortedBy(SpellFacets.characterClasses::serialize)
            .forEach { add("class" to SpellFacets.characterClasses.serialize(it)) }
        add("view" to resultMode.queryValue)
    },
    targetPage = targetPage,
)
