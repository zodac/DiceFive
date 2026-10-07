package net.zodac.dicefive.i18n

/** The rules a translation file has to keep to, as functions over parsed entries so they can be tested on hand-made input. */
internal object StringChecks {

    private val argument = Regex("""%\d+\$[sd]""")

    /** Plural entries are keyed `name[quantity]`. */
    private fun pluralName(key: String) = key.substringBefore('[')

    /** Problems with [translation] measured against [base]: extra keys, changed arguments. Empty if none. */
    fun translationProblems(locale: String, base: Map<String, String>, translation: Map<String, String>): List<String> {
        val problems = mutableListOf<String>()
        val baseNames = base.keys.map(::pluralName).toSet()
        for ((key, text) in translation) {
            if (pluralName(key) !in baseNames) {
                problems += "$locale: '$key' isn't in the base strings"
                continue
            }
            // A plural form the base doesn't have (Russian "few") is fine: compare against any base form of that plural.
            val baseText = base[key] ?: base.entries.firstOrNull { pluralName(it.key) == pluralName(key) }?.value ?: continue
            val missing = argument.findAll(baseText).map { it.value }.toSet() - argument.findAll(text).map { it.value }.toSet()
            if (missing.isNotEmpty()) problems += "$locale: '$key' is missing $missing from the base string"
        }
        return problems
    }

    /** Every base plural must have an `other` item. */
    fun pluralsWithoutOther(base: Map<String, String>): List<String> {
        val names = base.keys.filter { '[' in it }.map(::pluralName).toSet()
        return names.filter { "$it[other]" !in base }.sorted().map { "plural '$it' has no 'other' item" }
    }

    private val banned = listOf(
        // The one permitted copy of the trademark outside .claude/*.md besides AchievementEngineTest's guard; lower case, as there.
        Regex("yahtzee", RegexOption.IGNORE_CASE),
        Regex("(three|four|five)[ -]of[ -]a[ -]kind", RegexOption.IGNORE_CASE),
    )

    /** Entries whose text contains a banned word (see CLAUDE.md's working agreements). */
    fun bannedWords(strings: Map<String, String>): List<String> =
        strings.filter { (_, text) -> banned.any { it.containsMatchIn(text) } }.keys.sorted()
}
