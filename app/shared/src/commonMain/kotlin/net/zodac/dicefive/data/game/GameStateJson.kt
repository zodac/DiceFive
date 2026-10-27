package net.zodac.dicefive.data.game

import net.zodac.dicefive.data.JsonArray
import net.zodac.dicefive.data.JsonNumber
import net.zodac.dicefive.data.JsonObject
import net.zodac.dicefive.data.JsonParseException
import net.zodac.dicefive.data.JsonString
import net.zodac.dicefive.data.JsonValue
import net.zodac.dicefive.data.array
import net.zodac.dicefive.data.boolean
import net.zodac.dicefive.data.booleanOrNull
import net.zodac.dicefive.data.buildJsonObject
import net.zodac.dicefive.data.flag
import net.zodac.dicefive.data.int
import net.zodac.dicefive.data.intOrNull
import net.zodac.dicefive.data.ints
import net.zodac.dicefive.data.jsonInts
import net.zodac.dicefive.data.obj
import net.zodac.dicefive.data.objOrNull
import net.zodac.dicefive.data.objectList
import net.zodac.dicefive.data.parseJson
import net.zodac.dicefive.data.string
import net.zodac.dicefive.data.stringList
import net.zodac.dicefive.data.stringOrNull
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

    fun encode(state: GameState): String = encodeState(state).toJson()

    fun decode(json: String): GameState {
        val obj = parseJson(json) as? JsonObject ?: throw JsonParseException("Not a JSON object")
        return decodeState(obj)
    }

    private fun encodeState(state: GameState): JsonObject = buildJsonObject {
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
        state.undoSnapshot?.let { put("undoSnapshot", encodeState(it)) }
        put("dice", JsonArray(state.dice.map(::encodeDie)))
        put("players", JsonArray(state.players.map(::encodePlayer)))
    }

    private fun decodeState(obj: JsonObject): GameState {
        val gameMode = decodeGameMode(obj)
        val rollModifiers = RollModifiers(
            rollsPerTurn = obj.intOrNull("rollsPerTurnModifier"),
            storedRolls = obj.flag("storedRolls"),
            storedRollsMax = obj.intOrNull("storedRollsMax"),
        )
        val rollsRemaining = obj.int("rollsRemaining")
        val extendedScores = obj.flag("extendedScores")
        val disabledCategories = decodeDisabledCategories(obj, gameMode)
        val hitList = decodeHitList(obj, gameMode)
        return checkPlayable(
            GameState(
                gameMode = gameMode,
                turnTimer = TurnTimer.parse(obj.string("turnTimer")) ?: throw IllegalArgumentException("Unknown turn timer"),
                rollModifiers = rollModifiers,
                extendedScores = extendedScores,
                unluckyDice = decodeUnluckyDice(obj),
                disabledCategories = disabledCategories,
                hitList = hitList,
                currentPlayerIndex = obj.int("currentPlayerIndex"),
                rollsRemaining = rollsRemaining,
                turnRolls = obj.int("turnRolls"),
                phase = storedId<TurnPhase>(obj.string("phase")),
                isGameOver = obj.boolean("isGameOver"),
                turnSecondsLeft = obj.intOrNull("turnSecondsLeft"),
                // Left out by encode with nothing undone. One nothing recognises is dropped, not failed on: it only decides an achievement.
                undoneCategory = obj.stringOrNull("undoneCategory")?.takeIf { it.isNotEmpty() }?.let { storedIdOf<ScoreCategory>(it) },
                // Left out by encode with nothing to undo. One that can't be read is dropped, not failed on: it only costs the undo.
                undoSnapshot = obj.objOrNull("undoSnapshot")?.let { runCatching { decodeState(it) }.getOrNull() },
                dice = obj.objectList("dice").map(::decodeDie),
                players = obj.objectList("players").map { decodePlayer(it, gameMode, extendedScores, disabledCategories, hitList, rollModifiers.isActive) },
            ),
        )
    }

    /**
     * [state], or a failed decode - as a missing field is - for a game the board can't draw or play on: more seats than a
     * game has or none for the player whose turn it is, a hand of the wrong size, a die showing what the mode's dice
     * can't, a hold slot past the last, or rolls below none. This app never saves one, but a cloud save or a progress
     * file comes from somewhere else.
     */
    private fun checkPlayable(state: GameState): GameState {
        fun playable(holds: Boolean, what: String) {
            if (!holds) throw JsonParseException(what)
        }
        playable(state.players.size in GameState.MIN_PLAYERS..GameState.MAX_PLAYERS, "${state.players.size} players")
        playable(state.currentPlayerIndex in state.players.indices, "No player ${state.currentPlayerIndex} of ${state.players.size}")
        playable(state.dice.size == state.gameMode.diceCount, "${state.dice.size} dice in ${state.gameMode.id}")
        val dice = state.dice + state.players.flatMap { it.lastRoll.orEmpty() }
        playable(dice.all { it.value in state.gameMode.dieValues }, "A die the mode's dice can't show")
        playable(dice.all { die -> die.heldSlot == null || die.heldSlot in 0..<state.gameMode.scoringDiceCount }, "A hold slot past the last")
        playable(state.rollsRemaining >= 0 && state.turnRolls >= 0, "Rolls below none")
        return state
    }

    /**
     * An id nothing recognises fails the decode rather than guessing, which
     * `InProgressGameRepository.load` treats as "nothing to resume" - as it does any save it can't read.
     */
    private fun decodeGameMode(obj: JsonObject): GameMode {
        val id = obj.string("gameMode")
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
        val names = obj.stringList("disabledCategories")
        return names.map { name -> checkNotNull(storedIdOf<ScoreCategory>(name)) { "Unknown category: $name" } }.toSet()
    }

    /** Each target box's places, an any place written as 0, and its points. */
    private fun encodeHitList(hitList: Map<ScoreCategory, HitTarget>): JsonObject = buildJsonObject {
        for ((category, target) in hitList) {
            put(
                category.id,
                buildJsonObject {
                    put("places", jsonInts(target.places.map { it ?: ANY_PLACE }))
                    put("points", target.points)
                },
            )
        }
    }

    /** The targets, which a mode that draws them must have saved: without them there's no card to resume. */
    private fun decodeHitList(obj: JsonObject, gameMode: GameMode): Map<ScoreCategory, HitTarget> {
        if (!gameMode.hasHitList) return emptyMap()
        val hitList = obj.obj("hitList")
        return gameMode.categories.filter { it.isTarget }.associateWith { category ->
            val target = hitList.obj(category.id)
            val places = target.array("places").ints().map { value -> value.takeIf { it != ANY_PLACE } }
            HitTarget(places, target.int("points"))
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
        value = obj.int("value"),
        isHeld = obj.boolean("isHeld"),
        colour = obj.stringOrNull("colour")?.takeIf { it.isNotEmpty() }?.let { storedId<DieColour>(it) },
        heldSlot = obj.intOrNull("heldSlot"),
        isUnlucky = obj.flag("isUnlucky"),
        isGolden = obj.flag("isGolden"),
    )

    /** The Unlucky Dice modifier, or null when it was off - encode leaves its fields out. */
    private fun decodeUnluckyDice(obj: JsonObject): UnluckyDice? {
        val odds = obj.intOrNull("unluckyOdds") ?: return null
        val maxDice = obj.intOrNull("unluckyMaxDice") ?: return null
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
                    for ((category, slots) in player.jokerBonusSlots) put(category.id, jsonInts(slots.sorted()))
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
                    put(category.id, jsonInts(scores))
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
        val scorecardJson = obj.obj("scorecard")
        val categories = gameMode.categoriesWith(extendedScores)
        val scorecard = categories.associateWith { category -> decodeScores(scorecardJson[category.id]) }
        return PlayerState(
            name = obj.string("name"),
            type = storedId<PlayerType>(obj.string("type")),
            difficulty = storedId<Difficulty>(obj.string("difficulty")),
            colour = storedId<PlayerColour>(obj.string("colour")),
            gameMode = gameMode,
            extendedScores = extendedScores,
            disabledCategories = disabledCategories,
            hitList = hitList,
            scorecard = scorecard,
            fiveOfAKindBonusCount = obj.int("fiveOfAKindBonusCount"),
            // Left out by encode until a joker has scored.
            jokerBonusSlots = decodeJokerBonusSlots(obj.objOrNull("jokerBonusSlots"), categories),
            // Left out by encode for a player with no finished turn yet - no last roll to show.
            lastRoll = if ("lastRoll" in obj) obj.objectList("lastRoll").map(::decodeDie) else null,
            // Left out by encode before the player's first turn ends: nothing is highlighted yet.
            lastScoredCategory = obj.stringOrNull("lastScoredCategory")?.takeIf { it.isNotEmpty() }
                ?.let { id -> categories.firstOrNull { it.id == id } },
            rollCount = obj.int("rollCount"),
            storedRolls = if ("storedRolls" in obj) obj.int("storedRolls") else 0,
            // Left out by encode until the player's first hold.
            diceHeld = if ("diceHeld" in obj) obj.int("diceHeld") else 0,
            rollsModified = rollsModified,
            // Left out unless set: that player's flash is still to come.
            fiveOfAKindFlashed = obj.booleanOrNull("fiveOfAKindFlashed") == true,
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
        (value as? JsonArray ?: throw JsonParseException("A box's scores aren't an array")).ints()

    /** The [E] saved as [id] - failing the decode, as an unknown game mode does, for one nothing recognises. */
    private inline fun <reified E> storedId(id: String): E where E : Enum<E>, E : StoredId =
        storedIdOf<E>(id) ?: throw JsonParseException("Unknown ${E::class.simpleName}: $id")

    /** How an any place is written: no die shows a 0. */
    private const val ANY_PLACE = 0
}
