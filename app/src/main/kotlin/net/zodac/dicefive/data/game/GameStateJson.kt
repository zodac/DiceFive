package net.zodac.dicefive.data.game

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
import org.json.JSONArray
import org.json.JSONObject

/** Hand-rolled (de)serialization for [GameState], used to persist an in-progress game across sessions. */
object GameStateJson {

    fun encode(state: GameState): String = JSONObject().apply {
        put("gameMode", state.gameMode.id)
        put("turnTimer", state.turnTimer.name)
        put("currentPlayerIndex", state.currentPlayerIndex)
        put("rollsRemaining", state.rollsRemaining)
        put("phase", state.phase.name)
        put("isGameOver", state.isGameOver)
        put("dice", JSONArray(state.dice.map(::encodeDie)))
        put("players", JSONArray(state.players.map(::encodePlayer)))
    }.toString()

    fun decode(json: String): GameState {
        val obj = JSONObject(json)
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
            dice = obj.getJSONArray("dice").toObjectList().map(::decodeDie),
            players = obj.getJSONArray("players").toObjectList().map { decodePlayer(it, gameMode) },
        )
    }

    /**
     * A game saved before modes existed has no "gameMode", only "gameType" - and the only value it
     * could ever hold there was "CLASSIC", today's [GameMode.STANDARD]. An id nothing recognises
     * fails the decode rather than guessing, which `InProgressGameRepository.load` already treats as
     * "nothing to resume".
     */
    private fun decodeGameMode(obj: JSONObject): GameMode {
        if (!obj.has("gameMode")) {
            check(obj.optString("gameType") == LEGACY_CLASSIC_GAME_TYPE) { "Unknown game type" }
            return GameMode.STANDARD
        }
        val id = obj.getString("gameMode")
        return checkNotNull(GameMode.fromId(id)) { "Unknown game mode: $id" }
    }

    private fun encodeDie(die: Die): JSONObject = JSONObject().apply {
        put("value", die.value)
        put("isHeld", die.isHeld)
        die.colour?.let { put("colour", it.name) }
    }

    private fun decodeDie(obj: JSONObject) = Die(
        value = obj.getInt("value"),
        isHeld = obj.getBoolean("isHeld"),
        colour = obj.optString("colour").takeIf { it.isNotEmpty() }?.let { DieColour.valueOf(it) },
    )

    private fun encodePlayer(player: PlayerState): JSONObject = JSONObject().apply {
        put("name", player.name)
        put("type", player.type.name)
        put("difficulty", player.difficulty.name)
        put("fiveOfAKindBonusCount", player.fiveOfAKindBonusCount)
        player.lastRoll?.let { put("lastRoll", JSONArray(it.map(::encodeDie))) }
        put(
            "scorecard",
            JSONObject().apply {
                for ((category, value) in player.scorecard) {
                    put(category.name, value ?: JSONObject.NULL)
                }
            },
        )
    }

    private fun decodePlayer(obj: JSONObject, gameMode: GameMode): PlayerState {
        val scorecardJson = obj.getJSONObject("scorecard")
        val scorecard = gameMode.categories.associateWith { category ->
            if (scorecardJson.isNull(category.name)) null else scorecardJson.getInt(category.name)
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
            lastRoll = if (obj.has("lastRoll")) obj.getJSONArray("lastRoll").toObjectList().map(::decodeDie) else null,
        )
    }

    private const val LEGACY_CLASSIC_GAME_TYPE = "CLASSIC"

    private fun JSONArray.toObjectList(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
