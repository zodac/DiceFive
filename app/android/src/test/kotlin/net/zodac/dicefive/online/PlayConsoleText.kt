package net.zodac.dicefive.online

import java.io.File

// What the Play Console exports share: the app's languages and their text, and where (and whether) to write.

/** The app's languages: [folder] holds the text, and [playCodes] are the Play Console translations it fills (none for English, the default). */
internal enum class Locale(val code: String, val folder: String, val playCodes: List<String>) {
    ENGLISH("en", "values", emptyList()),
    SPANISH("es", "values-es", listOf("es-ES", "es-419")),
    ARABIC("ar", "values-ar", listOf("ar")),
}

internal val LOCALES = Locale.entries

/** Whether to write the exports: with `-PexportPlayConsole`, which `scripts/export-play-console.sh` passes. */
internal val exportingPlayConsole: Boolean get() = System.getProperty("dicefive.exportPlayConsole").toBoolean()

/** The gitignored `assets/play-console/` folder at the repo root, where the exports go. */
internal fun playConsoleDir(): File = File(System.getProperty("dicefive.playConsoleDir").orEmpty().ifBlank { "../../assets/play-console" })

private val stringEntry = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

/** A locale's plain strings, unescaped as Android reads them. Their banned words are StringChecks' to catch. */
internal fun stringsOf(locale: Locale): Map<String, String> =
    stringEntry.findAll(File("../shared/src/commonMain/composeResources/${locale.folder}/strings.xml").readText())
        .associate { it.groupValues[1] to unescape(it.groupValues[2]) }

private fun unescape(text: String): String = text
    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")

/** [value] as a CSV field, quoted only where it must be. */
internal fun csv(value: String) = if (value.any { it == ',' || it == '"' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value

/** [rows] as CSV lines, each ending in a newline. */
internal fun csvLines(rows: List<List<String>>): String = rows.joinToString("\n", postfix = "\n") { row -> row.joinToString(",") { csv(it) } }
