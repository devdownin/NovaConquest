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

        val soundId = soundMap[type]
        if (soundId != null) {
            soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
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
