package dev.shph.grimoire.model

data class CharacterClass(
    val id: String,
    val slug: String,
    val name: LocalizedName,
    val aliases: List<String> = emptyList(),
    val hitDie: Int? = null,
    val primaryAbilities: List<String> = emptyList(),
    val savingThrows: List<String> = emptyList(),
    val proficiencies: ClassProficiencies = ClassProficiencies(),
    val subclasses: List<LocalizedName> = emptyList(),
    val sections: List<RuleSection> = emptyList(),
    val description: String? = null,
    val sources: List<SourceReference>,
    val sourceUrl: String,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "id must be URL-safe" }
        require(slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "slug must be URL-safe" }
        require(name.ru.isNotBlank() && name.en.isNotBlank()) { "both class names are required" }
        require(hitDie == null || hitDie in 1..20) { "hit die must be between 1 and 20" }
        require(sources.isNotEmpty()) { "at least one source is required" }
    }
}

data class ClassProficiencies(
    val armor: List<String> = emptyList(),
    val weapons: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
)
