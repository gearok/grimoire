package dev.shph.grimoire.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Spell(
    val id: String,
    val slug: String,
    val name: LocalizedName,
    val aliases: List<String> = emptyList(),
    val level: Int,
    val school: MagicSchool,
    val castingTime: CastingTime,
    val range: String,
    val components: SpellComponents,
    val duration: String,
    val concentration: Boolean = false,
    val ritual: Boolean = false,
    val classes: List<ClassAccess> = emptyList(),
    val subclasses: List<SubclassAccess> = emptyList(),
    val description: String,
    val higherLevels: String? = null,
    val damageTypes: List<String> = emptyList(),
    val sources: List<SourceReference>,
    val sourceUrl: String,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "id must be URL-safe" }
        require(slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "slug must be URL-safe" }
        require(level in 0..9) { "level must be between 0 and 9" }
        require(name.ru.isNotBlank() && name.en.isNotBlank()) { "both spell names are required" }
        require(sources.isNotEmpty()) { "at least one source is required" }
    }
}

@Serializable
data class LocalizedName(
    val ru: String,
    val en: String,
)

@Serializable
enum class MagicSchool(val slug: String, val russianName: String) {
    @SerialName("abjuration")
    ABJURATION("abjuration", "Ограждение"),

    @SerialName("conjuration")
    CONJURATION("conjuration", "Вызов"),

    @SerialName("divination")
    DIVINATION("divination", "Прорицание"),

    @SerialName("enchantment")
    ENCHANTMENT("enchantment", "Очарование"),

    @SerialName("evocation")
    EVOCATION("evocation", "Воплощение"),

    @SerialName("illusion")
    ILLUSION("illusion", "Иллюзия"),

    @SerialName("necromancy")
    NECROMANCY("necromancy", "Некромантия"),

    @SerialName("transmutation")
    TRANSMUTATION("transmutation", "Преобразование");

    companion object {
        fun fromSlug(value: String?): MagicSchool? = entries.firstOrNull { it.slug == value }
    }
}

@Serializable
data class CastingTime(
    val text: String,
    val type: CastingTimeType,
    val reactionTrigger: String? = null,
)

@Serializable
enum class CastingTimeType {
    @SerialName("action")
    ACTION,

    @SerialName("bonus_action")
    BONUS_ACTION,

    @SerialName("reaction")
    REACTION,

    @SerialName("minute")
    MINUTE,

    @SerialName("hour")
    HOUR,

    @SerialName("other")
    OTHER,
}

@Serializable
data class SpellComponents(
    val verbal: Boolean = false,
    val somatic: Boolean = false,
    val material: Boolean = false,
    val materialDescription: String? = null,
    val materialCostGp: Int? = null,
    val materialConsumed: Boolean = false,
)

@Serializable
data class ClassAccess(
    val name: String,
    val optional: Boolean = false,
    val sourceCode: String? = null,
)

@Serializable
data class SubclassAccess(
    val name: String,
    val parentClass: String,
)

@Serializable
data class SourceReference(
    val code: String,
    val title: String,
    val page: Int? = null,
    val edition: String = "5e-2014",
)

@Serializable
data class SpellSearch(
    val query: String? = null,
    val levels: Set<Int> = emptySet(),
    val schools: Set<MagicSchool> = emptySet(),
    val characterClasses: Set<String> = emptySet(),
    val page: Int = 1,
    val pageSize: Int = 30,
) {
    val offset: Int get() = (page - 1) * pageSize
}

@Serializable
data class SpellSearchResult(
    val spells: List<Spell>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)
