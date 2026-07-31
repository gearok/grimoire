package dev.shph.grimoire.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

@Document(indexName = "monsters", createIndex = false)
data class Monster(
    @Id
    val id: String,
    val slug: String,
    val name: LocalizedName,
    val aliases: List<String> = emptyList(),
    val size: CreatureSize,
    val type: CreatureType,
    val subtype: String? = null,
    val alignment: String,
    val armorClass: ArmorClass,
    val hitPoints: HitPoints,
    val speeds: List<MonsterSpeed>,
    val abilities: AbilityScores,
    val savingThrows: List<NamedModifier> = emptyList(),
    val skills: List<NamedModifier> = emptyList(),
    val damageVulnerabilities: List<String> = emptyList(),
    val damageResistances: List<String> = emptyList(),
    val damageImmunities: List<String> = emptyList(),
    val conditionImmunities: List<String> = emptyList(),
    val senses: String? = null,
    val languages: List<String> = emptyList(),
    val challenge: ChallengeRating,
    val proficiencyBonus: Int? = null,
    val environments: List<String> = emptyList(),
    val namedNpc: Boolean = false,
    val sections: List<MonsterSection> = emptyList(),
    val description: String? = null,
    val sources: List<SourceReference>,
    val sourceUrl: String,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "id must be URL-safe" }
        require(slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "slug must be URL-safe" }
        require(name.ru.isNotBlank() && name.en.isNotBlank()) { "both monster names are required" }
        require(armorClass.value > 0) { "armor class must be positive" }
        require(hitPoints.average > 0) { "hit points must be positive" }
        require(sources.isNotEmpty()) { "at least one source is required" }
    }
}

enum class CreatureSize(val slug: String, val russianName: String) {
    TINY("tiny", "Крошечный"),
    SMALL("small", "Маленький"),
    MEDIUM("medium", "Средний"),
    LARGE("large", "Большой"),
    HUGE("huge", "Огромный"),
    GARGANTUAN("gargantuan", "Громадный");

    @JsonValue
    fun toJson(): String = slug

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromSlug(value: String?): CreatureSize? = entries.firstOrNull { it.slug == value }

        fun fromRussian(value: String): CreatureSize? =
            entries.firstOrNull {
                value.substringBefore(" ").lowercase().startsWith(
                    when (it) {
                        TINY -> "крошечн"
                        SMALL -> "маленьк"
                        MEDIUM -> "средн"
                        LARGE -> "больш"
                        HUGE -> "огромн"
                        GARGANTUAN -> "громадн"
                    },
                )
            }
    }
}

enum class CreatureType(val slug: String, val russianName: String) {
    ABERRATION("aberration", "Аберрация"),
    BEAST("beast", "Зверь"),
    CELESTIAL("celestial", "Небожитель"),
    CONSTRUCT("construct", "Конструкт"),
    DRAGON("dragon", "Дракон"),
    ELEMENTAL("elemental", "Элементаль"),
    FEY("fey", "Фея"),
    FIEND("fiend", "Исчадие"),
    GIANT("giant", "Великан"),
    HUMANOID("humanoid", "Гуманоид"),
    MONSTROSITY("monstrosity", "Монстр"),
    OOZE("ooze", "Слизь"),
    PLANT("plant", "Растение"),
    UNDEAD("undead", "Нежить");

    @JsonValue
    fun toJson(): String = slug

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromSlug(value: String?): CreatureType? = entries.firstOrNull { it.slug == value }

        fun fromRussian(value: String): CreatureType? =
            entries.firstOrNull {
                value.contains(it.russianName, ignoreCase = true) ||
                    (it == BEAST && value.contains("звер", ignoreCase = true))
            }
    }
}

data class ArmorClass(val value: Int, val description: String? = null)

data class HitPoints(val average: Int, val dice: String? = null)

data class MonsterSpeed(
    val type: String,
    val distanceFeet: Int,
    val hover: Boolean = false,
)

data class AbilityScores(
    val strength: Int,
    val dexterity: Int,
    val constitution: Int,
    val intelligence: Int,
    val wisdom: Int,
    val charisma: Int,
)

data class NamedModifier(val name: String, val value: Int)

data class ChallengeRating(
    val value: Double,
    val label: String,
    val experience: Int? = null,
)

data class MonsterSection(
    val title: String,
    val entries: List<MonsterRule>,
)

data class MonsterRule(
    val name: String? = null,
    val text: String,
)

data class MonsterSearch(
    val query: String? = null,
    val sizes: Set<CreatureSize> = emptySet(),
    val types: Set<CreatureType> = emptySet(),
    val challenges: Set<Double> = emptySet(),
    val page: Int = 1,
    val pageSize: Int? = 30,
) {
    val isUnfiltered: Boolean
        get() = query == null &&
            sizes.isEmpty() &&
            types.isEmpty() &&
            challenges.isEmpty()
}

data class MonsterSearchResult(
    val monsters: List<Monster>,
    val total: Long,
    val page: Int,
    val pageSize: Int?,
)
