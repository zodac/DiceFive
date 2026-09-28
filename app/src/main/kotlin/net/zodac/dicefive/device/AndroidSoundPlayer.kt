package net.zodac.dicefive.device

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import net.zodac.dicefive.R
import net.zodac.dicefive.platform.SoundEffect
import net.zodac.dicefive.platform.SoundPlayer

/**
 * [SoundPlayer] on [SoundPool]: built for exactly this kind of one-shot clip, unlike
 * [android.media.MediaPlayer] which carries far more setup overhead. Every clip is loaded once per
 * instance, from the build's peak-normalised copies in res/raw (see NormalizeOggAudioTask in
 * app/build.gradle.kts).
 */
internal class AndroidSoundPlayer(context: Context) : SoundPlayer {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val soundIds: Map<SoundEffect, Int> = SoundEffect.entries.associateWith { effect ->
        val resId = when (effect) {
            SoundEffect.CUP_SHAKE -> R.raw.cup_shake
            SoundEffect.MAT_LANDING -> R.raw.mat_landing
            SoundEffect.HOLD -> R.raw.hold
            SoundEffect.UNHOLD -> R.raw.unhold
            SoundEffect.CELEBRATION -> R.raw.celebration
        }
        pool.load(context, resId, 1)
    }

    // pool.load() above only kicks off an async decode - it does NOT mean the clip is ready to
    // play yet, and pool.play() on a sample that isn't decoded yet is a silent no-op (no sound,
    // no exception). That race is invisible for the short SFX, which never fire until well after
    // this pool was constructed (a roll needs a tap; a hold needs the dice on the felt) - but
    // celebration.ogg is played from GameOverScreen's very first frame, right as this whole
    // instance is created, so it used to lose the race and play nothing. This listener plays it
    // (or any sound requested too early) the instant its own decode actually finishes.
    private val loadedSoundIds = mutableSetOf<Int>()
    private val pendingVolumeById = mutableMapOf<Int, Float>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            loadedSoundIds += sampleId
            pendingVolumeById.remove(sampleId)?.let { volume -> pool.play(sampleId, volume, volume, 0, 0, 1f) }
        }
    }

    override fun play(effect: SoundEffect, volume: Float) {
        val id = soundIds[effect] ?: return
        if (id in loadedSoundIds) {
            pool.play(id, volume, volume, 0, 0, 1f)
        } else {
            pendingVolumeById[id] = volume
        }
    }

    override fun release() {
        pool.release()
    }
}
