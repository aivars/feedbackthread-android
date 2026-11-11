package com.feedbackthread.sdk

import com.feedbackthread.sdk.FeedbackThreadStringResources.english
import com.feedbackthread.sdk.FeedbackThreadStringResources.italian
import com.feedbackthread.sdk.FeedbackThreadStringResources.pluralIds
import com.feedbackthread.sdk.FeedbackThreadStringResources.stringIds
import com.feedbackthread.sdk.FeedbackThreadStringResources.translations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrity of the shipped string resources: English (`values/`) is the
 * default, and every translation must be complete. The translations are found
 * on disk ([translations]: each `values-*` directory with a strings.xml), so
 * adding a language means adding its file and nothing else.
 */
public class FeedbackThreadLocalizationTest {
    private val files = mapOf("en" to english) + translations

    @Test
    public fun italianIsAmongTheTranslations() {
        // Discovery checks whatever is there: without this, a lost values-it would pass.
        assertTrue("translations found: ${translations.keys}", "it" in translations)
    }

    @Test
    public fun everyResourceNameCarriesTheSdkPrefix() {
        // Library resources merge into the host app: an unprefixed name would
        // be overridden by one of the app's own.
        for ((language, file) in files) {
            for (name in file.strings.keys + file.plurals.keys) {
                assertTrue("$language: $name lacks the feedbackthread_ prefix", name.startsWith("feedbackthread_"))
            }
        }
    }

    @Test
    public fun everyTranslationHasEveryEnglishResourceAndNothingElse() {
        for ((language, file) in translations) {
            assertEquals("$language strings", english.strings.keys, file.strings.keys)
            assertEquals("$language plurals", english.plurals.keys, file.plurals.keys)
        }
    }

    @Test
    public fun noValueIsEmpty() {
        for ((language, file) in files) {
            file.strings.forEach { (name, value) -> assertTrue("$language: $name is empty", value.isNotBlank()) }
            file.plurals.forEach { (name, items) ->
                items.forEach { (quantity, value) -> assertTrue("$language: $name/$quantity is empty", value.isNotBlank()) }
            }
        }
    }

    @Test
    public fun everyPlaceholderIsPositional() {
        for ((language, file) in files) {
            val values = file.strings.entries.map { it.key to it.value } +
                file.plurals.flatMap { (name, items) -> items.map { (quantity, value) -> "$name/$quantity" to value } }
            for ((name, value) in values) {
                for (placeholder in placeholders(value)) {
                    assertTrue("$language: $name uses $placeholder", placeholder.matches(Regex("%\\d+\\$.+")))
                }
            }
        }
    }

    @Test
    public fun translationsKeepTheEnglishPlaceholders() {
        for ((language, file) in translations) {
            for ((name, value) in file.strings) {
                assertEquals("$language: $name", placeholders(english.strings.getValue(name)), placeholders(value))
            }
            for ((name, items) in file.plurals) {
                val englishItems = english.plurals.getValue(name)
                for ((quantity, value) in items) {
                    // A quantity English lacks (e.g. "many") is compared with English "other".
                    val englishValue = englishItems[quantity] ?: englishItems.getValue("other")
                    assertEquals("$language: $name/$quantity", placeholders(englishValue), placeholders(value))
                }
            }
        }
    }

    @Test
    public fun everyPluralHasTheQuantitiesItsLanguageNeeds() {
        for ((language, file) in files) {
            file.plurals.forEach { (name, items) -> assertTrue("$language: $name lacks other", "other" in items) }
        }
        for ((name, items) in english.plurals) {
            assertTrue("en: $name lacks one", "one" in items)
        }
        for ((name, items) in italian.plurals) {
            assertTrue("it: $name lacks one", "one" in items)
        }
    }

    @Test
    public fun everyGeneratedResourceIsInEveryFile() {
        assertTrue(stringIds.isNotEmpty())
        assertTrue(pluralIds.isNotEmpty())
        for ((language, file) in files) {
            stringIds.keys.forEach { assertTrue("$language lacks string $it", it in file.strings) }
            pluralIds.keys.forEach { assertTrue("$language lacks plurals $it", it in file.plurals) }
        }
    }

    @Test
    public fun generatedIdsAreDistinctSoMappingTestsCanTellResourcesApart() {
        assertEquals(stringIds.size, stringIds.values.toSet().size)
        assertTrue(stringIds.values.none { it == 0 })
    }

    @Test
    public fun apostrophesAreEscapedEverywhereAndTypographicInItalian() {
        // aapt's rule, for every file: a straight apostrophe needs its backslash.
        for ((language, file) in files) {
            val raws = file.rawStrings.entries.map { it.key to it.value } +
                file.rawPlurals.flatMap { (name, items) -> items.map { (quantity, raw) -> "$name/$quantity" to raw } }
            for ((name, raw) in raws) {
                assertTrue("$language: $name has an unescaped apostrophe", !Regex("(?<!\\\\)'").containsMatchIn(raw))
            }
        }
        // Italian's own rule: no straight apostrophe at all, escaped or not.
        italian.strings.forEach { (name, value) -> assertTrue("it: $name uses a straight apostrophe", '\'' !in value) }
        italian.plurals.forEach { (name, items) ->
            items.values.forEach { assertTrue("it: $name uses a straight apostrophe", '\'' !in it) }
        }
    }

    private fun placeholders(value: String): List<String> =
        Regex("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[a-zA-Z%]").findAll(value)
            .map { it.value }
            .filter { it != "%%" }
            .sorted()
            .toList()
}
