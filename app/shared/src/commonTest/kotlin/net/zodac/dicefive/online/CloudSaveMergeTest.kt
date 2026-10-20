package net.zodac.dicefive.online

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.dicefive.data.Stamped
import net.zodac.dicefive.data.achievements.CloudAchievements
import net.zodac.dicefive.data.scores.CloudScores
import net.zodac.dicefive.data.scores.ScoreEntry
import net.zodac.dicefive.data.settings.SettingValue

/** A game's row in the save, at [at], by [name]. */
internal fun cloudRow(uuid: String, at: Long, name: String = "Ann", score: Int = 200) = ScoreEntry(
    playerName = name,
    score = score,
    timestampEpochMillis = at,
    won = null,
    isPrimaryPlayer = true,
    fiveOfAKindCount = 0,
    zeroedCategoryCount = 0,
    upperSectionTotal = 0,
    chanceScore = 0,
    threeOfAKindScore = 0,
    fourOfAKindScore = 0,
    uuid = uuid,
)

class CloudSaveMergeTest {

    /** [CloudSaveMerge.merge] both ways round, checked to agree - the merge mustn't depend on which copy is whose. */
    private fun merge(a: CloudSave, b: CloudSave): CloudSave {
        val ab = CloudSaveMerge.merge(a, b)
        assertEquals(ab, CloudSaveMerge.merge(b, a))
        // Merging either copy in again changes nothing.
        assertEquals(ab, CloudSaveMerge.merge(ab, a))
        return ab
    }

    @Test
    fun `unlocks are a union - each at its earliest time`() {
        val merged = merge(
            CloudSave(achievements = CloudAchievements(unlockedAt = mapOf("A" to 10L, "B" to 50L))),
            CloudSave(achievements = CloudAchievements(unlockedAt = mapOf("B" to 30L, "C" to 70L))),
        )
        assertEquals(mapOf("A" to 10L, "B" to 30L, "C" to 70L), merged.achievements.unlockedAt)
    }

    @Test
    fun `summed counters keep each device's share - the larger copy of a share is the newer`() {
        // Phone played 5 games and the tablet 3; the cloud's copy of the phone's share is older, at 4.
        val phone = CloudSave(achievements = CloudAchievements(deviceCounters = mapOf("phone" to mapOf("GAMES_PLAYED" to 5))))
        val cloud = CloudSave(
            achievements = CloudAchievements(
                deviceCounters = mapOf("phone" to mapOf("GAMES_PLAYED" to 4), "tablet" to mapOf("GAMES_PLAYED" to 3, "DICE_ROLLED" to 90)),
            ),
        )
        val merged = merge(phone, cloud).achievements.deviceCounters
        assertEquals(mapOf("phone" to mapOf("GAMES_PLAYED" to 5), "tablet" to mapOf("GAMES_PLAYED" to 3, "DICE_ROLLED" to 90)), merged)
        // So the total is 8 games, not 5 + 4 + 3.
        assertEquals(8, merged.values.sumOf { it["GAMES_PLAYED"] ?: 0 })
    }

    @Test
    fun `flag counters are a union of their flags`() {
        val merged = merge(
            CloudSave(achievements = CloudAchievements(flagCounters = mapOf("FIVE_X_NUMBERS" to 0b000011))),
            CloudSave(achievements = CloudAchievements(flagCounters = mapOf("FIVE_X_NUMBERS" to 0b100010))),
        )
        assertEquals(mapOf("FIVE_X_NUMBERS" to 0b100011), merged.achievements.flagCounters)
    }

    @Test
    fun `the streak comes from the device that finished a game last - even when it is lower`() {
        val merged = merge(
            CloudSave(achievements = CloudAchievements(latestCounters = mapOf("WIN_STREAK" to Stamped(7, 100L)))),
            CloudSave(achievements = CloudAchievements(latestCounters = mapOf("WIN_STREAK" to Stamped(0, 200L)))),
        )
        assertEquals(mapOf("WIN_STREAK" to Stamped(0, 200L)), merged.achievements.latestCounters)
    }

    @Test
    fun `scores are a union by uuid - in the order played`() {
        val merged = merge(
            CloudSave(scores = CloudScores(rows = listOf(cloudRow("b", at = 20), cloudRow("a", at = 10)))),
            CloudSave(scores = CloudScores(rows = listOf(cloudRow("c", at = 30), cloudRow("a", at = 10)))),
        )
        assertEquals(listOf("a", "b", "c"), merged.scores.rows.map { it.uuid })
    }

