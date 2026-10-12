package net.zodac.dicefive.model

/**
 * A bonus of [amount] for a section's boxes adding up to [threshold] or more - D20's two halves each have one (see
 * [GameMode.sectionBonuses]). Standard's upper bonus isn't one: it has its own fields, which scale with the boxes
 * switched off.
 */
data class SectionBonus(val section: ScoreSection, val threshold: Int, val amount: Int)
