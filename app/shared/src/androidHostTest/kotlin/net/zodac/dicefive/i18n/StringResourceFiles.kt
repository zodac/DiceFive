package net.zodac.dicefive.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Reads the string resources from disk, for the i18n guards. `:app:shared`'s host tests are plain
 * JVM, so they can't resolve a resource through `Res`; the files are read directly instead (the
 * working directory is the module). See `.claude/I18N.md`, "Traps already hit".
 */
internal object StringResourceFiles {

    private val resources = File("src/commonMain/composeResources")

    /** Locale folder name (`values`, `values-fr`...) to the file's entries, for every locale present. */
    fun all(): Map<String, Map<String, String>> =
        resources.listFiles { file -> file.isDirectory && (file.name == "values" || file.name.startsWith("values-")) }
            .orEmpty()
            .sortedBy { it.name }
            .map { it.name to File(it, "strings.xml") }
            .filter { it.second.isFile }
            .associate { (locale, file) -> locale to parse(file.readText()) }

    /**
     * Every `<string>` as `name -> text`, and every `<plurals>` item as `name[quantity] -> text`, so
     * a guard can treat both alike. Text is as written in the file (XML entities resolved).
     */
    fun parse(xml: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream()).documentElement
        val entries = linkedMapOf<String, String>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val element = children.item(i) as? Element ?: continue
            when (element.tagName) {
                "string" -> entries[element.getAttribute("name")] = element.textContent
                "plurals" -> {
                    val items = element.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        entries["${element.getAttribute("name")}[${item.getAttribute("quantity")}]"] = item.textContent
                    }
                }
            }
        }
        return entries
    }
}
