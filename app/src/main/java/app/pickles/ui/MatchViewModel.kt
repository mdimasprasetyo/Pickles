package app.pickles.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pickles.data.MatchRepository
import app.pickles.data.UiPrefs
import app.pickles.engine.GameMode
import app.pickles.engine.MatchSetup
import app.pickles.engine.MatchState
import app.pickles.engine.MatchStatus
import app.pickles.engine.PicklesEngine
import app.pickles.engine.TeamId
import app.pickles.engine.Transition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the match screen shows the court. Display only; never affects scoring. */
enum class CourtLayout(val label: String, val description: String) {
    PORTRAIT("Portrait court", "Upright court, baselines at top and bottom."),
    LANDSCAPE("Landscape court", "Sideways court as seen from the net post. Phone stays in portrait."),
    FULL_LANDSCAPE("Full landscape screen", "Whole match screen turns sideways, rally buttons beside the court."),
}

sealed interface Screen {
    data object Home : Screen
    data class Setup(val mode: GameMode) : Screen
    data object Match : Screen
    data object Help : Screen
}

data class UiState(
    val screen: Screen = Screen.Home,
    /** The authoritative match state, or null when no match exists. */
    val match: MatchState? = null,
    val flipView: Boolean = false,
    val keepScreenOn: Boolean = true,
    val courtLayout: CourtLayout = CourtLayout.LANDSCAPE,
    /** Short-lived message, e.g. a rejected transition. */
    val notice: String? = null,
    /** Screen to return to when leaving Help. */
    val helpReturn: Screen = Screen.Home,
)

/**
 * Holds the single authoritative [MatchState] and routes every user intent through the pure
 * [PicklesEngine]. The UI never computes scores or servers itself.
 *
 * All transitions run synchronously on the main thread, one at a time, and carry the revision the
 * UI rendered, so a rapid double tap is applied exactly once. Each committed state is persisted.
 */
class MatchViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MatchRepository(app)
    private val prefs = UiPrefs(app)
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** Conflated: the writer always persists the latest committed state, in order, off the main thread. */
    private val saves = Channel<MatchState?>(Channel.CONFLATED)

    init {
        _ui.value = UiState(match = repo.load(), flipView = prefs.flipView, keepScreenOn = prefs.keepScreenOn, courtLayout = prefs.courtLayout)
        viewModelScope.launch(Dispatchers.IO) {
            for (state in saves) if (state == null) repo.clear() else repo.save(state)
        }
    }

    private fun commit(t: Transition, onApplied: (MatchState) -> Unit = {}) {
        when (t) {
            is Transition.Applied -> {
                _ui.update { it.copy(match = t.state, notice = null) }
                saves.trySend(t.state)
                onApplied(t.state)
            }
            is Transition.Rejected ->
                // A stale revision is a duplicate tap: ignore it silently.
                if (t.reason != app.pickles.engine.RejectReason.STALE_REVISION) _ui.update { it.copy(notice = t.message) }
        }
    }

    // ---------------------------------------------------------------- navigation
    fun openSetup(mode: GameMode) = _ui.update { it.copy(screen = Screen.Setup(mode)) }
    fun resume() = _ui.update { it.copy(screen = Screen.Match) }
    fun goHome() = _ui.update { it.copy(screen = Screen.Home) }
    fun openHelp() = _ui.update { it.copy(helpReturn = it.screen, screen = Screen.Help) }
    fun closeHelp() = _ui.update { it.copy(screen = it.helpReturn) }
    fun clearNotice() = _ui.update { it.copy(notice = null) }

    // ---------------------------------------------------------------- match intents
    fun startMatch(setup: MatchSetup) = commit(PicklesEngine.newMatch(setup)) {
        _ui.update { s -> s.copy(screen = Screen.Match) }
    }

    /** [renderedRevision] is the revision of the state the tapped button was drawn from. */
    fun rallyWon(winner: TeamId, renderedRevision: Long) {
        val m = _ui.value.match ?: return
        commit(PicklesEngine.applyRallyResult(m, winner, renderedRevision))
    }

    fun undo(renderedRevision: Long) {
        val m = _ui.value.match ?: return
        commit(PicklesEngine.undo(m, renderedRevision))
    }

    fun startNextGame(startingServerA: String, startingServerB: String, renderedRevision: Long) {
        val m = _ui.value.match ?: return
        commit(PicklesEngine.startNextGame(m, startingServerA, startingServerB, renderedRevision))
    }

    fun restart() {
        val m = _ui.value.match ?: return
        commit(PicklesEngine.restart(m))
    }

    fun rename(playerId: String, name: String) {
        val m = _ui.value.match ?: return
        commit(PicklesEngine.renamePlayer(m, playerId, name))
    }

    fun discardMatch() {
        _ui.update { it.copy(match = null, screen = Screen.Home) }
        saves.trySend(null)
    }

    fun setFlipView(v: Boolean) {
        prefs.flipView = v
        _ui.update { it.copy(flipView = v) }
    }

    fun setKeepScreenOn(v: Boolean) {
        prefs.keepScreenOn = v
        _ui.update { it.copy(keepScreenOn = v) }
    }

    fun setCourtLayout(v: CourtLayout) {
        prefs.courtLayout = v
        _ui.update { it.copy(courtLayout = v) }
    }

    val hasResumableMatch: Boolean get() = _ui.value.match?.status?.let { it != MatchStatus.COMPLETED } == true
}
