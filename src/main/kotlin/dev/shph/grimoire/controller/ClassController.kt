package dev.shph.grimoire.controller

import dev.shph.grimoire.Language
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.util.Locale

@Controller
class ClassController {
    @GetMapping("/classes", "/en/classes")
    fun index(locale: Locale, model: Model): String {
        model.addAttribute("alternates", alternates("/classes"))
        return Language.of(locale).template("classes/index")
    }

    @GetMapping("/classes/{slug}", "/en/classes/{slug}")
    fun detail(@PathVariable slug: String, locale: Locale, model: Model): String {
        val page = articlePage("classes", slug, Language.of(locale)) ?: throw ClassNotFoundException()
        model.addAttribute("alternates", articleAlternates("classes", slug))
        return page
    }
}

class ClassNotFoundException : RuntimeException("Class not found")
