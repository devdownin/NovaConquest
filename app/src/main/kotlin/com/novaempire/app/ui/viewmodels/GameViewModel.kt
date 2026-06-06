package com.novaempire.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.engine.GameEngine
import com.novaempire.core.engine.GameIntent
import com.novaempire.core.engine.save.SaveManager
import com.novaempire.core.engine.save.SaveRepository
import com.novaempire.app.audio.AudioManager
import com.novaempire.app.audio.SoundType
import com.novaempire.core.engine.GameEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = GameEngine()

    val gameState: StateFlow<GameState> = engine.state
    val isAiThinking: StateFlow<Boolean> = engine.isAiThinking
    val errors: SharedFlow<String> = engine.errors
    val effects: SharedFlow<GameEffect> = engine.effects

    private val _notifications = MutableSharedFlow<Pair<String, String>>()
    val notifications: SharedFlow<Pair<String, String>> = _notifications.asSharedFlow()

    private val saveRepository: SaveRepository

    init {
        val saveDir = File(application.filesDir, "saves")
        saveRepository = SaveManager(saveDir)
        AudioManager.init(application)

        // Observe game state for audio events (legacy - now moving to effects)
        /*
        viewModelScope.launch {
            gameState.collectLatest { state ->
                state.lastCombatEvent?.let { event ->
                    if (event.targetDestroyed) {
                        AudioManager.playSound(SoundType.COMBAT_EXPLOSION)
                    } else {
                        AudioManager.playSound(SoundType.COMBAT_LASER)
                    }
                }
            }
        }
        */

        // Observe game effects (new architectural approach)
        viewModelScope.launch {
            engine.effects.collect { effect ->
                when (effect) {
                    is GameEffect.PlaySound -> {
                        try {
                            val type = SoundType.valueOf(effect.soundId)
                            AudioManager.playSound(type)
                        } catch (_: IllegalArgumentException) {
                            // Ignore or log unknown sound IDs
                        }
                    }
                    is GameEffect.ShowNotification -> {
                        _notifications.emit(effect.message to effect.color)
                    }
                    is GameEffect.ShakeCamera -> {
                        // UI layer collects `effects` directly for haptic / animation
                    }
                }
            }
        }
    }

    fun dispatch(intent: GameIntent) {
        // Play UI click for every user intent
        AudioManager.playSound(SoundType.UI_CLICK)
        
        engine.processIntent(intent)

        // Auto-save and sound on EndTurn
        if (intent is GameIntent.EndTurn) {
            AudioManager.playSound(SoundType.END_TURN)
            val snapshot = engine.state.value
            viewModelScope.launch(Dispatchers.IO) { saveRepository.saveGame(snapshot) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.dispose()
        AudioManager.release()
    }

    fun hasSavedGame(): Boolean = saveRepository.hasSavedGame()

    fun loadGame(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) { saveRepository.loadLatestGame() }
            if (state != null) {
                engine.processIntent(GameIntent.LoadGame(state))
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun startNewGame(
        mapSize: com.novaempire.core.domain.models.MapSize = com.novaempire.core.domain.models.MapSize.MEDIUM,
        archetype: com.novaempire.core.domain.models.MapArchetype = com.novaempire.core.domain.models.MapArchetype.STANDARD
    ) {
        engine.processIntent(GameIntent.StartNewGameWithSize(mapSize, archetype))
    }
}
