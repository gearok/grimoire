package dev.shph.grimoire

import dev.shph.grimoire.model.CastingTimeType
import dev.shph.grimoire.model.MagicSchool
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions

@Configuration(proxyBeanMethods = false)
class ElasticsearchConfiguration {
    @Bean
    fun elasticsearchCustomConversions() = ElasticsearchCustomConversions(
        listOf(
            MagicSchoolWriter,
            MagicSchoolReader,
            CastingTimeTypeWriter,
            CastingTimeTypeReader,
        ),
    )
}

@WritingConverter
private object MagicSchoolWriter : Converter<MagicSchool, String> {
    override fun convert(source: MagicSchool) = source.slug
}

@ReadingConverter
private object MagicSchoolReader : Converter<String, MagicSchool> {
    override fun convert(source: String) =
        MagicSchool.fromSlug(source) ?: throw IllegalArgumentException("Unknown magic school: $source")
}

@WritingConverter
private object CastingTimeTypeWriter : Converter<CastingTimeType, String> {
    override fun convert(source: CastingTimeType) = source.toJson()
}

@ReadingConverter
private object CastingTimeTypeReader : Converter<String, CastingTimeType> {
    override fun convert(source: String) = CastingTimeType.fromJson(source)
}
