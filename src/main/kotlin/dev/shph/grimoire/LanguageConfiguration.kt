package dev.shph.grimoire

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import java.util.Locale

/**
 * Site languages. Russian is the default and lives at unprefixed URLs (`/classes/bard`);
 * every other language is served under a `/<tag>` path prefix (`/en/classes/bard`), and
 * its templates mirror the Russian ones under `templates/<tag>/`.
 */
enum class Language(val tag: String) {
    RU("ru"),
    EN("en"),
    ;

    val locale: Locale = Locale.forLanguageTag(tag)

    val pathPrefix: String
        get() = if (this == DEFAULT) "" else "/$tag"

    fun path(path: String) = pathPrefix + path

    fun template(name: String) = if (this == DEFAULT) name else "$tag/$name"

    companion object {
        val DEFAULT = RU

        fun of(locale: Locale) = entries.firstOrNull { it.tag == locale.language } ?: DEFAULT

        fun fromPath(path: String) =
            entries.firstOrNull { it != DEFAULT && (path == it.pathPrefix || path.startsWith("${it.pathPrefix}/")) }
                ?: DEFAULT
    }
}

@Configuration
class LanguageConfiguration {
    @Bean
    fun localeResolver(): LocaleResolver = PathLocaleResolver()
}

/** Takes the language from the URL path prefix, so every language version has its own URL. */
internal class PathLocaleResolver : LocaleResolver {
    override fun resolveLocale(request: HttpServletRequest): Locale =
        Language.fromPath(request.requestURI.removePrefix(request.contextPath)).locale

    override fun setLocale(request: HttpServletRequest, response: HttpServletResponse?, locale: Locale?) =
        throw UnsupportedOperationException("The language is chosen by the URL path")
}
