package net.zodac.dicefive.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt
import net.zodac.dicefive.game.nowEpochMillis
import net.zodac.dicefive.platform.Accelerometer
import net.zodac.dicefive.platform.LocalPlatformServices

/** How far above gravity a single accelerometer sample has to swing, in m/s², to count as one beat
 * of a shake - low enough that a firm real shake clears it comfortably, high enough that setting
 * the phone down or a single bump doesn't. */
private const val SHAKE_THRESHOLD = 10f

/** How long a burst of [SHAKE_MIN_BEATS] beats has to span to count as a deliberate shake rather
 * than a single knock - at least 500ms of real motion, any direction. */
private const val SHAKE_WINDOW_MILLIS = 500L

/** How many *rising edges* (see [ShakeDetector.onSample]) have to land inside
 * [SHAKE_WINDOW_MILLIS]. A real shake reverses direction repeatedly - each reversal is its own
 * rising edge - where one swing in a single direction is only ever one, however long the sensor
 * keeps reporting it above the threshold along the way. */
private const val SHAKE_MIN_BEATS = 3

/** How slowly gravity is assumed to change, sample to sample, for the low-pass filter that
 * separates it from the phone's own motion - see [ShakeDetector.onSample]. */
private const val GRAVITY_FILTER_ALPHA = 0.8f

/** Once a shake fires, how long to ignore the sensor for - long enough that the roll it triggered
 * (and the cup's own shake animation) finishes before another shake could be detected off the
 * same motion. */
private const val SHAKE_COOLDOWN_MILLIS = 1_500L

/**
 * Detects a real phone shake from accelerometer samples. Gravity is removed with a simple low-pass
 * filter - it changes far more slowly than a shake's own motion, so subtracting a slowly-tracked
 * running average of the raw reading leaves just the device's own acceleration - and [onShake]
 * fires once at least [SHAKE_MIN_BEATS] rising edges (the signal crossing up through the threshold,
 * having first dropped back below it) land within [SHAKE_WINDOW_MILLIS] of each other.
 *
 * Pure logic, fed by the platform's [Accelerometer] via [ShakeDetectorEffect], which ties it to
 * the host screen's own resumed state rather than the composition's - so the same tuning applies
 * on every platform, and it can be unit tested with made-up samples.
 */
internal class ShakeDetector(private val onShake: () -> Unit) {

    private val gravity = FloatArray(3)
    private val beatTimestamps = ArrayDeque<Long>()
    private var cooldownUntilMillis = 0L
    // Whether the most recent sample was already above the threshold - a beat is only counted on
    // the transition into it (see onSample), so one continuous swing in a single direction,
    // however many samples the sensor reports along the way, is still only ever one beat.
    private var aboveThreshold = false

    /** Forgets everything seen so far - called whenever the sensor stops, so a later start begins fresh. */
    fun reset() {
        gravity.fill(0f)
        beatTimestamps.clear()
        aboveThreshold = false
    }

    /** One raw reading, gravity included, in m/s², taken at [nowMillis]. */
    fun onSample(x: Float, y: Float, z: Float, nowMillis: Long) {
        if (nowMillis < cooldownUntilMillis) return

        val raw = floatArrayOf(x, y, z)
        for (i in 0..2) {
            gravity[i] = GRAVITY_FILTER_ALPHA * gravity[i] + (1 - GRAVITY_FILTER_ALPHA) * raw[i]
        }
        val dx = raw[0] - gravity[0]
        val dy = raw[1] - gravity[1]
        val dz = raw[2] - gravity[2]
        val magnitude = sqrt(dx * dx + dy * dy + dz * dz)

        val wasAboveThreshold = aboveThreshold
        aboveThreshold = magnitude >= SHAKE_THRESHOLD
        // Only a rising edge counts as a beat - see the class doc and SHAKE_MIN_BEATS. Requiring
        // the signal to have dropped back below the threshold first is what actually demands the
        // phone change direction, rather than just staying in motion.
        if (!aboveThreshold || wasAboveThreshold) return

        beatTimestamps.addLast(nowMillis)
        while (beatTimestamps.isNotEmpty() && nowMillis - beatTimestamps.first() > SHAKE_WINDOW_MILLIS) {
            beatTimestamps.removeFirst()
        }
        if (beatTimestamps.size >= SHAKE_MIN_BEATS) {
            beatTimestamps.clear()
            cooldownUntilMillis = nowMillis + SHAKE_COOLDOWN_MILLIS
            onShake()
        }
    }
}

/**
 * Wires a [ShakeDetector] to this screen's own lifecycle: registered only while it's actually
 * resumed - a backgrounded game shouldn't roll dice from a shake happening in someone's pocket -
 * and unregistered on pause or on leaving the composition entirely, the same register/unregister
 * shape [rememberSoundEffects] uses for its sound player, just tied to
 * [Lifecycle.Event.ON_RESUME]/[Lifecycle.Event.ON_PAUSE] instead of the composition's own start
 * and end, since a live sensor listener firing while the app isn't in front is pure waste (and,
 * worse here, could roll dice nobody's looking at).
 *
 * [onShake] is read through [rememberUpdatedState] so a caller can pass a fresh lambda every
 * recomposition (as [GameScreen] does, closing over the latest game state) without tearing down
 * and re-registering the sensor listener each time.
 */
@Composable
fun ShakeDetectorEffect(onShake: () -> Unit) {
    val platform = LocalPlatformServices.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnShake = rememberUpdatedState(onShake)
    val accelerometer = remember { platform.createAccelerometer() } ?: return
    val detector = remember { ShakeDetector { currentOnShake.value() } }

    DisposableEffect(lifecycleOwner, accelerometer) {
        fun stop() {
            accelerometer.stop()
            detector.reset()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> accelerometer.start { x, y, z -> detector.onSample(x, y, z, nowEpochMillis()) }
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
}
