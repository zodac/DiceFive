package net.zodac.dicefive.data

/**
 * A minimal JSON reader and writer, for the app's own documents: the saved in-progress game
 * (`GameStateJson`), the cloud save (`CloudSaveCodec`) and the licence dialog's collected NOTICE files. Hand-written rather than
 * kotlinx.serialization, which cost ~49KB of the release APK for these uses - more than every
 * other shared-code dependency - while this is a few hundred bytes. It follows RFC 8259 strictly
 * (anything malformed throws [JsonParseException]) but has no streaming, no schema and no
 * reflection: callers walk the [JsonValue] tree themselves.
 */
internal sealed interface JsonValue

internal data object JsonNull : JsonValue

internal data class JsonBoolean(val value: Boolean) : JsonValue

/** Kept as the literal text it was written as, so an integer never passes through a lossy Double. */
internal data class JsonNumber(val text: String) : JsonValue {
    constructor(value: Int) : this(value.toString())

    constructor(value: Long) : this(value.toString())

    fun toInt(): Int = text.toIntOrNull() ?: throw JsonParseException("Not an integer: $text")

    fun toLong(): Long = text.toLongOrNull() ?: throw JsonParseException("Not an integer: $text")
}

internal data class JsonString(val value: String) : JsonValue

internal data class JsonArray(val items: List<JsonValue>) : JsonValue

/** Fields in insertion order, which is also the order [toJson] writes them in. */
internal data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue {
    operator fun get(key: String): JsonValue? = fields[key]

    operator fun contains(key: String): Boolean = key in fields
}

internal class JsonParseException(message: String) : IllegalArgumentException(message)

/** Builds a [JsonObject] field by field, in order - `buildJsonObject { put("name", value) }`. */
internal fun buildJsonObject(block: JsonObjectBuilder.() -> Unit): JsonObject = JsonObjectBuilder().apply(block).build()

internal class JsonObjectBuilder {
    private val fields = LinkedHashMap<String, JsonValue>()

    fun put(key: String, value: JsonValue) {
        fields[key] = value
    }

    fun put(key: String, value: String) = put(key, JsonString(value))

    fun put(key: String, value: Boolean) = put(key, JsonBoolean(value))

    /** A null [value] is written as JSON `null`. */
    fun put(key: String, value: Int?) = put(key, value?.let(::JsonNumber) ?: JsonNull)

    fun put(key: String, value: Long) = put(key, JsonNumber(value))

    fun build(): JsonObject = JsonObject(fields)
}

// Reading a document's fields. A required field that's missing or of the wrong type fails the read with a
// JsonParseException naming it; an optional one (`...OrNull`) reads as null.

/** The value at [key]. */
internal fun JsonObject.field(key: String): JsonValue = this[key] ?: throw JsonParseException("Missing \"$key\"")

internal fun JsonObject.int(key: String): Int = (field(key) as? JsonNumber)?.toInt() ?: throw JsonParseException("\"$key\" isn't a number")

internal fun JsonObject.long(key: String): Long = (field(key) as? JsonNumber)?.toLong() ?: throw JsonParseException("\"$key\" isn't a number")

internal fun JsonObject.boolean(key: String): Boolean = (field(key) as? JsonBoolean)?.value ?: throw JsonParseException("\"$key\" isn't a boolean")

internal fun JsonObject.string(key: String): String = (field(key) as? JsonString)?.value ?: throw JsonParseException("\"$key\" isn't a string")

internal fun JsonObject.obj(key: String): JsonObject = field(key) as? JsonObject ?: throw JsonParseException("\"$key\" isn't an object")

internal fun JsonObject.array(key: String): JsonArray = field(key) as? JsonArray ?: throw JsonParseException("\"$key\" isn't an array")

internal fun JsonObject.stringList(key: String): List<String> =
    array(key).items.map { (it as? JsonString)?.value ?: throw JsonParseException("\"$key\" holds a non-string") }

internal fun JsonObject.objectList(key: String): List<JsonObject> =
    array(key).items.map { it as? JsonObject ?: throw JsonParseException("\"$key\" holds a non-object") }

/** The number at [key], or null if there's none - absent, or not a number. */
internal fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonNumber)?.toInt()

/** The number at [key], or null if there's none - absent, or not a number. */
internal fun JsonObject.longOrNull(key: String): Long? = (this[key] as? JsonNumber)?.toLong()

/** The boolean at [key], or null if there's none - absent, or not a boolean. */
internal fun JsonObject.booleanOrNull(key: String): Boolean? = (this[key] as? JsonBoolean)?.value

/** The string at [key], or null if there's none - absent, or not a string. */
internal fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonString)?.value

/** The object at [key], or null if there's none - absent, or not an object. */
internal fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject

/** The boolean at [key] if it's there - which must be a boolean - or false: for a flag written only when set. */
internal fun JsonObject.flag(key: String): Boolean = key in this && boolean(key)

/** Every field, each a number. */
internal fun JsonObject.ints(): Map<String, Int> =
    fields.mapValues { (key, value) -> (value as? JsonNumber)?.toInt() ?: throw JsonParseException("\"$key\" isn't a number") }

/** Every field, each a number. */
internal fun JsonObject.longs(): Map<String, Long> =
    fields.mapValues { (key, value) -> (value as? JsonNumber)?.toLong() ?: throw JsonParseException("\"$key\" isn't a number") }

