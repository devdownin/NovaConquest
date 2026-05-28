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
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // Load dummy sounds (assuming they exist in res/raw, which we don't have here, so we catch exception)
        // soundMap[SoundType.COMBAT_LASER] = soundPool!!.load(context, R.raw.laser, 1)
        // soundMap[SoundType.COMBAT_EXPLOSION] = soundPool!!.load(context, R.raw.explosion, 1)
        // soundMap[SoundType.UI_CLICK] = soundPool!!.load(context, R.raw.ui_click, 1)
        // soundMap[SoundType.END_TURN] = soundPool!!.load(context, R.raw.end_turn, 1)

        isInitialized = true
        Log.d("AudioManager", "Initialized SoundPool")
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
