package net.zodac.dicefive.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LicenseReportTest {

    private val librariesJson = """
        {
          "libraries": [
            {"uniqueId": "a:zeta", "name": "Zeta", "artifactVersion": "1.0", "website": "https://zeta.example",
             "licenses": ["Apache-2.0"]},
            {"uniqueId": "a:alpha", "name": "alpha", "artifactVersion": "2.0", "description": "Not a notice",
             "licenses": ["Apache-2.0"]},
            {"uniqueId": "b:proto", "name": "Proto", "artifactVersion": "3.0",
             "description": "Copyright 2008 Example Inc.", "licenses": ["BSD-3-Clause"]},
            {"uniqueId": "font", "name": "Some Font", "tag": "font", "description": "Copyright Font Authors",
             "licenses": ["BSD-3-Clause"]},
            {"uniqueId": "s1", "name": "Tick", "tag": "sound", "licenses": ["CC0-1.0"]},
            {"uniqueId": "s2", "name": "Tock", "tag": "sound", "licenses": ["CC0-1.0"]}
          ],
          "licenses": {
            "Apache-2.0": {"name": "Apache License 2.0", "hash": "Apache-2.0", "content": "Apache text\n"},
            "BSD-3-Clause": {"name": "BSD 3-Clause", "hash": "BSD-3-Clause", "content": "BSD text"},
            "CC0-1.0": {"name": "CC0", "hash": "CC0-1.0", "content": "CC0 text"},
            "MIT": {"name": "MIT License", "hash": "MIT", "content": "MIT text"}
          }
        }
    """.trimIndent()

    private val report = parseLicenseReport(librariesJson, "{}")

    @Test
    fun `items are grouped by license - most-used first - alphabetical within - each kind counted - with copyright and website`() {
        assertEquals(listOf("Apache License 2.0", "BSD 3-Clause", "CC0"), report.groups.map { it.name })
        assertEquals(listOf("alpha", "Zeta"), report.groups[0].components.map { it.name })
        assertEquals("Apache text", report.groups[0].text)
        // A license nothing shipped uses is left out.
        assertFalse(report.groups.any { it.name == "MIT License" })

        // Only a description that is a copyright notice is shown as one.
        assertNull(report.groups[0].components.first { it.name == "alpha" }.copyright)
        assertEquals("Copyright 2008 Example Inc.", report.groups[1].components.first { it.name == "Proto" }.copyright)
        // The website is carried through for linking.
        assertEquals("https://zeta.example", report.groups[0].components.first { it.name == "Zeta" }.website)
        assertNull(report.groups[0].components.first { it.name == "alpha" }.website)

        // A licence counts each kind of item - not everything as a library.
        assertEquals(listOf(ComponentKind.LIBRARY to 2), report.groups[0].kindCounts)
        assertEquals(listOf(ComponentKind.LIBRARY to 1, ComponentKind.FONT to 1), report.groups[1].kindCounts)
        assertEquals(listOf(ComponentKind.SOUND to 2), report.groups[2].kindCounts)
        // One license shared by a library, a font, a sound and an image counts each kind.
        val mixed = parseLicenseReport(
            """
            {
              "libraries": [
                {"uniqueId": "l1", "name": "Lib One", "licenses": ["MIT"]},
                {"uniqueId": "l2", "name": "Lib Two", "licenses": ["MIT"]},
                {"uniqueId": "f", "name": "Font", "tag": "font", "licenses": ["MIT"]},
                {"uniqueId": "s", "name": "Sound", "tag": "sound", "licenses": ["MIT"]},
                {"uniqueId": "i", "name": "Image", "tag": "image", "licenses": ["MIT"]}
              ],
              "licenses": {"MIT": {"name": "MIT License", "hash": "MIT", "content": "MIT text"}}
            }
            """.trimIndent(),
            "{}",
        ).groups.single()
        assertEquals(5, mixed.components.size)
        assertEquals(listOf(ComponentKind.LIBRARY to 2, ComponentKind.FONT to 1, ComponentKind.SOUND to 1, ComponentKind.IMAGE to 1), mixed.kindCounts)
        // An untagged or unknown tag is a library.
        assertEquals(ComponentKind.LIBRARY, ComponentKind.fromTag(null))
        assertEquals(ComponentKind.LIBRARY, ComponentKind.fromTag("something-new"))
        assertEquals(ComponentKind.SOUND, ComponentKind.fromTag("sound"))
    }

    @Test
    fun `notices are read in library order - and urls become links without the punctuation that follows them`() {
        val withNotices = parseLicenseReport(librariesJson, """{"z:lib": "Z notice", "a:lib": "A notice"}""")
        assertEquals(listOf(ThirdPartyNotice("a:lib", "A notice"), ThirdPartyNotice("z:lib", "Z notice")), withNotices.notices)

        val text = "See https://freesound.org/s/140147/. Also (https://example.com/a) and http://x.org, done."
        val linked = linkifyUrls(text)
        assertEquals(text, linked.text)
        assertEquals(listOf("https://freesound.org/s/140147/", "https://example.com/a", "http://x.org"), linked.getStringAnnotations(URL_TAG, 0, linked.length).map { it.item })
    }
}
