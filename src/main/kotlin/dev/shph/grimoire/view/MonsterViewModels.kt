package dev.shph.grimoire.view

import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import dev.shph.grimoire.model.Monster
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.model.MonsterSearchResult
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
    val cardsView: Boolean,
    val hasFilters: Boolean,
    val pageLabel: String?,
    val previousUrl: String?,
    val nextUrl: String?,
)

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
    cardsView: Boolean = false,
): MonsterIndexView {
    val totalPages = ((total + pageSize - 1) / pageSize).toInt()
    return MonsterIndexView(
        query = criteria.query.orEmpty(),
        sizes = sizeOptions(criteria.sizes),
        types = typeOptions(criteria.types),
        challenges = challengeOptions(criteria.challenges),
        total = total,
        monsters = monsters.map(Monster::toCardView),
        monsterGroups = monsters.sortedBy { it.name.ru.lowercase() }
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
            },
        cardsView = cardsView,
        hasFilters = criteria.query != null ||
            criteria.sizes.isNotEmpty() ||
            criteria.types.isNotEmpty() ||
            criteria.challenges.isNotEmpty(),
        pageLabel = if (totalPages > 1) "Страница $page из $totalPages" else null,
        previousUrl = if (page > 1) criteria.urlForPage(page - 1, cardsView) else null,
        nextUrl = if (page < totalPages) criteria.urlForPage(page + 1, cardsView) else null,
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
    CreatureSize.entries.map { SelectOption(it.slug, it.russianName, it in selected) }

private fun typeOptions(selected: Set<CreatureType> = emptySet()) =
    CreatureType.entries.map { SelectOption(it.slug, it.russianName, it in selected) }

private fun challengeOptions(selected: Set<Double> = emptySet()) =
    CHALLENGE_OPTIONS.map { (value, label) ->
        SelectOption(value.toQueryValue(), label, value in selected)
    }

private fun MonsterSearch.urlForPage(targetPage: Int, cardsView: Boolean): String {
    val params = buildList {
        query?.let { add("q=${it.urlEncode()}") }
        sizes.sortedBy { it.slug }.forEach { add("size=${it.slug}") }
        types.sortedBy { it.slug }.forEach { add("type=${it.slug}") }
        challenges.sorted().forEach { add("challenge=${it.toQueryValue()}") }
        if (cardsView) add("view=cards")
        add("page=$targetPage")
    }
    return "/monsters?${params.joinToString("&")}"
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
private fun Double.toQueryValue() = if (this % 1.0 == 0.0) toInt().toString() else toString()

private const val MONSTER_DESCRIPTION_PREVIEW_LENGTH = 300
private val CHALLENGE_OPTIONS = listOf(
    0.0 to "0",
    0.125 to "1/8",
    0.25 to "1/4",
    0.5 to "1/2",
) + (1..30).map { it.toDouble() to it.toString() }
