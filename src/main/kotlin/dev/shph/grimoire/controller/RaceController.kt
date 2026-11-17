package dev.shph.grimoire.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class RaceController {
    @GetMapping("/races")
    fun index(): String = "races/index"

    @GetMapping("/races/{slug}")
    fun detail(@PathVariable slug: String): String =
        articlePage("races", slug) ?: throw RaceNotFoundException()
}

class RaceNotFoundException : RuntimeException("Race not found")
