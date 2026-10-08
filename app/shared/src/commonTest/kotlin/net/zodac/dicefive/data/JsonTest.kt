package net.zodac.dicefive.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonTest {

    @Test
    fun `every kind of value round trips - strings exactly - keeping field order and each number's written form`() {
        val value = buildJsonObject {
            put("string", "text")
            put("int", 42)
            put("negative", -7)
            put("nothing", null)
            put("yes", true)
            put("no", false)
            put("list", JsonArray(listOf(JsonNumber(1), JsonString("two"), JsonNull, JsonArray(emptyList()))))
            put("nested", buildJsonObject { put("inner", "value") })
            put("empty", JsonObject(emptyMap()))
        }
        assertEquals(value, parseJson(value.toJson()))

        // Strings that need escaping round trip exactly - control characters escaped and nothing else.
        val tricky = "quote \" backslash \\ slash / newline \n return \r tab \t bell \u0007 nul \u0000 é 🎲 'single'"
        assertEquals(tricky, (parseJson(JsonString(tricky).toJson()) as JsonString).value)
        assertEquals("\"a\\nb\\u0001c é 🎲\"", JsonString("a\nb\u0001c é 🎲").toJson())
        // Escapes written by other writers are read - unicode and surrogate pairs included.
        assertEquals("é 🎲 / \b\u000C", (parseJson("\"\\u00e9 \\ud83c\\udfb2 \\/ \\b\\f\"") as JsonString).value)

        // Whitespace between tokens is ignored and field order is kept.
        val parsed = parseJson(" {\n\t\"b\" : 1 ,\r\n \"a\" : [ true , null ] } ") as JsonObject
        assertEquals(listOf("b", "a"), parsed.fields.keys.toList())
        assertEquals("""{"b":1,"a":[true,null]}""", parsed.toJson())

        // Numbers keep their written form.
        val numbers = parseJson("[0, -12, 3.25, 1e10, -2.5E-3]") as JsonArray
        assertEquals(listOf("0", "-12", "3.25", "1e10", "-2.5E-3"), numbers.items.map { (it as JsonNumber).text })
        assertEquals(-12, (numbers.items[1] as JsonNumber).toInt())
    }

    @Test
    fun `malformed documents are rejected - and a non-integer number is not read as one`() {
        for (bad in listOf(
            "", "{", "[1,]", "{\"a\":1,}", "{\"a\" 1}", "{a:1}", "\"unterminated", "\"bad \\x escape\"",
            "\"raw\nnewline\"", "01", "1.", "-", "tru", "nul", "[1] 2", "{\"a\":1}}",
        )) {
            assertFailsWith<JsonParseException>("should reject: $bad") { parseJson(bad) }
        }
        assertFailsWith<JsonParseException> { JsonNumber("2.5").toInt() }
    }
}
