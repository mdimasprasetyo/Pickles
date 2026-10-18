package app.pickles

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pickles.ui.MatchViewModel
import app.pickles.ui.Screen
import app.pickles.ui.screens.HelpScreen
import app.pickles.ui.screens.HomeScreen
import app.pickles.ui.screens.MatchScreen
import app.pickles.ui.screens.SetupScreen
import app.pickles.ui.theme.PicklesColors
import app.pickles.ui.theme.PicklesTheme

class MainActivity : ComponentActivity() {
    private val vm: MatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicklesTheme {
                PicklesApp(
                    vm,
                    setKeepScreenOn = { keepOn ->
                        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                    setLandscape = { landscape ->
                        val want = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        if (requestedOrientation != want) requestedOrientation = want
                    },
                )
            }
        }
    }
}

@Composable
fun PicklesApp(vm: MatchViewModel, setKeepScreenOn: (Boolean) -> Unit, setLandscape: (Boolean) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val onMatch = ui.screen == Screen.Match
    LaunchedEffect(onMatch, ui.keepScreenOn) { setKeepScreenOn(onMatch && ui.keepScreenOn) }
    // Only the match screen turns sideways (referee view); Home, Setup and Rules stay portrait.
    LaunchedEffect(onMatch, ui.courtLayout) { setLandscape(onMatch && ui.courtLayout == app.pickles.ui.CourtLayout.FULL_LANDSCAPE) }

    Box(Modifier.fillMaxSize().background(PicklesColors.Navy).safeDrawingPadding()) {
        AnimatedContent(
            targetState = ui.screen,
            transitionSpec = {
                // Deeper screens slide in from the right; going back slides the other way.
                val forward = depth(targetState) >= depth(initialState)
                val dir = if (forward) 1 else -1
                (fadeIn(tween(260, delayMillis = 40)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { dir * it / 10 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -dir * it / 14 })
            },
            contentKey = { if (it is Screen.Setup) "setup-${it.mode}" else it::class.simpleName },
            label = "screen",
        ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(
                match = ui.match,
                onSingles = { vm.openSetup(app.pickles.engine.GameMode.SINGLES) },
                onDoubles = { vm.openSetup(app.pickles.engine.GameMode.DOUBLES) },
                onResume = vm::resume,
                onDiscard = vm::discardMatch,
                onHelp = vm::openHelp,
            )
            is Screen.Setup -> {
                BackHandler { vm.goHome() }
                SetupScreen(
                    mode = screen.mode,
                    previous = ui.match?.setup?.takeIf { it.settings.mode == screen.mode },
                    onBack = vm::goHome,
                    onStart = vm::startMatch,
                    onHelp = vm::openHelp,
                )
            }
            Screen.Match -> {
                BackHandler { vm.goHome() }
                val match = ui.match
                if (match == null) {
                    LaunchedEffect(Unit) { vm.goHome() }
                } else {
                    MatchScreen(
                        match = match,
                        flipView = ui.flipView,
                        keepScreenOn = ui.keepScreenOn,
                        courtLayout = ui.courtLayout,
                        notice = ui.notice,
                        onRally = vm::rallyWon,
                        onUndo = vm::undo,
                        onRestart = vm::restart,
                        onNextGame = vm::startNextGame,
                        onRename = vm::rename,
                        onFlipView = vm::setFlipView,
                        onKeepScreenOn = vm::setKeepScreenOn,
                        onCourtLayout = vm::setCourtLayout,
                        onHome = vm::goHome,
                        onHelp = vm::openHelp,
                        onNoticeShown = vm::clearNotice,
                    )
                }
            }
            Screen.Help -> {
                BackHandler { vm.closeHelp() }
                HelpScreen(onBack = vm::closeHelp)
            }
        }
        }
    }
}

private fun depth(s: Screen): Int = when (s) {
    Screen.Home -> 0
    is Screen.Setup -> 1
    Screen.Match -> 2
    Screen.Help -> 3
}
