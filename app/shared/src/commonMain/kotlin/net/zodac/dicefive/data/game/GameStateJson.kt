package net.zodac.dicefive.data.game

import net.zodac.dicefive.data.JsonArray
import net.zodac.dicefive.data.JsonBoolean
import net.zodac.dicefive.data.JsonNumber
import net.zodac.dicefive.data.JsonObject
import net.zodac.dicefive.data.JsonParseException
import net.zodac.dicefive.data.JsonString
import net.zodac.dicefive.data.JsonValue
import net.zodac.dicefive.data.buildJsonObject
import net.zodac.dicefive.data.parseJson
import net.zodac.dicefive.data.toJson
import net.zodac.dicefive.model.Die
import net.zodac.dicefive.model.DieColour
import net.zodac.dicefive.model.Difficulty
import net.zodac.dicefive.model.GameMode
import net.zodac.dicefive.model.GameState
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer

/** Hand-rolled (de)serialization for [GameState], used to persist an in-progress game across sessions. */
object GameStateJson {

    fun encode(state: GameState): String = buildJsonObject {
        put("gameMode", state.gameMode.id)
        put("turnTimer", state.turnTimer.name)
        put("currentPlayerIndex", state.currentPlayerIndex)
        put("rollsRemaining", state.rollsRemaining)
        put("turnRolls", state.turnRolls)
        // Left out when off, so a game with no roll modifier is saved exactly as it always was.
        state.rollModifiers.rollsPerTurn?.let { put("rollsPerTurnModifier", it) }
        if (state.rollModifiers.storedRolls) put("storedRolls", true)
        state.rollModifiers.storedRollsMax?.let { put("storedRollsMax", it) }
        put("phase", state.phase.name)
        put("isGameOver", state.isGameOver)
        put("turnSecondsLeft", state.turnSecondsLeft)
        put("dice", JsonArray(state.dice.map(::encodeDie)))
        put("players", JsonArray(state.players.map(::encodePlayer)))
    }.toJson()

    fun decode(json: String): GameState {
        val obj = parseJson(json) as? JsonObject ?: throw JsonParseException("Not a JSON object")
        val gameMode = decodeGameMode(obj)
        val rollModifiers = RollModifiers(
            rollsPerTurn = (obj["rollsPerTurnModifier"] as? JsonNumber)?.toInt(),
            storedRolls = "storedRolls" in obj && obj.getBoolean("storedRolls"),
            storedRollsMax = (obj["storedRollsMax"] as? JsonNumber)?.toInt(),
        )
        val rollsRemaining = obj.getInt("rollsRemaining")
        return GameState(
            gameMode = gameMode,
            turnTimer = TurnTimer.valueOf(obj.getString("turnTimer")),
            rollModifiers = rollModifiers,
            currentPlayerIndex = obj.getInt("currentPlayerIndex"),
            rollsRemaining = rollsRemaining,
            // Missing from a game saved before stored rolls: the turn started with the full allowance.
            turnRolls = if ("turnRolls" in obj) obj.getInt("turnRolls") else rollModifiers.rollsPerTurn ?: gameMode.rollsPerTurn,
            phase = TurnPhase.valueOf(obj.getString("phase")),
            isGameOver = obj.getBoolean("isGameOver"),
            turnSecondsLeft = (obj["turnSecondsLeft"] as? JsonNumber)?.toInt(),
            dice = obj.getObjectList("dice").map(::decodeDie),
            players = obj.getObjectList("players").map { decodePlayer(it, gameMode, rollModifiers.isActive) },
        )
    }

    /**
     * An id nothing recognises fails the decode rather than guessing, which
     * `InProgressGameRepository.load` treats as "nothing to resume" - as it does any save it can't read.
     */
    private fun decodeGameMode(obj: JsonObject): GameMode {
        val id = obj.getString("gameMode")
        return checkNotNull(GameMode.fromId(id)) { "Unknown game mode: $id" }
    }

    private fun encodeDie(die: Die): JsonObject = buildJsonObject {
        put("value", die.value)
        put("isHeld", die.isHeld)
        die.colour?.let { put("colour", it.name) }
        die.heldSlot?.let { put("heldSlot", it) }
    }

