package dev.shph.grimoire.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document

@Document(indexName = "spells", createIndex = false)
data class Spell(
    @Id
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

data class LocalizedName(
    val ru: String,
    val en: String,
)

enum class MagicSchool(val slug: String, val russianName: String) {
    ABJURATION("abjuration", "Ограждение"),

    CONJURATION("conjuration", "Вызов"),

    DIVINATION("divination", "Прорицание"),

    ENCHANTMENT("enchantment", "Очарование"),

    EVOCATION("evocation", "Воплощение"),

    ILLUSION("illusion", "Иллюзия"),

    NECROMANCY("necromancy", "Некромантия"),

    TRANSMUTATION("transmutation", "Преобразование");

    @JsonValue
    fun toJson(): String = slug

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromSlug(value: String?): MagicSchool? = entries.firstOrNull { it.slug == value }
    }
}

data class CastingTime(
    val text: String,
    val type: CastingTimeType,
    val reactionTrigger: String? = null,
)

enum class CastingTimeType(private val jsonValue: String) {
    ACTION("action"),
    BONUS_ACTION("bonus_action"),
    REACTION("reaction"),
    MINUTE("minute"),
    HOUR("hour"),
    OTHER("other");

    @JsonValue
    fun toJson(): String = jsonValue

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromJson(value: String): CastingTimeType =
            entries.firstOrNull { it.jsonValue == value }
                ?: throw IllegalArgumentException("Unknown casting time type: $value")
    }
}

data class SpellComponents(
    val verbal: Boolean = false,
    val somatic: Boolean = false,
    val material: Boolean = false,
    val materialDescription: String? = null,
    val materialCostGp: Int? = null,
    val materialConsumed: Boolean = false,
)

data class ClassAccess(
    val name: String,
    val optional: Boolean = false,
    val sourceCode: String? = null,
)

data class SubclassAccess(
    val name: String,
    val parentClass: String,
)

data class SourceReference(
    val code: String,
    val title: String,
    val page: Int? = null,
    val edition: String = "5e-2014",
)

data class SpellSearch(
    val query: String? = null,
    val levels: Set<Int> = emptySet(),
    val schools: Set<MagicSchool> = emptySet(),
    val characterClasses: Set<String> = emptySet(),
    val page: Int = 1,
    val pageSize: Int? = 30,
) {
    val isUnfiltered: Boolean
        get() = query == null &&
            levels.isEmpty() &&
            schools.isEmpty() &&
            characterClasses.isEmpty()
}

data class SpellSearchResult(
    val spells: List<Spell>,
    val total: Long,
    val page: Int,
    val pageSize: Int?,
)
