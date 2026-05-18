package com.novaempire.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.engine.GameEngine
import com.novaempire.core.engine.GameIntent
import com.novaempire.core.engine.save.SaveManager
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = GameEngine()
    private val saveManager: SaveManager

    init {
        val saveDir = File(application.filesDir, "saves")
        saveManager = SaveManager(saveDir)
    }

    val gameState: StateFlow<GameState> = engine.state

    fun dispatch(intent: GameIntent) {
        engine.processIntent(intent)

        // Auto-save on EndTurn
        if (intent is GameIntent.EndTurn) {
            saveManager.saveGame(engine.state.value)
        }
    }

    fun hasSavedGame(): Boolean {
        val saveDir = File(getApplication<Application>().filesDir, "saves")
        return File(saveDir, "autosave_1.json").exists()
    }

    fun loadGame(): Boolean {
        val state = saveManager.loadLatestGame()
        if (state != null) {
            engine.processIntent(GameIntent.LoadGame(state))
            return true
        }
        return false
    }

    fun startNewGame(mapSize: com.novaempire.core.domain.models.MapSize = com.novaempire.core.domain.models.MapSize.MEDIUM) {
        engine.processIntent(GameIntent.StartNewGame(mapSize))
    }
}