    private fun decodeDie(obj: JsonObject) = Die(
        value = obj.getInt("value"),
        isHeld = obj.getBoolean("isHeld"),
        colour = obj.optString("colour").takeIf { it.isNotEmpty() }?.let { DieColour.valueOf(it) },
        heldSlot = (obj["heldSlot"] as? JsonNumber)?.toInt(),
    )

    private fun encodePlayer(player: PlayerState): JsonObject = buildJsonObject {
        put("name", player.name)
        put("type", player.type.name)
        put("difficulty", player.difficulty.name)
        put("fiveOfAKindBonusCount", player.fiveOfAKindBonusCount)
        put("rollCount", player.rollCount)
        if (player.storedRolls > 0) put("storedRolls", player.storedRolls)
        player.lastRoll?.let { put("lastRoll", JsonArray(it.map(::encodeDie))) }
        player.lastScoredCategory?.let { put("lastScoredCategory", it.name) }
        put(
            "scorecard",
            buildJsonObject {
                // A list per box, its scores in the order they went in - see PlayerState.scorecard.
                for ((category, scores) in player.scorecard) {
                    put(category.name, JsonArray(scores.map { JsonNumber(it.toString()) }))
                }
            },
        )
    }

    private fun decodePlayer(obj: JsonObject, gameMode: GameMode, rollsModified: Boolean): PlayerState {
        val scorecardJson = obj.getObject("scorecard")
        val scorecard = gameMode.categories.associateWith { category -> decodeScores(scorecardJson[category.name]) }
        return PlayerState(
            name = obj.getString("name"),
            type = PlayerType.valueOf(obj.getString("type")),
            difficulty = Difficulty.valueOf(obj.getString("difficulty")),
            gameMode = gameMode,
            scorecard = scorecard,
            fiveOfAKindBonusCount = obj.getInt("fiveOfAKindBonusCount"),
            // Left out by encode for a player with no finished turn yet - no last roll to show.
            lastRoll = if ("lastRoll" in obj) obj.getObjectList("lastRoll").map(::decodeDie) else null,
            // Missing before the first turn ends, and from a game saved before it was kept: nothing
            // is highlighted as that player's last score until their next one.
            lastScoredCategory = obj.optString("lastScoredCategory").takeIf { it.isNotEmpty() }
                ?.let { name -> gameMode.categories.firstOrNull { it.name == name } },
            // Missing from a game saved before rolls were counted: it picks up from zero, which
            // can only keep the Flowerpot's sunflower from blooming that game, never hand it out.
            rollCount = if ("rollCount" in obj) obj.getInt("rollCount") else 0,
            storedRolls = if ("storedRolls" in obj) obj.getInt("storedRolls") else 0,
            rollsModified = rollsModified,
        )
    }

    /**
     * One box's scores: a list of them, or - from a game saved before a box could be scored more than
     * once - a single number for a filled box and null (or nothing) for an open one.
     */
    private fun decodeScores(value: JsonValue?): List<Int> = when (value) {
        is JsonArray -> value.items.map { (it as? JsonNumber)?.toInt() ?: throw JsonParseException("A score isn't a number") }
        is JsonNumber -> listOf(value.toInt())
        else -> emptyList()
    }

    private fun JsonObject.field(key: String): JsonValue = this[key] ?: throw JsonParseException("Missing \"$key\"")

    private fun JsonObject.getInt(key: String): Int =
        (field(key) as? JsonNumber)?.toInt() ?: throw JsonParseException("\"$key\" isn't a number")

    private fun JsonObject.getBoolean(key: String): Boolean =
        (field(key) as? JsonBoolean)?.value ?: throw JsonParseException("\"$key\" isn't a boolean")

    private fun JsonObject.getString(key: String): String =
        (field(key) as? JsonString)?.value ?: throw JsonParseException("\"$key\" isn't a string")

    /** The string at [key], or "" if it's absent or null. */
    private fun JsonObject.optString(key: String): String = (this[key] as? JsonString)?.value.orEmpty()

    private fun JsonObject.getObject(key: String): JsonObject =
        field(key) as? JsonObject ?: throw JsonParseException("\"$key\" isn't an object")

    private fun JsonObject.getObjectList(key: String): List<JsonObject> =
        (field(key) as? JsonArray ?: throw JsonParseException("\"$key\" isn't an array")).items.map {
            it as? JsonObject ?: throw JsonParseException("\"$key\" holds a non-object")
        }
}
