package dev.shph.grimoire.scraper

import dev.shph.grimoire.model.LocalizedName
import dev.shph.grimoire.model.Rule
import dev.shph.grimoire.model.RuleSection
import dev.shph.grimoire.model.SourceReference
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Shared parsing of a dnd.su long-form article card (used by class and race pages).
 *
 * Unlike the compact spell and monster stat blocks, these pages are free-form articles:
 * the title lives in `.card-title`, the source in a `Источник:` parameter, and the body
 * is prose organised under `<h2>/<h3>` headings with `<strong>Name.</strong> text` rules.
 */
internal class DndSuArticle(html: String, requestedUrl: String, kind: String) {
    private val document: Document = org.jsoup.Jsoup.parse(html, requestedUrl)
    val card: Element = document.selectFirst("div.card")
        ?: error("$kind card is missing at $requestedUrl")

    val id: String = URI(requestedUrl).path.trim('/').substringAfterLast('/').substringBefore("-")
        .ifBlank { error("$kind id is missing at $requestedUrl") }

    val slug: String = slugFrom(URI(requestedUrl).path)

    val name: LocalizedName = run {
        val title = card.selectFirst(".card-title")?.text()?.trim()
            ?: error("$kind title is missing at $requestedUrl")
        val match = NAME.matchEntire(title)
            ?: error("expected a bilingual $kind title, got '$title'")
        LocalizedName(match.groupValues[1].trim(), match.groupValues[2].trim())
    }

    val sourceUrl: String = document.selectFirst("meta[property=og:url]")?.attr("content")
        ?.takeIf(String::isNotBlank) ?: requestedUrl

    /** The article body element carrying the prose (`div.desc[itemprop=articleBody]`). */
    val body: Element = card.selectFirst("div.desc[itemprop=articleBody]")
        ?: card.selectFirst("div.card__body[itemprop=articleBody]")
        ?: error("$kind body is missing at $requestedUrl")

    /** The `Источник:` parameters list, if present, holding the source titles. */
    val parameters: Element? = card.selectFirst("ul.params")

    fun sources(kind: String): List<SourceReference> {
        val titles = parameters?.select("li")
            ?.filter { it.selectFirst("strong")?.text()?.trim()?.startsWith("Источник") == true }
            ?.flatMap { li -> li.select("span").map { it.text() }.ifEmpty { listOf(sourceText(li)) } }
            ?.map { it.trim().trim('«', '»', '"').trim() }
            ?.filter(String::isNotEmpty)
            .orEmpty()
            .distinct()
        require(titles.isNotEmpty()) { "$kind source is missing" }
        return titles.map { SourceReference(code = sourceCode(it), title = it) }
    }

    /**
     * Splits the article body into titled sections at each heading, collecting the
     * paragraph rules under each. Walks all descendants in document order (not just direct
     * children) because dnd.su nests article content inside collapsible `spoiler` blocks,
     * and folds the heading depth away so class and race pages parse uniformly.
     */
    fun sections(): List<RuleSection> {
        val sections = mutableListOf<RuleSection>()
        var title = "Описание"
        var entries = mutableListOf<Rule>()
        fun flush() {
            if (entries.isNotEmpty()) {
                val existing = sections.indexOfFirst { it.title == title }
                if (existing >= 0) {
                    sections[existing] = sections[existing].copy(entries = sections[existing].entries + entries)
                } else {
                    sections += RuleSection(title, entries.toList())
                }
            }
            entries = mutableListOf()
        }
        body.select("h1, h2, h3, h4, h5, h6, p, blockquote").forEach { element ->
            if (element.tagName().matches(HEADING)) {
                flush()
                title = element.text().trim().ifEmpty { title }
            } else {
                rule(element)?.let(entries::add)
            }
        }
        flush()
        return sections
    }

    /** The plain-text description, taken from the leading unheaded prose. */
    fun description(): String? =
        sections().firstOrNull { it.title.equals("Описание", ignoreCase = true) }
            ?.entries?.joinToString("\n\n") { it.text }?.takeIf(String::isNotBlank)

    private fun rule(paragraph: Element): Rule? {
        val text = paragraph.text().trim()
        if (text.isEmpty()) return null
        val label = paragraph.selectFirst("strong, b")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() && it.length < 80 && text.startsWith(it) }
        val name = label?.trimEnd('.', ':', ' ')?.takeIf(String::isNotEmpty)
        return Rule(
            name = name,
            text = label?.let { text.removePrefix(it).trim() } ?: text,
        )
    }

    private fun sourceText(li: Element): String =
        li.text().substringAfter(":", "").trim()

    private fun sourceCode(title: String): String =
        title.split(Regex("\\s+")).filter(String::isNotEmpty)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifEmpty { "SRC" }

    private fun slugFrom(path: String): String =
        path.trim('/').substringAfterLast('/').substringAfter("-")
            .lowercase().replace("_", "-").replace(Regex("[^a-z0-9-]+"), "-")
            .replace(Regex("-+"), "-").trim('-')

    private companion object {
        val NAME = Regex("""(.+?)\s*\[([^\]]+)]""")
        val HEADING = Regex("h[1-6]")
    }
}
