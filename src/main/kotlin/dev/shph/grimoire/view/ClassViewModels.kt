package dev.shph.grimoire.view

import dev.shph.grimoire.model.CharacterClass

data class ClassIndexView(
    val total: Int,
    val groups: List<ClassLinkGroupView>,
)

data class ClassLinkGroupView(val initial: String, val classes: List<ClassLinkView>)

data class ClassLinkView(
    val id: String,
    val nameRu: String,
    val nameEn: String,
    val hitDieLabel: String?,
)

data class ClassDetailView(
    val pageTitle: String,
    val nameRu: String,
    val nameEn: String,
    val facts: List<FactView>,
    val subclasses: List<String>,
    val sections: List<RuleSectionView>,
    val description: String?,
    val sourcesLabel: String,
    val sourceUrl: String,
)

fun List<CharacterClass>.toIndexView(): ClassIndexView {
    val groups = sortedBy { it.name.ru.lowercase() }
        .groupBy { it.name.ru.trim().first().uppercaseChar().toString() }
        .map { (initial, group) ->
            ClassLinkGroupView(
                initial,
                group.map {
                    ClassLinkView(
                        id = it.id,
                        nameRu = it.name.ru,
                        nameEn = it.name.en,
                        hitDieLabel = it.hitDie?.let { die -> "к$die" },
                    )
                },
            )
        }
    return ClassIndexView(total = size, groups = groups)
}

fun CharacterClass.toDetailView() = ClassDetailView(
    pageTitle = "${name.ru} · Гримуар",
    nameRu = name.ru,
    nameEn = name.en,
    facts = buildList {
        hitDie?.let { add(FactView("Кость хитов", "к$it")) }
        if (savingThrows.isNotEmpty()) {
            add(FactView("Спасброски", savingThrows.joinToString { it.replaceFirstChar(Char::uppercase) }))
        }
        proficiencies.armor.takeIf { it.isNotEmpty() }
            ?.let { add(FactView("Доспехи", it.joinToString())) }
        proficiencies.weapons.takeIf { it.isNotEmpty() }
            ?.let { add(FactView("Оружие", it.joinToString())) }
        proficiencies.tools.takeIf { it.isNotEmpty() }
            ?.let { add(FactView("Инструменты", it.joinToString())) }
        proficiencies.skills.takeIf { it.isNotEmpty() }
            ?.let { add(FactView("Навыки", it.joinToString())) }
    },
    subclasses = subclasses.map { it.ru },
    sections = sections.map { it.toView() },
    description = description,
    sourcesLabel = sources.joinToString { it.code },
    sourceUrl = sourceUrl,
)
