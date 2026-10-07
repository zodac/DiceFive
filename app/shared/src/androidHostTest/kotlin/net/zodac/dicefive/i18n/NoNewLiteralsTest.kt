package net.zodac.dicefive.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * No player-visible string literal in `ui/`: they belong in `strings.xml`. A literal that has to stay (a mark like
 * "3x", an animation label) ends its line with `// i18n: not translated - <why>`. The scan is a few regexes over
 * the common arguments of text - `text =`, `Text("`, `contentDescription =` and so on - not a parser, so a new
 * kind of call that takes text is worth adding to [argument] when it turns up. See `.claude/I18N.md`.
 */
class NoNewLiteralsTest {

    private val uiRoot = File("src/commonMain/kotlin/net/zodac/dicefive/ui")

    @Test
    fun `ui holds no string literal that is shown or spoken`() {
        val problems = scan(uiRoot).map { (file, lines) ->
            "$file: " + lines.joinToString { "${it.line}: ${it.text}" }
        }

        assertEquals(
            emptyList(),
            problems,
            "Move these into strings.xml, or end the line with '// i18n: not translated - <why>' if it must stay",
        )
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
