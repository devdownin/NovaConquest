package com.novaempire.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

enum class SoundType {
    COMBAT_LASER,
    COMBAT_EXPLOSION,
    UI_CLICK,
    END_TURN
}

object AudioManager {
    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<SoundType, Int>()
    private var isInitialized = false

    // Les curseurs de volume de l'écran de réglages n'étaient branchés sur rien : tout se jouait à
    // plein volume. `masterVolume` s'applique à tout, `sfxVolume` seulement aux bruits de combat —
    // sans ça, baisser les « SFX » aurait aussi étouffé les clics d'interface.
    private var masterVolume = 0.8f
    private var sfxVolume = 0.7f

    fun setVolumes(master: Float, sfx: Float) {
        masterVolume = master.coerceIn(0f, 1f)
        sfxVolume = sfx.coerceIn(0f, 1f)
    }

    private fun volumeFor(type: SoundType): Float = when (type) {
        SoundType.COMBAT_LASER, SoundType.COMBAT_EXPLOSION -> masterVolume * sfxVolume
        SoundType.UI_CLICK, SoundType.END_TURN -> masterVolume
    }

    fun init(context: Context) {
        if (isInitialized) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        // Dynamically load resources from res/raw if they exist
        val soundsToLoad = mapOf(
            SoundType.COMBAT_LASER to "laser",
            SoundType.COMBAT_EXPLOSION to "explosion",
            SoundType.UI_CLICK to "ui_click",
            SoundType.END_TURN to "end_turn"
        )

        soundsToLoad.forEach { (type, fileName) ->
            val resId = context.resources.getIdentifier(fileName, "raw", context.packageName)
            if (resId != 0) {
                soundMap[type] = soundPool!!.load(context, resId, 1)
                Log.d("AudioManager", "Loaded sound resource: $fileName (ID: $resId)")
            } else {
                Log.w("AudioManager", "Sound resource NOT found: $fileName. Ensure it exists in res/raw/")
            }
        }

        isInitialized = true
        Log.d("AudioManager", "AudioManager Initialized")
    }

    fun playSound(type: SoundType) {
        if (!isInitialized) return

        val volume = volumeFor(type)
        if (volume <= 0f) return

        val soundId = soundMap[type]
        if (soundId != null) {
            soundPool?.play(soundId, volume, volume, 1, 0, 1f)
            Log.d("AudioManager", "Playing sound: $type")
        } else {
            // Log missing sound for dummy implementation
            Log.d("AudioManager", "Mock play sound: $type")
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        isInitialized = false
    }
}
