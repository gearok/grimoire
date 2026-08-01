package dev.shph.grimoire.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScraperConfigTest {
    @Test
    fun `imports every content type by default`() {
        val config = ScraperConfig.parse(emptyArray())

        assertEquals(ScraperContent.entries.toSet(), config.content)
    }

    @Test
    fun `parses a combined import with separate indexes`() {
        val config = ScraperConfig.parse(
            arrayOf(
                "--content=spells,monsters",
                "--spells-index=test-spells",
                "--monsters-index=test-monsters",
            ),
        )

        assertEquals(setOf(ScraperContent.SPELLS, ScraperContent.MONSTERS), config.content)
        assertEquals("test-spells", config.spellsIndexName)
        assertEquals("test-monsters", config.monstersIndexName)
    }

    @Test
    fun `keeps index shorthand for a single content type`() {
        val config = ScraperConfig.parse(arrayOf("--content=monsters", "--index=test-monsters"))

        assertEquals("test-monsters", config.monstersIndexName)
    }

    @Test
    fun `rejects the ambiguous index shorthand for a combined import`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ScraperConfig.parse(arrayOf("--content=spells,monsters", "--index=combined"))
        }

        assertTrue(failure.message.orEmpty().contains("--<type>-index"))
    }

    @Test
    fun `parses per-type JSON output paths for classes and races`() {
        val config = ScraperConfig.parse(
            arrayOf(
                "--content=classes,races",
                "--classes-out=out/classes.json",
                "--races-out=out/races.json",
            ),
        )

        assertEquals(setOf(ScraperContent.CLASSES, ScraperContent.RACES), config.content)
        assertEquals("out/classes.json", config.outputPath(ScraperContent.CLASSES))
        assertEquals("out/races.json", config.outputPath(ScraperContent.RACES))
    }

    @Test
    fun `keeps out shorthand for a single file content type`() {
        val config = ScraperConfig.parse(arrayOf("--content=classes", "--out=out/classes.json"))

        assertEquals("out/classes.json", config.outputPath(ScraperContent.CLASSES))
    }

    @Test
    fun `rejects the ambiguous out shorthand for multiple file content types`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ScraperConfig.parse(arrayOf("--content=classes,races", "--out=combined.json"))
        }

        assertTrue(failure.message.orEmpty().contains("--<type>-out"))
    }

    @Test
    fun `rejects using the same index for both content types`() {
        assertFailsWith<IllegalArgumentException> {
            ScraperConfig.parse(
                arrayOf(
                    "--content=spells,monsters",
                    "--spells-index=shared",
                    "--monsters-index=shared",
                ),
            )
        }
    }

    @Test
    fun `rejects an empty content list entry`() {
        assertFailsWith<IllegalArgumentException> {
            ScraperConfig.parse(arrayOf("--content=spells,"))
        }
    }

    @Test
    fun `parses the request concurrency limit`() {
        val config = ScraperConfig.parse(arrayOf("--concurrency=8"))

        assertEquals(8, config.concurrency)
    }

    @Test
    fun `rejects excessive request concurrency`() {
        assertFailsWith<IllegalArgumentException> {
            ScraperConfig.parse(arrayOf("--concurrency=33"))
        }
    }

    @Test
    fun `rejects the removed import limit option`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            ScraperConfig.parse(arrayOf("--limit=10"))
        }

        assertTrue(failure.message.orEmpty().contains("unknown option"))
    }
}
