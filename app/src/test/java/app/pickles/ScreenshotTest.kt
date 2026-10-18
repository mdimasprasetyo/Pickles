package app.pickles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.pickles.engine.GameMode
import app.pickles.engine.MatchSettings
import app.pickles.engine.MatchSetup
import app.pickles.engine.MatchState
import app.pickles.engine.PicklesEngine
import app.pickles.engine.TeamId
import app.pickles.engine.Transition
import app.pickles.ui.screens.HelpScreen
import app.pickles.ui.screens.HomeScreen
import app.pickles.ui.screens.MatchScreen
import app.pickles.ui.screens.SetupScreen
import app.pickles.ui.theme.PicklesColors
import app.pickles.ui.theme.PicklesTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the real screens from real engine states, for visual review (build/outputs/roborazzi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun doubles(seq: String, bestOf: Int = 1, points: Int = 11): MatchState {
        var s = (PicklesEngine.newMatch(
            MatchSetup(MatchSettings(GameMode.DOUBLES, pointsToWin = points, bestOf = bestOf), listOf("Ana", "Ben"), listOf("Cara", "Dev")),
        ) as Transition.Applied).state
        seq.forEach { c -> s = (PicklesEngine.applyRallyResult(s, TeamId.valueOf(c.toString())) as Transition.Applied).state }
        return s
    }

    private fun singles(seq: String): MatchState {
        var s = (PicklesEngine.newMatch(MatchSetup(MatchSettings(GameMode.SINGLES), listOf("Ana"), listOf("Cara"))) as Transition.Applied).state
        seq.forEach { c -> s = (PicklesEngine.applyRallyResult(s, TeamId.valueOf(c.toString())) as Transition.Applied).state }
        return s
    }

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { PicklesTheme { Box(Modifier.fillMaxSize().background(PicklesColors.Navy)) { content() } } }
        rule.mainClock.advanceTimeBy(3000)
        rule.onRoot().captureRoboImage("screens/$name.png")
    }



    @Test fun home() = shoot("01_home") { HomeScreen(doubles("AAB"), {}, {}, {}, {}, {}) }
    @Test fun setup() = shoot("02_setup_doubles") { SetupScreen(GameMode.DOUBLES, null, {}, {}, {}) }
    @Test fun doublesStart() = shoot("03_doubles_002") { M(doubles("")) }
    @Test fun doublesMid() = shoot("04_doubles_mid") { M(doubles("ABAABBA")) }
    @Test fun singlesMid() = shoot("05_singles") { M(singles("AAAB")) }
    @Test fun betweenGames() = shoot("06_between_games") { M(doubles("AAA", bestOf = 3, points = 3)) }
    @Test fun completed() = shoot("07_completed") { M(doubles("AAA", points = 3)) }
    @Test fun help() = shoot("08_help") { HelpScreen {} }
}

@androidx.compose.runtime.Composable
private fun M(m: MatchState) = MatchScreen(
    match = m, flipView = false, keepScreenOn = true, courtLayout = app.pickles.ui.CourtLayout.PORTRAIT, notice = null,
    onRally = { _, _ -> }, onUndo = {}, onRestart = {}, onNextGame = { _, _, _ -> }, onRename = { _, _ -> },
    onFlipView = {}, onKeepScreenOn = {}, onCourtLayout = {}, onHome = {}, onHelp = {}, onNoticeShown = {},
)