    @Test
    fun `a leaderboard reset drops every score and dismissal from before it - whichever copy made it`() {
        val reset = CloudSave(scoresResetAtEpochMillis = 25, scores = CloudScores(rows = listOf(cloudRow("new", at = 40))))
        val older = CloudSave(
            scores = CloudScores(rows = listOf(cloudRow("old", at = 10), cloudRow("after", at = 30)), dismissedAt = mapOf("Bob" to 20L)),
        )
        val merged = merge(reset, older)
        assertEquals(25, merged.scoresResetAtEpochMillis)
        assertEquals(listOf("after", "new"), merged.scores.rows.map { it.uuid })
        assertEquals(emptyMap(), merged.scores.dismissedAt)
    }

    @Test
    fun `a dismissal stands until a later game under the name - played on any device - in any case`() {
        val merged = merge(
            CloudSave(scores = CloudScores(dismissedAt = mapOf("Bob" to 50L, "Cat" to 50L, "ÉLISE" to 50L))),
            CloudSave(
                scores = CloudScores(
                    // Case in any alphabet: "élise" is "ÉLISE".
                    rows = listOf(cloudRow("1", at = 60, name = "bob"), cloudRow("2", at = 40, name = "Cat"), cloudRow("3", at = 70, name = "élise")),
                    dismissedAt = mapOf("Cat" to 45L),
                ),
            ),
        )
        assertEquals(mapOf("Cat" to 50L), merged.scores.dismissedAt)
    }

    @Test
    fun `each setting is whichever was set last - an unset one too`() {
        val merged = merge(
            CloudSave(
                settings = mapOf(
                    "dice_style_id" to Stamped(SettingValue.Text("gold"), 10L),
                    "player_count" to Stamped(SettingValue.Number(3), 50L),
                    "rolls_per_turn_modifier" to Stamped(SettingValue.Number(4), 10L),
                ),
            ),
            CloudSave(
                settings = mapOf(
                    "dice_style_id" to Stamped(SettingValue.Text("ocean"), 20L),
                    "player_count" to Stamped(SettingValue.Number(2), 40L),
                    "rolls_per_turn_modifier" to Stamped(null, 30L),
                    "extended_scores_modifier" to Stamped(SettingValue.Flag(true), 5L),
                ),
            ),
        )
        assertEquals(
            mapOf(
                "dice_style_id" to Stamped(SettingValue.Text("ocean"), 20L),
                "player_count" to Stamped(SettingValue.Number(3), 50L),
                "rolls_per_turn_modifier" to Stamped<SettingValue?>(null, 30L),
                "extended_scores_modifier" to Stamped(SettingValue.Flag(true), 5L),
            ),
            merged.settings,
        )
    }

    @Test
    fun `the newest game in progress wins - a finished one's clearing beats an older copy`() {
        val playing = CloudSave(inProgressGame = Stamped("{\"game\":1}", 100L))
        val finished = CloudSave(inProgressGame = Stamped(null, 200L))
        assertEquals(Stamped<String?>(null, 200L), merge(playing, finished).inProgressGame)
        val newer = CloudSave(inProgressGame = Stamped("{\"game\":2}", 300L))
        assertEquals(Stamped<String?>("{\"game\":2}", 300L), merge(finished, newer).inProgressGame)
    }

    @Test
    fun `what this build doesn't know - an achievement - a counter - a setting - is carried through`() {
        val newer = CloudSave(
            achievements = CloudAchievements(unlockedAt = mapOf("FUTURE_ACHIEVEMENT" to 5L), flagCounters = mapOf("FUTURE_FLAGS" to 1)),
            settings = mapOf("future_setting" to Stamped(SettingValue.Flag(true), 5L)),
        )
        val merged = merge(CloudSave(), newer)
        assertEquals(newer.achievements, merged.achievements)
        assertEquals(newer.settings, merged.settings)
    }

    @Test
    fun `an unstamped setting beats one never chosen - but not a later one`() {
        val unstamped = CloudSave(settings = mapOf("dice_style_id" to Stamped(SettingValue.Text("ocean"), 0L)))
        val neverChosen = CloudSave(settings = mapOf("dice_style_id" to Stamped<SettingValue?>(null, 0L)))
        assertEquals(unstamped.settings, merge(unstamped, neverChosen).settings)
        val later = CloudSave(settings = mapOf("dice_style_id" to Stamped(SettingValue.Text("gold"), 5L)))
        assertEquals(later.settings, merge(unstamped, later).settings)
    }
}
