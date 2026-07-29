package dev.shph.grimoire.view

import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import dev.shph.grimoire.model.Monster
import dev.shph.grimoire.model.MonsterFacets
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.model.MonsterSearchResult
import dev.shph.grimoire.model.SearchResultMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class MonsterIndexView(
    val query: String,
    val sizes: List<SelectOption>,
    val types: List<SelectOption>,
    val challenges: List<SelectOption>,
    val total: Long,
    val monsters: List<MonsterCardView>,
    val monsterGroups: List<MonsterLinkGroupView>,
    val resultMode: SearchResultMode,
    val hasResults: Boolean,
    val hasFilters: Boolean,
    val pageLabel: String?,
    val previousUrl: String?,
    val nextUrl: String?,
) {
    val cardsView: Boolean get() = resultMode == SearchResultMode.CARDS
}

data class MonsterLinkGroupView(val initial: String, val monsters: List<MonsterLinkView>)

data class MonsterLinkView(
    val id: String,
    val challenge: String,
    val nameRu: String,
    val typeName: String,
)

data class MonsterCardView(
    val id: String,
    val challengeLabel: String,
    val typeName: String,
    val nameRu: String,
    val nameEn: String,
    val summary: String,
    val description: String?,
    val armorClass: Int,
    val hitPoints: Int,
)

data class MonsterDetailView(
    val pageTitle: String,
    val query: String,
    val sizes: List<SelectOption>,
    val types: List<SelectOption>,
    val challenges: List<SelectOption>,
    val challengeLabel: String,
    val nameRu: String,
    val nameEn: String,
    val combatStats: List<MonsterStatView>,
    val abilityStats: List<MonsterStatView>,
    val facts: List<SpellFactView>,
    val sections: List<MonsterSectionView>,
    val description: String?,
    val sourcesLabel: String,
    val sourceUrl: String,
)

data class MonsterSectionView(val title: String, val entries: List<MonsterRuleView>)
data class MonsterRuleView(val name: String?, val text: String)
data class MonsterStatView(val label: String, val value: String)

fun MonsterSearchResult.toIndexView(
    criteria: MonsterSearch,
    resultMode: SearchResultMode = SearchResultMode.INDEX,
): MonsterIndexView {
    val totalPages = ((total + pageSize - 1) / pageSize).toInt()
    val cards = if (resultMode == SearchResultMode.CARDS) monsters.map(Monster::toCardView) else emptyList()
    val groups = if (resultMode == SearchResultMode.INDEX) monsters.toLinkGroups() else emptyList()
    return MonsterIndexView(
        query = criteria.query.orEmpty(),
        sizes = sizeOptions(criteria.sizes),
        types = typeOptions(criteria.types),
        challenges = challengeOptions(criteria.challenges),
        total = total,
        monsters = cards,
        monsterGroups = groups,
        resultMode = resultMode,
        hasResults = monsters.isNotEmpty(),
        hasFilters = criteria.query != null ||
            criteria.sizes.isNotEmpty() ||
            criteria.types.isNotEmpty() ||
            criteria.challenges.isNotEmpty(),
        pageLabel = if (totalPages > 1) "Страница $page из $totalPages" else null,
        previousUrl = if (page > 1) criteria.urlForPage(page - 1, resultMode) else null,
        nextUrl = if (page < totalPages) criteria.urlForPage(page + 1, resultMode) else null,
    )
}

private fun List<Monster>.toLinkGroups(): List<MonsterLinkGroupView> =
    sortedBy { it.name.ru.lowercase() }
        .groupBy { it.name.ru.trim().first().uppercaseChar().toString() }
        .map { (initial, group) ->
            MonsterLinkGroupView(
                initial,
                group.map {
                    MonsterLinkView(
                        it.id,
                        it.challenge.label,
                        it.name.ru,
                        it.type.russianName,
                    )
                },
            )
        }

