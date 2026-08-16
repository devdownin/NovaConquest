package com.novaempire.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novaempire.core.domain.state.GameState
import com.novaempire.core.engine.GameEngine
import com.novaempire.core.engine.GameIntent
import com.novaempire.core.engine.save.CampaignProgressRepository
import com.novaempire.core.engine.save.CampaignProgressStore
import com.novaempire.core.engine.save.SaveManager
import com.novaempire.core.engine.save.SaveRepository
import com.novaempire.app.audio.AudioManager
import com.novaempire.app.audio.SoundType
import com.novaempire.core.engine.GameEffect
import com.novaempire.core.engine.save.LoadResult
import com.novaempire.app.settings.AppSettings
import com.novaempire.app.settings.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = GameEngine()

    val gameState: StateFlow<GameState> = engine.state
    val isAiThinking: StateFlow<Boolean> = engine.isAiThinking

    /** Whether the last action of the current turn can be taken back — drives the UNDO button. */
    val canUndo: StateFlow<Boolean> = engine.canUndo

    /** Distingue « rien à annuler » de « l'action a levé du brouillard, elle est définitive ». */
    val undoClosedByExploration: StateFlow<Boolean> = engine.undoClosedByExploration

    /** Tirs résolus, un par échange — y compris deux échanges identiques d'affilée. */
    val combatEvents: SharedFlow<com.novaempire.core.domain.state.CombatEvent> =
        engine.effects
            .filterIsInstance<GameEffect.CombatResolved>()
            .map { it.event }
            .shareIn(viewModelScope, SharingStarted.Eagerly)
    /**
     * Secousses de caméra demandées par le moteur.
     *
     * `ShakeCamera` était émis depuis le début et n'était collecté nulle part : le commentaire du
     * `when` ci-dessous affirmait que « la couche UI collecte les effets directement », mais aucun
     * écran ne le faisait. L'exposer comme un flux dédié, à l'image de [combatEvents], donne à la
     * carte quelque chose à collecter sans lui faire filtrer la sealed class elle-même.
     */
    val shakeEvents: SharedFlow<Unit> =
        engine.effects
            .filterIsInstance<GameEffect.ShakeCamera>()
            .map { }
            .shareIn(viewModelScope, SharingStarted.Eagerly)

    val errors: SharedFlow<String> = engine.errors
    val effects: SharedFlow<GameEffect> = engine.effects

    private val _notifications = MutableSharedFlow<Pair<String, String>>()
    val notifications: SharedFlow<Pair<String, String>> = _notifications.asSharedFlow()

    private val saveRepository: SaveRepository

    // Campaign progress lives on its own file rather than in the turn autosave, which skips
    // terminal states by design — so the one moment progress is created (winning a mission) was
    // the one moment nothing was written. See CampaignProgressStore.
    private val campaignProgress: CampaignProgressRepository

    // Préférences d'affichage et d'audio, volontairement hors du GameState : elles doivent exister
    // avant qu'une partie soit chargée (menu principal, écrans de sélection) et ne rien devoir au
    // format de sauvegarde.
    private val settingsStore = SettingsStore(application)
    private val _settings = MutableStateFlow(settingsStore.read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        val saveDir = File(application.filesDir, "saves")
        saveRepository = SaveManager(saveDir)
        campaignProgress = CampaignProgressStore(File(saveDir, "campaign_progress.json"))
        AudioManager.init(application)
        AudioManager.setVolumes(_settings.value.masterVolume, _settings.value.sfxVolume)

        // Restore the earned record, then mirror it back to disk whenever it changes.
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { campaignProgress.read() }
            engine.processIntent(GameIntent.RestoreCampaignProgress(stored))

            // Wait for the restore to actually land before mirroring: intents are processed on a
            // channel, so the first state emissions still carry an empty record — writing one of
            // those back would erase the file that was just read.
            engine.state.first { it.campaignState.toProgress() == stored }

            var lastWritten = stored
            engine.state.collect { state ->
                val current = state.campaignState.toProgress()
                if (current != lastWritten) {
                    lastWritten = current
                    val saved = withContext(Dispatchers.IO) { campaignProgress.write(current) }
                    if (!saved) _notifications.emit("ÉCHEC DE L'ENREGISTREMENT DE LA PROGRESSION" to "RED")
                }
            }
        }

        // Auto-save once an end-of-turn cycle has actually settled. EndTurn is processed
        // asynchronously (AI turns can take seconds), so saving the snapshot synchronously in
        // dispatch() persisted the *pre*-turn state. Observing the turn counter instead captures
        // the fully-resolved state after every completed turn.
        viewModelScope.launch {
            var lastSavedTurn = engine.state.value.turn
            engine.state.collect { state ->
                // Never persist a finished game: the victory is detected during the same EndTurn
                // that bumps the counter, so the autosave used to capture a terminal state — and
                // "RESUME COMMAND" then dropped the player straight back onto the victory screen
                // of a game they had already completed. Keeping the previous turn on disk leaves a
                // save that can actually be played.
                if (state.turn != lastSavedTurn && state.victoryReason == null) {
                    lastSavedTurn = state.turn
                    val saved = withContext(Dispatchers.IO) { saveRepository.saveGame(state) }
                    // Surface write failures instead of failing silently — the player would
                    // otherwise keep playing believing the turn had been auto-saved.
                    if (!saved) _notifications.emit("ÉCHEC DE LA SAUVEGARDE AUTOMATIQUE" to "RED")
                }
            }
        }

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
                        // Consommé par `shakeEvents`, que la carte collecte pour secouer la caméra.
                    }
                    is GameEffect.CombatResolved -> {
                        // Consommé par `combatEvents`, que l'écran de carte collecte pour animer
                        // le tir. Le son part séparément en PlaySound.
                    }
                }
            }
        }
    }

    fun dispatch(intent: GameIntent) {
        // Play UI click for every user intent
        AudioManager.playSound(SoundType.UI_CLICK)
        
        engine.processIntent(intent)

        // Sound on EndTurn. The autosave itself happens in the turn-counter observer above,
        // once the (asynchronous) end-of-turn processing has fully settled.
        if (intent is GameIntent.EndTurn) {
            AudioManager.playSound(SoundType.END_TURN)
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.dispose()
        AudioManager.release()
    }

    /**
     * Applique et persiste les réglages, immédiatement.
     *
     * Pas de brouillon validé par un bouton : chaque réglage a un effet visible ou audible tout de
     * suite, donc le voir en direct vaut mieux qu'un « APPLY » qui n'annonce pas à quoi il engage.
     */
    fun updateSettings(settings: AppSettings) {
        if (_settings.value == settings) return
        settingsStore.write(settings)
        _settings.value = settings
        AudioManager.setVolumes(settings.masterVolume, settings.sfxVolume)
    }

    fun hasSavedGame(): Boolean = saveRepository.hasSavedGame()

    fun loadGame(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { saveRepository.loadLatestGame() }) {
                is LoadResult.Success -> {
                    // Guard for saves written before the rule above: loading a finished game would
                    // jump straight to the end screen, which reads as a bug rather than a resume.
                    if (result.state.victoryReason != null) {
                        onResult(false, "Cette sauvegarde correspond à une partie déjà terminée.")
                    } else {
                        engine.processIntent(GameIntent.LoadGame(result.state))
                        onResult(true, null)
                    }
                }
                is LoadResult.Failed -> onResult(false, result.reason)
                is LoadResult.NoSave -> onResult(false, null)
            }
        }
    }

    fun startNewGame(
        mapSize: com.novaempire.core.domain.models.MapSize = com.novaempire.core.domain.models.MapSize.MEDIUM,
        archetype: com.novaempire.core.domain.models.MapArchetype = com.novaempire.core.domain.models.MapArchetype.STANDARD
    ) {
        engine.processIntent(GameIntent.StartNewGameWithSize(mapSize, archetype))
    }

    fun startCampaignMission(
        mission: com.novaempire.core.domain.models.CampaignMission,
        perkIds: Set<String> = emptySet()
    ) {
        // Start a new game with mission parameters
        engine.processIntent(GameIntent.StartNewGameWithSize(mission.mapSize, mission.mapArchetype))

        // Select the correct faction
        engine.processIntent(GameIntent.SelectFaction(mission.playerFaction))

        // Apply campaign state, charging any glory perks bought for this run
        engine.processIntent(GameIntent.StartCampaign(mission.id, perkIds))
    }
}
