package dev.shph.grimoire

import dev.shph.grimoire.model.CastingTimeType
import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import dev.shph.grimoire.model.MagicSchool
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions

@Configuration(proxyBeanMethods = false)
class ElasticsearchConfiguration {
    @Bean
    fun elasticsearchCustomConversions() = ElasticsearchCustomConversions(
        listOf(
            MagicSchoolReader,
            CastingTimeTypeReader,
            CreatureSizeReader,
            CreatureTypeReader,
        ),
    )
}

@ReadingConverter
private object MagicSchoolReader : Converter<String, MagicSchool> {
    override fun convert(source: String) =
        MagicSchool.fromSlug(source) ?: throw IllegalArgumentException("Unknown magic school: $source")
}

@ReadingConverter
private object CastingTimeTypeReader : Converter<String, CastingTimeType> {
    override fun convert(source: String) = CastingTimeType.fromJson(source)
}

@ReadingConverter
private object CreatureSizeReader : Converter<String, CreatureSize> {
    override fun convert(source: String) =
        CreatureSize.fromSlug(source) ?: throw IllegalArgumentException("Unknown creature size: $source")
}

@ReadingConverter
private object CreatureTypeReader : Converter<String, CreatureType> {
    override fun convert(source: String) =
        CreatureType.fromSlug(source) ?: throw IllegalArgumentException("Unknown creature type: $source")
}
