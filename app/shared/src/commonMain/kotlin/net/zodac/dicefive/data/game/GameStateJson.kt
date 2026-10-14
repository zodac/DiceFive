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
import net.zodac.dicefive.model.PlayerColour
import net.zodac.dicefive.model.PlayerState
import net.zodac.dicefive.model.PlayerType
import net.zodac.dicefive.model.RollModifiers
import net.zodac.dicefive.model.ScoreCategory
import net.zodac.dicefive.model.StoredId
import net.zodac.dicefive.model.TurnPhase
import net.zodac.dicefive.model.TurnTimer
import net.zodac.dicefive.model.UnluckyDice
import net.zodac.dicefive.model.storedIdOf

/** Hand-rolled (de)serialization for [GameState], used to persist an in-progress game across sessions. */
object GameStateJson {

    fun encode(state: GameState): String = buildJsonObject {
        put("gameMode", state.gameMode.id)
        put("turnTimer", state.turnTimer.name)
        put("currentPlayerIndex", state.currentPlayerIndex)
        put("rollsRemaining", state.rollsRemaining)
        put("turnRolls", state.turnRolls)
        // Each modifier's fields are left out when it's off.
        state.rollModifiers.rollsPerTurn?.let { put("rollsPerTurnModifier", it) }
        if (state.rollModifiers.storedRolls) put("storedRolls", true)
        state.rollModifiers.storedRollsMax?.let { put("storedRollsMax", it) }
        if (state.extendedScores) put("extendedScores", true)
        state.unluckyDice?.let {
            put("unluckyOdds", it.oddsPercent)
            put("unluckyMaxDice", it.maxDice)
        }
        // Only a mode that switches boxes off at random has any - see GameMode.drawDisabledCategories.
        if (state.gameMode.randomDisabledCategories > 0) put("disabledCategories", JsonArray(state.disabledCategories.map { JsonString(it.id) }))
        // Only a mode with targets draws any - see GameMode.drawHitList.
        if (state.gameMode.hasHitList) put("hitList", encodeHitList(state.hitList))
        put("phase", state.phase.id)
        put("isGameOver", state.isGameOver)
        put("turnSecondsLeft", state.turnSecondsLeft)
        state.undoneCategory?.let { put("undoneCategory", it.id) }
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
            turnTimer = TurnTimer.parse(obj.getString("turnTimer")) ?: throw IllegalArgumentException("Unknown turn timer"),
            rollModifiers = rollModifiers,
            extendedScores = extendedScores,
            unluckyDice = decodeUnluckyDice(obj),
            disabledCategories = disabledCategories,
            hitList = hitList,
            currentPlayerIndex = obj.getInt("currentPlayerIndex"),
            rollsRemaining = rollsRemaining,
            turnRolls = obj.getInt("turnRolls"),
            phase = storedId<TurnPhase>(obj.getString("phase")),
            isGameOver = obj.getBoolean("isGameOver"),
            turnSecondsLeft = (obj["turnSecondsLeft"] as? JsonNumber)?.toInt(),
            // Left out by encode with nothing undone. One nothing recognises is dropped, not failed on: it only decides an achievement.
            undoneCategory = obj.optString("undoneCategory").takeIf { it.isNotEmpty() }?.let { storedIdOf<ScoreCategory>(it) },
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
     * The boxes switched off, which a mode that draws some at random must have saved: without them there's no
     * card to resume, so it fails the decode like an unknown mode, and `InProgressGameRepository.load` treats it
     * as nothing to resume.
     */
    private fun decodeDisabledCategories(obj: JsonObject, gameMode: GameMode): Set<ScoreCategory> {
        if (gameMode.randomDisabledCategories == 0) return gameMode.disabledCategories
        if ("disabledCategories" !in obj) throw JsonParseException("A ${gameMode.id} game saved without its disabled boxes")
        val names = obj.getStringList("disabledCategories")
        return names.map { name -> checkNotNull(storedIdOf<ScoreCategory>(name)) { "Unknown category: $name" } }.toSet()
    }

    /** Each target box's places, an any place written as 0, and its points. */
    private fun encodeHitList(hitList: Map<ScoreCategory, HitTarget>): JsonObject = buildJsonObject {
        for ((category, target) in hitList) {
            put(
                category.id,
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
            val target = hitList.getObject(category.id)
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
        die.colour?.let { put("colour", it.id) }
        die.heldSlot?.let { put("heldSlot", it) }
        if (die.isUnlucky) put("isUnlucky", true)
        if (die.isGolden) put("isGolden", true)
    }

    private fun decodeDie(obj: JsonObject) = Die(
        value = obj.getInt("value"),
        isHeld = obj.getBoolean("isHeld"),
        colour = obj.optString("colour").takeIf { it.isNotEmpty() }?.let { storedId<DieColour>(it) },
        heldSlot = (obj["heldSlot"] as? JsonNumber)?.toInt(),
        isUnlucky = "isUnlucky" in obj && obj.getBoolean("isUnlucky"),
        isGolden = "isGolden" in obj && obj.getBoolean("isGolden"),
    )

    /** The Unlucky Dice modifier, or null when it was off - encode leaves its fields out. */
    private fun decodeUnluckyDice(obj: JsonObject): UnluckyDice? {
        val odds = (obj["unluckyOdds"] as? JsonNumber)?.toInt() ?: return null
        val maxDice = (obj["unluckyMaxDice"] as? JsonNumber)?.toInt() ?: return null
        return UnluckyDice(odds, maxDice)
    }

    private fun encodePlayer(player: PlayerState): JsonObject = buildJsonObject {
        put("name", player.name)
        put("type", player.type.id)
        put("difficulty", player.difficulty.id)
        put("colour", player.colour.id)
        put("fiveOfAKindBonusCount", player.fiveOfAKindBonusCount)
        if (player.jokerBonusSlots.isNotEmpty()) {
            put(
                "jokerBonusSlots",
                buildJsonObject {
                    for ((category, slots) in player.jokerBonusSlots) put(category.id, JsonArray(slots.sorted().map { JsonNumber(it.toString()) }))
                },
            )
        }
        put("rollCount", player.rollCount)
        if (player.storedRolls > 0) put("storedRolls", player.storedRolls)
        if (player.diceHeld > 0) put("diceHeld", player.diceHeld)
        if (player.fiveOfAKindFlashed) put("fiveOfAKindFlashed", true)
        player.lastRoll?.let { put("lastRoll", JsonArray(it.map(::encodeDie))) }
        player.lastScoredCategory?.let { put("lastScoredCategory", it.id) }
        put(
            "scorecard",
            buildJsonObject {
                // A list per box, its scores in the order they went in - see PlayerState.scorecard.
                for ((category, scores) in player.scorecard) {
                    put(category.id, JsonArray(scores.map { JsonNumber(it.toString()) }))
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
        val scorecard = categories.associateWith { category -> decodeScores(scorecardJson[category.id]) }
        return PlayerState(
            name = obj.getString("name"),
            type = storedId<PlayerType>(obj.getString("type")),
            difficulty = storedId<Difficulty>(obj.getString("difficulty")),
            colour = storedId<PlayerColour>(obj.getString("colour")),
            gameMode = gameMode,
            extendedScores = extendedScores,
            disabledCategories = disabledCategories,
            hitList = hitList,
            scorecard = scorecard,
            fiveOfAKindBonusCount = obj.getInt("fiveOfAKindBonusCount"),
            // Left out by encode until a joker has scored; also absent from a game saved before the chips were tied to a box.
            jokerBonusSlots = decodeJokerBonusSlots(obj["jokerBonusSlots"] as? JsonObject, categories),
            // Left out by encode for a player with no finished turn yet - no last roll to show.
            lastRoll = if ("lastRoll" in obj) obj.getObjectList("lastRoll").map(::decodeDie) else null,
            // Left out by encode before the player's first turn ends: nothing is highlighted yet.
            lastScoredCategory = obj.optString("lastScoredCategory").takeIf { it.isNotEmpty() }
                ?.let { id -> categories.firstOrNull { it.id == id } },
            rollCount = obj.getInt("rollCount"),
            storedRolls = if ("storedRolls" in obj) obj.getInt("storedRolls") else 0,
            // Left out until the player's first hold, and by a save from before it was kept.
            diceHeld = if ("diceHeld" in obj) obj.getInt("diceHeld") else 0,
            rollsModified = rollsModified,
            // Left out unless set: that player's flash is still to come.
            fiveOfAKindFlashed = (obj["fiveOfAKindFlashed"] as? JsonBoolean)?.value == true,
        )
    }

    private fun decodeJokerBonusSlots(obj: JsonObject?, categories: List<ScoreCategory>): Map<ScoreCategory, Set<Int>> {
        if (obj == null) return emptyMap()
        return categories.mapNotNull { category ->
            val slots = (obj[category.id] as? JsonArray)?.items?.mapNotNull { (it as? JsonNumber)?.toInt() }?.toSet()
            slots?.takeIf { it.isNotEmpty() }?.let { category to it }
        }.toMap()
    }

    /** One box's scores, in the order they went in. */
    private fun decodeScores(value: JsonValue?): List<Int> =
        (value as? JsonArray ?: throw JsonParseException("A box's scores aren't an array")).items.map {
            (it as? JsonNumber)?.toInt() ?: throw JsonParseException("A score isn't a number")
        }

    /** The [E] saved as [id] - failing the decode, as an unknown game mode does, for one nothing recognises. */
    private inline fun <reified E> storedId(id: String): E where E : Enum<E>, E : StoredId =
        storedIdOf<E>(id) ?: throw JsonParseException("Unknown ${E::class.simpleName}: $id")

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