fun Monster.toDetailView() = MonsterDetailView(
    pageTitle = "${name.ru} · Гримуар",
    query = "",
    sizes = sizeOptions(),
    types = typeOptions(),
    challenges = challengeOptions(),
    challengeLabel = challenge.label,
    nameRu = name.ru,
    nameEn = name.en,
    combatStats = listOf(
        MonsterStatView(
            "КД",
            armorClass.value.toString() + armorClass.description?.let { " ($it)" }.orEmpty(),
        ),
        MonsterStatView(
            "Хиты",
            hitPoints.average.toString() + hitPoints.dice?.let { " ($it)" }.orEmpty(),
        ),
    ),
    abilityStats = listOf(
        MonsterStatView("СИЛ", abilities.strength.toString()),
        MonsterStatView("ЛОВ", abilities.dexterity.toString()),
        MonsterStatView("ТЕЛ", abilities.constitution.toString()),
        MonsterStatView("ИНТ", abilities.intelligence.toString()),
        MonsterStatView("МДР", abilities.wisdom.toString()),
        MonsterStatView("ХАР", abilities.charisma.toString()),
    ),
    facts = buildList {
        add(
            SpellFactView(
                "Размер и вид",
                "${this@toDetailView.size.russianName}, ${type.russianName}${subtype?.let { " ($it)" }.orEmpty()}",
            ),
        )
        add(SpellFactView("Мировоззрение", alignment))
        add(SpellFactView("Скорость", speeds.joinToString { "${it.type} ${it.distanceFeet} фт." }))
        senses?.let { add(SpellFactView("Чувства", it)) }
        if (languages.isNotEmpty()) add(SpellFactView("Языки", languages.joinToString()))
        add(SpellFactView("Опасность", challenge.label + challenge.experience?.let { " ($it опыта)" }.orEmpty()))
        if (environments.isNotEmpty()) add(SpellFactView("Местность", environments.joinToString()))
    },
    sections = sections.map { section ->
        MonsterSectionView(
            section.title,
            section.entries.map { MonsterRuleView(it.name, it.text) },
        )
    },
    description = description,
    sourcesLabel = sources.joinToString { it.code },
    sourceUrl = sourceUrl,
)

private fun Monster.toCardView() = MonsterCardView(
    id = id,
    challengeLabel = challenge.label,
    typeName = type.russianName,
    nameRu = name.ru,
    nameEn = name.en,
    summary = "${size.russianName}, ${type.russianName.lowercase()}, $alignment",
    description = description?.toCardPreview(),
    armorClass = armorClass.value,
    hitPoints = hitPoints.average,
)

private fun String.toCardPreview(): String? {
    val normalized = replace(Regex("\\s+"), " ").trim()
    if (normalized.isEmpty()) return null
    if (normalized.length <= MONSTER_DESCRIPTION_PREVIEW_LENGTH) return normalized

    val lastSpace = normalized.lastIndexOf(' ', MONSTER_DESCRIPTION_PREVIEW_LENGTH - 1)
    val endIndex = lastSpace.takeIf { it >= MONSTER_DESCRIPTION_PREVIEW_LENGTH / 2 }
        ?: MONSTER_DESCRIPTION_PREVIEW_LENGTH - 1
    return normalized.take(endIndex).trimEnd(' ', ',', ';', ':') + "…"
}

private fun sizeOptions(selected: Set<CreatureSize> = emptySet()) =
    MonsterFacets.sizes.options.map { option ->
        SelectOption(option.value, option.label, option.item in selected)
    }

private fun typeOptions(selected: Set<CreatureType> = emptySet()) =
    MonsterFacets.types.options.map { option ->
        SelectOption(option.value, option.label, option.item in selected)
    }

private fun challengeOptions(selected: Set<Double> = emptySet()) =
    MonsterFacets.challenges.options.map { option ->
        SelectOption(option.value, option.label, option.item in selected)
    }

private fun MonsterSearch.urlForPage(targetPage: Int, resultMode: SearchResultMode): String {
    val params = buildList {
        query?.let { add("q=${it.urlEncode()}") }
        sizes.sortedBy(MonsterFacets.sizes::serialize)
            .forEach { add("size=${MonsterFacets.sizes.serialize(it)}") }
        types.sortedBy(MonsterFacets.types::serialize)
            .forEach { add("type=${MonsterFacets.types.serialize(it)}") }
        challenges.sorted().forEach { add("challenge=${MonsterFacets.challenges.serialize(it)}") }
        add("view=${resultMode.queryValue}")
        add("page=$targetPage")
    }
    return "/monsters?${params.joinToString("&")}"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
private const val MONSTER_DESCRIPTION_PREVIEW_LENGTH = 300
