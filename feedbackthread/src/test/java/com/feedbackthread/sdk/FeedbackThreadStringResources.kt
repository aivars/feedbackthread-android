package com.feedbackthread.sdk

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The SDK's string resources read straight from `src/main/res`, so plain JVM
 * tests can check the copy that ships - and what a [FeedbackThreadText]
 * reads as in English and in Italian - without Robolectric.
 */
internal object FeedbackThreadStringResources {
    /**
     * Gradle runs unit tests from the module directory; an IDE may run them
     * from the repository root.
     */
    val resDirectory: File = listOf(File("src/main/res"), File("feedbackthread/src/main/res"))
        .firstOrNull { File(it, "values/strings.xml").isFile }
        ?: error("src/main/res not found from ${File("").absolutePath}")

    val english = FeedbackThreadStringFile(File(resDirectory, "values/strings.xml"), Locale.ENGLISH)

    /**
     * Every translation, by resource qualifier (`it`, `pt-rBR`): each
     * `values-*` directory that holds a strings.xml. Found on disk rather than
     * listed, so a new language is checked as soon as its file exists.
     */
    val translations: Map<String, FeedbackThreadStringFile> = resDirectory.listFiles().orEmpty()
        .filter { it.name.startsWith("values-") && File(it, "strings.xml").isFile }
        .sortedBy { it.name }
        .associate { directory ->
            val qualifier = directory.name.removePrefix("values-")
            qualifier to FeedbackThreadStringFile(File(directory, "strings.xml"), locale(qualifier))
        }

    /** The Italian file, for tests that check exact Italian copy. */
    val italian: FeedbackThreadStringFile get() = translations.getValue("it")

    /** The fields of the module's generated `R.string`: resource name to id. */
    val stringIds: Map<String, Int> = R.string::class.java.fields.associate { it.name to it.getInt(null) }

    /** The fields of the module's generated `R.plurals`: resource name to id. */
    val pluralIds: Map<String, Int> = R.plurals::class.java.fields.associate { it.name to it.getInt(null) }

    fun stringName(id: Int): String =
        stringIds.entries.singleOrNull { it.value == id }?.key ?: error("No R.string field has the id $id")

    /** The locale a `values-*` qualifier names: `it`, `pt-rBR`, or BCP 47 `b+sr+Latn`. */
    private fun locale(qualifier: String): Locale = Locale.forLanguageTag(
        if (qualifier.startsWith("b+")) qualifier.removePrefix("b+").replace('+', '-') else qualifier.replace("-r", "-"),
    )
}

internal class FeedbackThreadStringFile(file: File, private val locale: Locale) {
    /** Each `<string>`'s value as Android resolves it (escapes undone). */
    val strings: Map<String, String>

    /** Each `<string>`'s text exactly as written in the file. */
    val rawStrings: Map<String, String>

    /** Each `<plurals>`'s items, quantity to value (escapes undone). */
    val plurals: Map<String, Map<String, String>>

    /** Each `<plurals>`'s items, quantity to text exactly as written in the file. */
    val rawPlurals: Map<String, Map<String, String>>

    init {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val children = (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
        rawStrings = children.filter { it.tagName == "string" }.associate { it.getAttribute("name") to it.textContent }
        strings = rawStrings.mapValues { unescaped(it.value) }
        rawPlurals = children.filter { it.tagName == "plurals" }.associate { element ->
            val items = element.getElementsByTagName("item")
            element.getAttribute("name") to (0 until items.length).map { items.item(it) as Element }
                .associate { it.getAttribute("quantity") to it.textContent }
        }
        plurals = rawPlurals.mapValues { (_, items) -> items.mapValues { unescaped(it.value) } }
    }

    /** What [text] reads as in this file's language, formatted the way `getString(id, args)` formats it. */
    fun render(text: FeedbackThreadText): String = when (text) {
        is FeedbackThreadText.Verbatim -> text.value
        is FeedbackThreadText.Resource -> {
            val value = strings.getValue(FeedbackThreadStringResources.stringName(text.id))
            if (text.args.isEmpty()) value else String.format(locale, value, *text.args.toTypedArray())
        }
    }

    /** Undoes the backslash escapes of Android string resources (`\'`, `\"`, `\n`, `\t`, `\uXXXX`). */
    private fun unescaped(raw: String): String {
        val value = StringBuilder()
        var index = 0
        val text = raw.trim()
        while (index < text.length) {
            val character = text[index]
            if (character == '\\' && index + 1 < text.length) {
                when (val escaped = text[index + 1]) {
                    'n' -> value.append('\n')
                    't' -> value.append('\t')
                    'u' -> {
                        value.append(text.substring(index + 2, index + 6).toInt(16).toChar())
                        index += 4
                    }
                    else -> value.append(escaped)
                }
                index += 2
            } else {
                value.append(character)
                index += 1
            }
        }
        return value.toString()
    }
}
