package net.zodac.dicefive.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * No new player-visible string literal in `ui/`: they belong in `strings.xml`. Each file may hold at
 * most as many as the baseline says, and the baseline has to shrink as they move (Step 4 of
 * `.claude/I18N.md`), so a count that is too high *or* too low fails. A literal that must stay (a
 * mark like "3x") ends its line with `// i18n: not translated - <why>`.
 *
 * To regenerate the baseline: `./gradlew :app:shared:testAndroidHostTest --tests '*NoNewLiteralsTest*' -PregenerateI18nBaseline`.
 */
class NoNewLiteralsTest {

    private val uiRoot = File("src/commonMain/kotlin/net/zodac/dicefive/ui")
    private val baselineFile = File("src/androidHostTest/i18n-literal-baseline.txt")

    @Test
    fun `ui holds no more string literals than the baseline and no fewer`() {
        val found = scan(uiRoot)
        if (System.getProperty("dicefive.regenerateI18nBaseline") == "true") {
            baselineFile.writeText(found.entries.sortedBy { it.key }.joinToString("") { "${it.key}\t${it.value.size}\n" })
            println("Wrote ${found.values.sumOf { it.size }} literals in ${found.size} files to ${baselineFile.absolutePath}")
            return
        }
        assertTrue(baselineFile.isFile, "No baseline at ${baselineFile.absolutePath} - regenerate it, see the class doc")
        val baseline = baselineFile.readLines().filter { it.isNotBlank() }.associate { it.substringBefore('\t') to it.substringAfter('\t').toInt() }

        val problems = (found.keys + baseline.keys).sorted().mapNotNull { file ->
            val lines = found[file].orEmpty()
            val allowed = baseline[file] ?: 0
            when {
                lines.size > allowed -> "$file has ${lines.size} string literals, baseline $allowed - move the new ones into strings.xml " +
                    "(or end the line with '// i18n: not translated - <why>'): " + lines.joinToString { "${it.line}: ${it.text}" }
                lines.size < allowed -> "$file has ${lines.size} string literals, baseline $allowed - lower the baseline to ${lines.size}"
                else -> null
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `the scan finds visible text and skips the rest`() {
        val source = """
            Text("Hello")
            Text(
                "On the next line",
            )
            Text(text = "Named", modifier = Modifier)
            Text("${'$'}count")
            Text("3x") // i18n: not translated - a mark
            Icon(contentDescription = "Close", imageVector = X)
            onClick(label = "Roll") { }
            val key = "label"
            Modifier.semantics { stateDescription = "Locked" }
            DiceFiveDialog(title = stringResource(Res.string.a), message = "Sure?")
            Text("")
        """.trimIndent()

        val lines = scanSource(source).map { it.line }

        assertEquals(listOf(1, 3, 5, 8, 9, 11, 12), lines)
    }

    private data class Literal(val line: Int, val text: String)

    private fun scan(root: File): Map<String, List<Literal>> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }
            .associate { it.relativeTo(root).invariantSeparatorsPath to scanSource(it.readText()) }
            .filterValues { it.isNotEmpty() }

    private val templateParts = Regex("""\$\{[^}]*}|\$\w+""")

    private val argument = Regex(
        """(?:\bText\(\s*|\b(?:text|contentDescription|stateDescription|onClickLabel|label|title|message|headline|supporting|confirmLabel|dismissLabel|closeLabel|heading|body|subtitle|hint)\s*=\s*)"((?:[^"\\]|\\.)*)"""",
    )

    private fun scanSource(source: String): List<Literal> {
        val lines = source.lines()
        val starts = lines.runningFold(0) { offset, line -> offset + line.length + 1 }
        return argument.findAll(source).mapNotNull { match ->
            val line = starts.indexOfLast { it <= match.groups[1]!!.range.first } + 1
            val text = match.groupValues[1]
            val visible = text.replace(templateParts, "").any { it.isLetter() }
            if (!visible || "// i18n: not translated" in lines[line - 1]) null else Literal(line, text)
        }.toList()
    }
}
