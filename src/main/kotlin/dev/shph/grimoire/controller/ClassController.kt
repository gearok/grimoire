package dev.shph.grimoire.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class ClassController {
    @GetMapping("/classes")
    fun index(): String = "classes/index"

    @GetMapping("/classes/{slug}")
    fun detail(@PathVariable slug: String): String =
        articlePage("classes", slug) ?: throw ClassNotFoundException()
}

class ClassNotFoundException : RuntimeException("Class not found")
