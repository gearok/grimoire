package dev.shph.grimoire.model

data class FacetOption<T>(
    val value: String,
    val label: String,
    val item: T,
)

class FacetCatalog<T>(
    val options: List<FacetOption<T>>,
    private val parser: (String) -> T?,
) {
    private val optionsByItem = options.associateBy(FacetOption<T>::item)

    init {
        require(optionsByItem.size == options.size) { "Facet items must be unique" }
        require(options.map(FacetOption<T>::value).distinct().size == options.size) {
            "Facet values must be unique"
        }
    }

    fun parse(value: String): T? = parser(value)?.takeIf(optionsByItem::containsKey)

    fun serialize(item: T): String =
        requireNotNull(optionsByItem[item]) { "Unsupported facet item: $item" }.value
}

object SpellFacets {
    val levels = FacetCatalog(
        options = (0..9).map { level ->
            FacetOption(level.toString(), if (level == 0) "Заговор" else level.toString(), level)
        },
        parser = String::toIntOrNull,
    )

    val schools = FacetCatalog(
        options = MagicSchool.entries.map { school ->
            FacetOption(school.slug, school.russianName, school)
        },
        parser = MagicSchool::fromSlug,
    )

    val characterClasses = FacetCatalog(
        options = listOf(
            "бард",
            "волшебник",
            "друид",
            "жрец",
            "изобретатель",
            "колдун",
            "паладин",
            "следопыт",
            "чародей",
        ).map { characterClass ->
            FacetOption(
                characterClass,
                characterClass.replaceFirstChar { it.uppercase() },
                characterClass,
            )
        },
        parser = String::lowercase,
    )
}

object MonsterFacets {
    val sizes = FacetCatalog(
        options = CreatureSize.entries.map { size ->
            FacetOption(size.slug, size.russianName, size)
        },
        parser = CreatureSize::fromSlug,
    )

    val types = FacetCatalog(
        options = CreatureType.entries.map { type ->
            FacetOption(type.slug, type.russianName, type)
        },
        parser = CreatureType::fromSlug,
    )

    val challenges = FacetCatalog(
        options = listOf(
            FacetOption("0", "0", 0.0),
            FacetOption("0.125", "1/8", 0.125),
            FacetOption("0.25", "1/4", 0.25),
            FacetOption("0.5", "1/2", 0.5),
        ) + (1..30).map { challenge ->
            FacetOption(challenge.toString(), challenge.toString(), challenge.toDouble())
        },
        parser = String::toDoubleOrNull,
    )
}
