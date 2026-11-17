package dev.shph.grimoire.controller

import org.springframework.core.io.ClassPathResource

private val SLUG = Regex("[a-z]+(?:-[a-z]+)*")

/**
 * Classes and races are small, fixed sets of long-form articles, so each one is a
 * hand-maintained HTML page under `templates/<section>/pages/<slug>.html` rather than
 * structured data. Returns the view name for [slug], or null when no such page exists.
 */
internal fun articlePage(section: String, slug: String): String? =
    "$section/pages/$slug".takeIf { slug.matches(SLUG) && ClassPathResource("templates/$it.html").exists() }
