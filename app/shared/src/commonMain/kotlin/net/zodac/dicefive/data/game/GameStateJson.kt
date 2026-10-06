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
import net.zodac.dicefive.model.HitTarget
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice

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
        if (state.extendedScores) put("extendedScores", true)
        state.unluckyDice?.let {
            put("unluckyOdds", it.oddsPercent)
            put("unluckyMaxDice", it.maxDice)
        }
        // Only a mode that switches boxes off at random has any - see GameMode.drawDisabledCategories.
        if (state.gameMode.randomDisabledCategories > 0) put("disabledCategories", JsonArray(state.disabledCategories.map { JsonString(it.name) }))
        // Only a mode with targets draws any - see GameMode.drawHitList.
        if (state.gameMode.hasHitList) put("hitList", encodeHitList(state.hitList))
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
        val extendedScores = "extendedScores" in obj && obj.getBoolean("extendedScores")
        val disabledCategories = decodeDisabledCategories(obj, gameMode)
        val hitList = decodeHitList(obj, gameMode)
        return GameState(
            gameMode = gameMode,
            turnTimer = TurnTimer.valueOf(obj.getString("turnTimer")),
            rollModifiers = rollModifiers,
            extendedScores = extendedScores,
            unluckyDice = decodeUnluckyDice(obj),
            disabledCategories = disabledCategories,
            hitList = hitList,
            currentPlayerIndex = obj.getInt("currentPlayerIndex"),
            rollsRemaining = rollsRemaining,
            // Missing from a game saved before stored rolls: the turn started with the full allowance.
            turnRolls = if ("turnRolls" in obj) obj.getInt("turnRolls") else rollModifiers.rollsPerTurn ?: gameMode.rollsPerTurn,
            phase = TurnPhase.valueOf(obj.getString("phase")),
            isGameOver = obj.getBoolean("isGameOver"),
            turnSecondsLeft = (obj["turnSecondsLeft"] as? JsonNumber)?.toInt(),
            dice = obj.getObjectList("dice").map(::decodeDie),
            players = obj.getObjectList("players").map { decodePlayer(it, gameMode, extendedScores, disabledCategories, hitList, rollModifiers.isActive) },
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

    /**
     * The boxes switched off, which a mode that draws some at random must have saved: a game saved without them
     * (Quickfire's old one-roll rules shared its id) has no card to resume, so it fails the decode like an
     * unknown mode, and `InProgressGameRepository.load` treats it as nothing to resume.
     */
    private fun decodeDisabledCategories(obj: JsonObject, gameMode: GameMode): Set<ScoreCategory> {
        if (gameMode.randomDisabledCategories == 0) return gameMode.disabledCategories
        if ("disabledCategories" !in obj) throw JsonParseException("A ${gameMode.displayName} game saved without its disabled boxes")
        val names = obj.getStringList("disabledCategories")
        return names.map { name -> checkNotNull(ScoreCategory.entries.firstOrNull { it.name == name }) { "Unknown category: $name" } }.toSet()
    }

    /** Each target box's places, an any place written as 0, and its points. */
    private fun encodeHitList(hitList: Map<ScoreCategory, HitTarget>): JsonObject = buildJsonObject {
        for ((category, target) in hitList) {
            put(
                category.name,
                buildJsonObject {
                    put("places", JsonArray(target.places.map { JsonNumber((it ?: ANY_PLACE).toString()) }))
                    put("points", target.points)
                },
            )
        }
    }

    /** The targets, which a mode that draws them must have saved: without them there's no card to resume. */
    private fun decodeHitList(obj: JsonObject, gameMode: GameMode): Map<ScoreCategory, HitTarget> {
        if (!gameMode.hasHitList) return emptyMap()
        val hitList = obj.getObject("hitList")
        return gameMode.categories.filter { it.isTarget }.associateWith { category ->
            val target = hitList.getObject(category.name)
            val places = (target.field("places") as? JsonArray ?: throw JsonParseException("A target's places aren't an array")).items.map { item ->
                val value = (item as? JsonNumber)?.toInt() ?: throw JsonParseException("A place isn't a number")
                value.takeIf { it != ANY_PLACE }
            }
            HitTarget(places, target.getInt("points"))
        }
    }

    private fun encodeDie(die: Die): JsonObject = buildJsonObject {
        put("value", die.value)
        put("isHeld", die.isHeld)
        die.colour?.let { put("colour", it.name) }
        die.heldSlot?.let { put("heldSlot", it) }
        if (die.isUnlucky) put("isUnlucky", true)
    }

    private fun decodeDie(obj: JsonObject) = Die(
        value = obj.getInt("value"),
        isHeld = obj.getBoolean("isHeld"),
        colour = obj.optString("colour").takeIf { it.isNotEmpty() }?.let { DieColour.valueOf(it) },
        heldSlot = (obj["heldSlot"] as? JsonNumber)?.toInt(),
        isUnlucky = "isUnlucky" in obj && obj.getBoolean("isUnlucky"),
    )

    /** The Unlucky Dice modifier, or null when the save has none - as every game saved before it existed. */
    private fun decodeUnluckyDice(obj: JsonObject): UnluckyDice? {
        val odds = (obj["unluckyOdds"] as? JsonNumber)?.toInt() ?: return null
        val maxDice = (obj["unluckyMaxDice"] as? JsonNumber)?.toInt() ?: return null
        return UnluckyDice(odds, maxDice)
    }

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

    private fun decodePlayer(
        obj: JsonObject,
        gameMode: GameMode,
        extendedScores: Boolean,
        disabledCategories: Set<ScoreCategory>,
        hitList: Map<ScoreCategory, HitTarget>,
        rollsModified: Boolean,
    ): PlayerState {
        val scorecardJson = obj.getObject("scorecard")
        val categories = gameMode.categoriesWith(extendedScores)
        val scorecard = categories.associateWith { category -> decodeScores(scorecardJson[category.name]) }
        return PlayerState(
            name = obj.getString("name"),
            type = PlayerType.valueOf(obj.getString("type")),
            difficulty = Difficulty.valueOf(obj.getString("difficulty")),
            gameMode = gameMode,
            extendedScores = extendedScores,
            disabledCategories = disabledCategories,
            hitList = hitList,
            scorecard = scorecard,
            fiveOfAKindBonusCount = obj.getInt("fiveOfAKindBonusCount"),
            // Left out by encode for a player with no finished turn yet - no last roll to show.
            lastRoll = if ("lastRoll" in obj) obj.getObjectList("lastRoll").map(::decodeDie) else null,
            // Missing before the first turn ends, and from a game saved before it was kept: nothing
            // is highlighted as that player's last score until their next one.
            lastScoredCategory = obj.optString("lastScoredCategory").takeIf { it.isNotEmpty() }
                ?.let { name -> categories.firstOrNull { it.name == name } },
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

    /** How an any place is written: no die shows a 0. */
    private const val ANY_PLACE = 0

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

    private fun JsonObject.getStringList(key: String): List<String> =
        (field(key) as? JsonArray ?: throw JsonParseException("\"$key\" isn't an array")).items.map {
            (it as? JsonString)?.value ?: throw JsonParseException("\"$key\" holds a non-string")
        }

    private fun JsonObject.getObjectList(key: String): List<JsonObject> =
        (field(key) as? JsonArray ?: throw JsonParseException("\"$key\" isn't an array")).items.map {
            it as? JsonObject ?: throw JsonParseException("\"$key\" holds a non-object")
        }
}
