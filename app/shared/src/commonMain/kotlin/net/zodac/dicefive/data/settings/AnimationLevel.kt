package net.zodac.dicefive.data.settings

/**
 * The player's "Animations" setting: how much of the app moves, and how fast it's drawn. A step down trades
 * smoothness and decoration for less work a frame (an older phone, a longer battery), never what the game does.
 *
 * - [ambientMotion]: decoration that moves on its own while nothing is happening - the menu's drifting dice, a
 *   background's floating dice or twinkling stars, a cup's steam or bubbles, the Neon, RGB and Glitch art, sparkles,
 *   the googly dice's pupils. Off, each holds the still pose it has under reduced motion. These are what keep a
 *   frame drawn every refresh of an idle screen, so they're the first thing to go.
 * - [gameplayMotion]: everything that answers what's happening - the cup's shake and pour, the dice's toss, banners,
 *   page fades, score count-ups, fireworks. Off only at [OFF], which is the app's reduced motion (`LocalReduceMotion`).
 * - [scorePulse]: whether a scoring tile's gold glow pulses or is steady.
 * - [maxFramesPerSecond]: the frame rate asked for, or null for whatever the screen does (90 or 120Hz on many phones).
 *
 * Saved by name. [HIGH] is the default; a player who had the old "Remove animations" switch on starts at [OFF].
 */
enum class AnimationLevel(
    val ambientMotion: Boolean,
    val gameplayMotion: Boolean,
    val scorePulse: Boolean,
    val maxFramesPerSecond: Int?,
) {
    HIGH(ambientMotion = true, gameplayMotion = true, scorePulse = true, maxFramesPerSecond = null),
    MEDIUM(ambientMotion = false, gameplayMotion = true, scorePulse = true, maxFramesPerSecond = 60),
    LOW(ambientMotion = false, gameplayMotion = true, scorePulse = false, maxFramesPerSecond = 30),
    OFF(ambientMotion = false, gameplayMotion = false, scorePulse = false, maxFramesPerSecond = 30),
    ;

    companion object {
        val default = HIGH
    }
}
