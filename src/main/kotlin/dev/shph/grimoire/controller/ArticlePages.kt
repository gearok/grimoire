package dev.shph.grimoire.controller

import dev.shph.grimoire.Language
import org.springframework.core.io.ClassPathResource
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

private val SLUG = Regex("[a-z]+(?:-[a-z]+)*")

/**
 * Classes and races are small, fixed sets of long-form articles, so each one is a
 * hand-maintained HTML page under `templates/<section>/pages/<slug>.html` rather than
 * structured data. Translations live under `templates/<language>/<section>/pages/<slug>.html`.
 * Returns the view name for [slug] in [language], or null when no such page exists.
 */
internal fun articlePage(section: String, slug: String, language: Language = Language.DEFAULT): String? =
    language.template("$section/pages/$slug")
        .takeIf { slug.matches(SLUG) && ClassPathResource("templates/$it.html").exists() }

/** Link to one language version of a page: [href] for navigation, absolute [url] for `hreflang`. */
data class Alternate(val href: String, val url: String)

/** Language versions of the page at [path] (written without a language prefix), keyed by language tag. */
internal fun alternates(path: String, languages: Collection<Language> = Language.entries): Map<String, Alternate> {
    val base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString()
    return languages.associate { it.tag to it.path(path).let { href -> Alternate(href, base + href) } }
}

internal fun articleAlternates(section: String, slug: String) =
    alternates("/$section/$slug", Language.entries.filter { articlePage(section, slug, it) != null })
