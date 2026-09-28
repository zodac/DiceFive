package net.zodac.dicefive.data.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer

/** Hand-rolled (de)serialization for [GameState], used to persist an in-progress game across sessions. */
object GameStateJson {

    fun encode(state: GameState): String = buildJsonObject {
        put("gameMode", state.gameMode.id)
        put("turnTimer", state.turnTimer.name)
        put("currentPlayerIndex", state.currentPlayerIndex)
        put("rollsRemaining", state.rollsRemaining)
        put("phase", state.phase.name)
        put("isGameOver", state.isGameOver)
        put("dice", JsonArray(state.dice.map(::encodeDie)))
        put("players", JsonArray(state.players.map(::encodePlayer)))
    }.toString()

    fun decode(json: String): GameState {
        val obj = Json.parseToJsonElement(json).jsonObject
        val gameMode = decodeGameMode(obj)
        return GameState(
            gameMode = gameMode,
            // Absent from a game saved before this field existed - falls back to no timer rather
            // than failing to resume it.
            turnTimer = obj.optString("turnTimer").takeIf { it.isNotEmpty() }
                ?.let { runCatching { TurnTimer.valueOf(it) }.getOrNull() } ?: TurnTimer.NONE,
            currentPlayerIndex = obj.getInt("currentPlayerIndex"),
            rollsRemaining = obj.getInt("rollsRemaining"),
            phase = TurnPhase.valueOf(obj.getString("phase")),
            isGameOver = obj.getBoolean("isGameOver"),
            dice = obj.getObjectList("dice").map(::decodeDie),
            players = obj.getObjectList("players").map { decodePlayer(it, gameMode) },
        )
    }

    /**
     * A game saved before modes existed has no "gameMode", only "gameType" - and the only value it
     * could ever hold there was "CLASSIC", today's [GameMode.STANDARD]. An id nothing recognises
     * fails the decode rather than guessing, which `InProgressGameRepository.load` already treats as
     * "nothing to resume".
     */
    private fun decodeGameMode(obj: JsonObject): GameMode {
        if ("gameMode" !in obj) {
            check(obj.optString("gameType") == LEGACY_CLASSIC_GAME_TYPE) { "Unknown game type" }
            return GameMode.STANDARD
        }
        val id = obj.getString("gameMode")
        return checkNotNull(GameMode.fromId(id)) { "Unknown game mode: $id" }
    }

    private fun encodeDie(die: Die): JsonObject = buildJsonObject {
        put("value", die.value)
        put("isHeld", die.isHeld)
        die.colour?.let { put("colour", it.name) }
    }

    private fun decodeDie(obj: JsonObject) = Die(
        value = obj.getInt("value"),
        isHeld = obj.getBoolean("isHeld"),
        colour = obj.optString("colour").takeIf { it.isNotEmpty() }?.let { DieColour.valueOf(it) },
    )

    private fun encodePlayer(player: PlayerState): JsonObject = buildJsonObject {
        put("name", player.name)
        put("type", player.type.name)
        put("difficulty", player.difficulty.name)
        put("fiveOfAKindBonusCount", player.fiveOfAKindBonusCount)
        player.lastRoll?.let { put("lastRoll", JsonArray(it.map(::encodeDie))) }
        put(
            "scorecard",
            buildJsonObject {
                for ((category, value) in player.scorecard) {
                    put(category.name, value)
                }
            },
        )
    }

    private fun decodePlayer(obj: JsonObject, gameMode: GameMode): PlayerState {
        val scorecardJson = obj.getValue("scorecard").jsonObject
        val scorecard = gameMode.categories.associateWith { category ->
            scorecardJson[category.name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.int
        }
        return PlayerState(
            name = obj.getString("name"),
            type = PlayerType.valueOf(obj.getString("type")),
            difficulty = Difficulty.valueOf(obj.getString("difficulty")),
            gameMode = gameMode,
            scorecard = scorecard,
            fiveOfAKindBonusCount = obj.getInt("fiveOfAKindBonusCount"),
            // Absent from a game saved before this field existed, or from a player with no
            // finished turn yet - either way, null (no last roll to show) rather than failing to
            // resume it.
            lastRoll = if ("lastRoll" in obj) obj.getObjectList("lastRoll").map(::decodeDie) else null,
        )
    }

    private const val LEGACY_CLASSIC_GAME_TYPE = "CLASSIC"

    private fun JsonObject.getInt(key: String): Int = getValue(key).jsonPrimitive.int

    private fun JsonObject.getBoolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean

    private fun JsonObject.getString(key: String): String = getValue(key).jsonPrimitive.content

    /** The string at [key], or "" if it's absent or null - org.json's `optString`, which this replaced. */
    private fun JsonObject.optString(key: String): String =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content.orEmpty()

    private fun JsonObject.getObjectList(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }
}