/** Every item, each a number. */
internal fun JsonArray.ints(): List<Int> = items.map { (it as? JsonNumber)?.toInt() ?: throw JsonParseException("An item isn't a number") }

/** [values] as an object of numbers, by key. */
internal fun jsonInts(values: Map<String, Int>): JsonObject = JsonObject(values.mapValues { JsonNumber(it.value) })

/** [values] as an object of numbers, by key. */
internal fun jsonLongs(values: Map<String, Long>): JsonObject = JsonObject(values.mapValues { JsonNumber(it.value) })

/** [values] as an array of numbers. */
internal fun jsonInts(values: List<Int>): JsonArray = JsonArray(values.map(::JsonNumber))

/** Parses one complete JSON document - nothing but whitespace may follow it. */
internal fun parseJson(text: String): JsonValue = JsonParser(text).parseDocument()

/** Compact JSON: no whitespace between tokens. */
internal fun JsonValue.toJson(): String = StringBuilder().also { writeTo(it) }.toString()

private fun JsonValue.writeTo(out: StringBuilder) {
    when (this) {
        JsonNull -> out.append("null")
        is JsonBoolean -> out.append(value)
        is JsonNumber -> out.append(text)
        is JsonString -> writeString(value, out)
        is JsonArray -> {
            out.append('[')
            items.forEachIndexed { index, item ->
                if (index > 0) out.append(',')
                item.writeTo(out)
            }
            out.append(']')
        }
        is JsonObject -> {
            out.append('{')
            fields.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) out.append(',')
                writeString(key, out)
                out.append(':')
                value.writeTo(out)
            }
            out.append('}')
        }
    }
}

private fun writeString(value: String, out: StringBuilder) {
    out.append('"')
    for (char in value) {
        when (char) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            // Every other control character has to be escaped too; anything else - accents, emoji
            // (as their surrogate pairs) - is written as-is.
            in '\u0000'..'\u001F' -> out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> out.append(char)
        }
    }
    out.append('"')
}

private class JsonParser(private val text: String) {

    private var index = 0

    fun parseDocument(): JsonValue {
        val value = parseValue()
        skipWhitespace()
        if (index != text.length) fail("Unexpected '${text[index]}' after the document")
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (index >= text.length) fail("Unexpected end of input")
        return when (val char = text[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonString(parseString())
            't' -> literal("true", JsonBoolean(true))
            'f' -> literal("false", JsonBoolean(false))
            'n' -> literal("null", JsonNull)
            else -> if (char == '-' || char in '0'..'9') parseNumber() else fail("Unexpected '$char'")
        }
    }

    private fun parseObject(): JsonObject {
        index++ // {
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (peek() == '}') {
            index++
            return JsonObject(fields)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("Expected a field name")
            val key = parseString()
            skipWhitespace()
            expect(':')
            fields[key] = parseValue()
            skipWhitespace()
            when (next()) {
                ',' -> continue
                '}' -> return JsonObject(fields)
                else -> fail("Expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): JsonArray {
        index++ // [
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (peek() == ']') {
            index++
            return JsonArray(items)
        }
        while (true) {
            items += parseValue()
            skipWhitespace()
            when (next()) {
                ',' -> continue
                ']' -> return JsonArray(items)
                else -> fail("Expected ',' or ']'")
            }
        }
    }

    private fun parseString(): String {
        index++ // opening quote
        val out = StringBuilder()
        while (true) {
            val char = next() ?: fail("Unterminated string")
            when {
                char == '"' -> return out.toString()
                char == '\\' -> out.append(parseEscape())
                char < ' ' -> fail("Unescaped control character in a string")
                else -> out.append(char)
            }
        }
    }

    private fun parseEscape(): Char = when (val char = next()) {
        '"' -> '"'
        '\\' -> '\\'
        '/' -> '/'
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        // A surrogate pair arrives as two consecutive escapes, each decoded to its own half here.
        'u' -> {
            if (index + 4 > text.length) fail("Truncated \\u escape")
            val code = text.substring(index, index + 4).toIntOrNull(16) ?: fail("Bad \\u escape")
            index += 4
            code.toChar()
        }
        else -> fail("Bad escape '\\$char'")
    }

    private fun parseNumber(): JsonNumber {
        val start = index
        if (peek() == '-') index++
        when {
            peek() == '0' -> index++
            peek() in '1'..'9' -> skipDigits()
            else -> fail("Bad number")
        }
        if (peek() == '.') {
            index++
            if (peek() !in '0'..'9') fail("Bad number")
            skipDigits()
        }
        if (peek() == 'e' || peek() == 'E') {
            index++
            if (peek() == '+' || peek() == '-') index++
            if (peek() !in '0'..'9') fail("Bad number")
            skipDigits()
        }
        return JsonNumber(text.substring(start, index))
    }

    private fun skipDigits() {
        while (peek() in '0'..'9') index++
    }

    private fun literal(word: String, value: JsonValue): JsonValue {
        if (!text.startsWith(word, index)) fail("Unexpected '${text[index]}'")
        index += word.length
        return value
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index] in " \t\n\r") index++
    }

    private fun expect(char: Char) {
        if (next() != char) fail("Expected '$char'")
    }

    private fun peek(): Char? = text.getOrNull(index)

    private fun next(): Char? = text.getOrNull(index)?.also { index++ }

    private fun fail(message: String): Nothing = throw JsonParseException("$message at offset $index")
}
