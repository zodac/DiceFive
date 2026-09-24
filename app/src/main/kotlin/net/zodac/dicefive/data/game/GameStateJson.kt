package net.zodac.dicefive.data.game

import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.GameType
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
        put("gameType", state.gameType.name)
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
        return GameState(
            gameType = GameType.valueOf(obj.getString("gameType")),
            // Absent from a game saved before this field existed - falls back to no timer rather
            // than failing to resume it.
            turnTimer = obj.optString("turnTimer").takeIf { it.isNotEmpty() }
                ?.let { runCatching { TurnTimer.valueOf(it) }.getOrNull() } ?: TurnTimer.NONE,
            currentPlayerIndex = obj.getInt("currentPlayerIndex"),
            rollsRemaining = obj.getInt("rollsRemaining"),
            phase = TurnPhase.valueOf(obj.getString("phase")),
            isGameOver = obj.getBoolean("isGameOver"),
            dice = obj.getJSONArray("dice").toObjectList().map(::decodeDie),
            players = obj.getJSONArray("players").toObjectList().map(::decodePlayer),
        )
    }

    private fun encodeDie(die: Die): JSONObject = JSONObject().apply {
        put("value", die.value)
        put("isHeld", die.isHeld)
    }

    private fun decodeDie(obj: JSONObject) = Die(value = obj.getInt("value"), isHeld = obj.getBoolean("isHeld"))

    private fun encodePlayer(player: PlayerState): JSONObject = JSONObject().apply {
        put("name", player.name)
        put("type", player.type.name)
        put("difficulty", player.difficulty.name)
        put("fiveOfAKindBonusCount", player.fiveOfAKindBonusCount)
        put(
            "scorecard",
            JSONObject().apply {
                for ((category, value) in player.scorecard) {
                    put(category.name, value ?: JSONObject.NULL)
                }
            },
        )
    }

    private fun decodePlayer(obj: JSONObject): PlayerState {
        val scorecardJson = obj.getJSONObject("scorecard")
        val scorecard = ScoreCategory.entries.associateWith { category ->
            if (scorecardJson.isNull(category.name)) null else scorecardJson.getInt(category.name)
        }
        return PlayerState(
            name = obj.getString("name"),
            type = PlayerType.valueOf(obj.getString("type")),
            difficulty = Difficulty.valueOf(obj.getString("difficulty")),
            scorecard = scorecard,
            fiveOfAKindBonusCount = obj.getInt("fiveOfAKindBonusCount"),
        )
    }

    private fun JSONArray.toObjectList(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
