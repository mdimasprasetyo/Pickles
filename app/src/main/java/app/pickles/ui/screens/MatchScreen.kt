package app.pickles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pickles.engine.GameMode
import app.pickles.engine.MatchState
import app.pickles.engine.MatchStatus
import app.pickles.engine.PicklesEngine
import app.pickles.engine.ScoringSystem
import app.pickles.engine.TeamId
import app.pickles.ui.components.CompactScoreboard
import app.pickles.ui.components.pressScale
import androidx.compose.animation.togetherWith
import app.pickles.ui.components.CourtView
import app.pickles.ui.components.RenameDialog
import app.pickles.ui.components.Scoreboard
import app.pickles.ui.components.teamLabel
import app.pickles.ui.theme.PicklesColors

@Composable
fun MatchScreen(
    match: MatchState,
    flipView: Boolean,
    keepScreenOn: Boolean,
    courtLayout: app.pickles.ui.CourtLayout,
    notice: String?,
    onRally: (TeamId, Long) -> Unit,
    onUndo: (Long) -> Unit,
    onRestart: () -> Unit,
    onNextGame: (String, String, Long) -> Unit,
    onRename: (String, String) -> Unit,
    onFlipView: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onCourtLayout: (app.pickles.ui.CourtLayout) -> Unit,
    onHome: () -> Unit,
    onHelp: () -> Unit,
    onNoticeShown: () -> Unit,
) {
    // The single source of truth: everything below is derived from `match`.
    val sit = PicklesEngine.situation(match)
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(2500)
            onNoticeShown()
        }
    }

    val onWin: (TeamId) -> Unit = { team ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onRally(team, match.revision)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    if (maxWidth > maxHeight) {
        LandscapeMatch(
            match = match,
            sit = sit,
            flipView = flipView,
            notice = notice,
            onWin = onWin,
            onNextGame = { a, b -> onNextGame(a, b, match.revision) },
            onPlayerTap = { renaming = it },
            onUndo = { onUndo(match.revision) },
            onRestart = { confirmRestart = true },
            onSettings = { showSettings = true },
            onHome = onHome,
            onHelp = onHelp,
        )
    } else
    Column(Modifier.fillMaxSize()) {
        // Top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onHome) { Text("‹ Home", color = PicklesColors.Volt, fontSize = 16.sp) }
            Spacer(Modifier.weight(1f))
            Text("PICKLES", color = PicklesColors.Volt, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onHelp) { Text("Rules", color = PicklesColors.Volt, fontSize = 16.sp) }
        }

        // Upper section: court
        CourtView(
            match = match,
            sit = sit,
            flipView = flipView,
            onPlayerTap = { renaming = it },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            landscape = courtLayout != app.pickles.ui.CourtLayout.PORTRAIT,
        )
        Legend(match.settings.mode == GameMode.DOUBLES)

        // Lower section: scoreboard and controls
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NoticeBanner(notice, Modifier.fillMaxWidth())
            Scoreboard(match, sit, Modifier.fillMaxWidth())

            androidx.compose.animation.AnimatedContent(
                targetState = match.status,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                        androidx.compose.animation.slideInVertically { it / 6 }) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
                },
                label = "status",
            ) { status ->
            when (status) {
                MatchStatus.IN_PROGRESS -> RallyButtons(
                    match,
                    // With a sideways court, order the buttons to match where each team stands.
                    leftTeam = if (courtLayout == app.pickles.ui.CourtLayout.PORTRAIT) TeamId.A
                    else if (match.game.endsSwapped xor flipView) TeamId.B else TeamId.A,
                    onWin = onWin,
                )
                MatchStatus.BETWEEN_GAMES -> NextGameCard(match) { a, b -> onNextGame(a, b, match.revision) }
                MatchStatus.COMPLETED -> CompletedCard(match)
            }
            }

            Toolbar(
                canUndo = match.canUndo,
                onUndo = { onUndo(match.revision) },
                onRestart = { confirmRestart = true },
                onSettings = { showSettings = true },
                onHelp = onHelp,
            )
        }
    }
    }

    renaming?.let { id ->
        RenameDialog(match.player(id).name, onDismiss = { renaming = null }) {
            onRename(id, it)
            renaming = null
        }
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Restart match?") },
            text = { Text("The score and rally history will be cleared. Players, starting servers and format stay the same.") },
            confirmButton = { TextButton(onClick = { confirmRestart = false; onRestart() }) { Text("Restart", color = PicklesColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancel") } },
        )
    }
    if (showSettings) {
        SettingsDialog(
            match = match,
            flipView = flipView,
            keepScreenOn = keepScreenOn,
            courtLayout = courtLayout,
            onFlipView = onFlipView,
            onKeepScreenOn = onKeepScreenOn,
            onCourtLayout = onCourtLayout,
            onRename = { showSettings = false; renaming = it },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun Legend(doubles: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(PicklesColors.Volt, "Serve → receiving court")
        LegendDot(Color.White, "Required position")
        if (doubles) LegendDot(PicklesColors.Muted, "Flexible partner")
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, color = PicklesColors.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun RallyButtons(match: MatchState, leftTeam: TeamId, onWin: (TeamId) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(leftTeam, leftTeam.other).forEach { team ->
            val color = PicklesColors.team(team)
            val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Button(
                onClick = { onWin(team) },
                interactionSource = src,
                modifier = Modifier.weight(1f).pressScale(src).heightIn(min = 84.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = PicklesColors.Navy),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Team ${team.name} Won Rally", fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center)
                    Text(
                        teamLabel(match, team),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = PicklesColors.Navy.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NextGameCard(match: MatchState, onStart: (String, String) -> Unit) {
    val g = match.game
    var startA by remember(g.gameNumber) { mutableStateOf(g.startingServerA) }
    var startB by remember(g.gameNumber) { mutableStateOf(g.startingServerB) }
    val nextFirst = g.firstServingTeam.other
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PicklesColors.Panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Game ${g.gameNumber} to Team ${g.winner}  ${g.scoreA}–${g.scoreB}",
            color = PicklesColors.team(g.winner ?: TeamId.A),
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
        )
        Text(
            "Game ${g.gameNumber + 1}: teams change ends and Team ${nextFirst.name} serves first (Rule 21.F.2).",
            color = PicklesColors.Muted,
            fontSize = 13.sp,
        )
        if (match.settings.mode == GameMode.DOUBLES) {
            Text("Starting servers may change between games (Rule 21.F.3):", color = PicklesColors.Muted, fontSize = 13.sp)
            Row {
                StarterPicker(match, TeamId.A, startA, Modifier.weight(1f)) { startA = it }
                StarterPicker(match, TeamId.B, startB, Modifier.weight(1f)) { startB = it }
            }
        }
        val startSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Button(
            onClick = { onStart(startA, startB) },
            interactionSource = startSrc,
            modifier = Modifier.pressScale(startSrc).fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PicklesColors.Volt, contentColor = PicklesColors.Navy),
        ) { Text("Start game ${g.gameNumber + 1}", fontWeight = FontWeight.Black, fontSize = 18.sp) }
    }
}

@Composable
private fun StarterPicker(match: MatchState, team: TeamId, selected: String, modifier: Modifier, onSelect: (String) -> Unit) {
    Column(modifier) {
        Text("Team ${team.name}", color = PicklesColors.team(team), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        match.team(team).forEach { p ->
            Row(Modifier.clickable { onSelect(p.id) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == p.id, onClick = { onSelect(p.id) })
                Text(p.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CompletedCard(match: MatchState) {
    val winner = match.matchWinner ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PicklesColors.team(winner).copy(alpha = 0.18f)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Team ${winner.name} wins!", color = PicklesColors.team(winner), fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(teamLabel(match, winner), color = Color.White, fontSize = 15.sp)
        Text(
            match.completedGames.joinToString("   ") { "${it.scoreA}–${it.scoreB}" },
            color = PicklesColors.Muted,
            fontSize = 15.sp,
        )
        Text("Scoring is locked. Undo the last rally or restart.", color = PicklesColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Toolbar(canUndo: Boolean, onUndo: () -> Unit, onRestart: () -> Unit, onSettings: () -> Unit, onHelp: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToolButton("↶", "Undo", canUndo, Modifier.weight(1f), onClick = onUndo)
        ToolButton("⟲", "Restart", true, Modifier.weight(1f), onClick = onRestart)
        ToolButton("⚙", "Settings", true, Modifier.weight(1f), onClick = onSettings)
        ToolButton("?", "Rules", true, Modifier.weight(1f), onClick = onHelp)
    }
}

@Composable
private fun ToolButton(glyph: String, label: String, enabled: Boolean, modifier: Modifier, height: androidx.compose.ui.unit.Dp = 56.dp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PicklesColors.PanelHigh,
            contentColor = Color.White,
            disabledContainerColor = PicklesColors.Panel,
            disabledContentColor = PicklesColors.Muted.copy(alpha = 0.5f),
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(glyph, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsDialog(
    match: MatchState,
    flipView: Boolean,
    keepScreenOn: Boolean,
    courtLayout: app.pickles.ui.CourtLayout,
    onFlipView: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onCourtLayout: (app.pickles.ui.CourtLayout) -> Unit,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = match.settings
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match settings") },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Format", fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(if (s.mode == GameMode.SINGLES) "Singles" else "Doubles")
                        append(if (s.scoring == ScoringSystem.SIDE_OUT) " · side-out scoring" else " · rally scoring (provisional)")
                        append(" · games to ${s.pointsToWin}, win by ${s.winBy}")
                        append(if (s.bestOf == 1) " · 1 game" else " · best of ${s.bestOf}")
                        if (s.endChangeInDecidingGame) append(" · end change at ${s.endChangeAt}")
                    },
                    color = PicklesColors.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (s.isOfficialTournamentFormat) "Official tournament format (Rule 15.C)" else "Recreational format",
                    color = if (s.isOfficialTournamentFormat) PicklesColors.Volt else PicklesColors.TeamA,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "The format is fixed once a match starts. Start a new match from Home to change it.",
                    color = PicklesColors.Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider()
                Text("Players (tap to rename)", fontWeight = FontWeight.Bold)
                match.players.forEach { p ->
                    Text(
                        "Team ${p.team.name}: ${p.name}  ✎",
                        color = PicklesColors.team(p.team),
                        modifier = Modifier.fillMaxWidth().clickable { onRename(p.id) }.padding(vertical = 6.dp),
                    )
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Flip court view")
                        Text("Swap which team is drawn on the left (landscape court) or bottom (portrait court). Display only.", color = PicklesColors.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = flipView, onCheckedChange = onFlipView)
                }
                Text("Court view", fontWeight = FontWeight.Bold)
                app.pickles.ui.CourtLayout.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onCourtLayout(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = courtLayout == option, onClick = { onCourtLayout(option) })
                        Column(Modifier.weight(1f)) {
                            Text(option.label)
                            Text(option.description, color = PicklesColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keep screen on during a match", Modifier.weight(1f))
                    Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}


/**
 * Landscape referee view: the court is drawn sideways as seen from the net post, and each team's
 * "won rally" button sits on the same side of the screen as that team stands on the court.
 * Display only: every value still comes from [PicklesEngine.situation].
 */
@Composable
private fun LandscapeMatch(
    match: MatchState,
    sit: app.pickles.engine.CourtSituation,
    flipView: Boolean,
    notice: String?,
    onWin: (TeamId) -> Unit,
    onNextGame: (String, String) -> Unit,
    onPlayerTap: (String) -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onHome: () -> Unit,
    onHelp: () -> Unit,
) {
    // Same rule as CourtView: the "near" team is drawn on the left.
    val leftTeam = if (match.game.endsSwapped xor flipView) TeamId.B else TeamId.A
    val live = match.status == MatchStatus.IN_PROGRESS
    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SideRallyButton(match, leftTeam, live, Modifier.width(132.dp).fillMaxHeight()) { onWin(leftTeam) }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onHome, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                    Text("‹ Home", color = PicklesColors.Volt, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("PICKLES", color = PicklesColors.Volt, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onHelp, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                    Text("Rules", color = PicklesColors.Volt, fontSize = 15.sp)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CourtView(
                    match = match,
                    sit = sit,
                    flipView = flipView,
                    onPlayerTap = onPlayerTap,
                    modifier = Modifier.fillMaxSize(),
                    landscape = true,
                )
                when (match.status) {
                    MatchStatus.IN_PROGRESS -> Unit
                    MatchStatus.BETWEEN_GAMES -> Box(
                        Modifier.align(Alignment.Center).widthIn(max = 460.dp)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) { NextGameCard(match, onNextGame) }
                    MatchStatus.COMPLETED -> Box(
                        Modifier.align(Alignment.Center).widthIn(max = 420.dp)
                            .clip(RoundedCornerShape(18.dp)).background(PicklesColors.Navy.copy(alpha = 0.92f)),
                    ) { CompletedCard(match) }
                }
                NoticeBanner(notice, Modifier.align(Alignment.TopCenter))
            }
            CompactScoreboard(match, sit, leftTeam, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolButton("↶", "Undo", match.canUndo, Modifier.weight(1f), 46.dp, onClick = onUndo)
                ToolButton("⟲", "Restart", true, Modifier.weight(1f), 46.dp, onClick = onRestart)
                ToolButton("⚙", "Settings", true, Modifier.weight(1f), 46.dp, onClick = onSettings)
            }
        }
        SideRallyButton(match, leftTeam.other, live, Modifier.width(132.dp).fillMaxHeight()) { onWin(leftTeam.other) }
    }
}

@Composable
private fun SideRallyButton(match: MatchState, team: TeamId, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = PicklesColors.team(team)
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = src,
        modifier = modifier.pressScale(src, 0.97f),
        shape = RoundedCornerShape(20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = PicklesColors.Navy,
            disabledContainerColor = color.copy(alpha = 0.18f),
            disabledContentColor = color.copy(alpha = 0.6f),
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TEAM ${team.name}", fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
            Text("WON\nRALLY", fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                teamLabel(match, team),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) PicklesColors.Navy.copy(alpha = 0.75f) else color.copy(alpha = 0.6f),
            )
        }
    }
}


/** Short-lived message (e.g. a rejected tap) that fades and slides in rather than popping. */
@Composable
private fun NoticeBanner(notice: String?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf("") }
    if (notice != null) last = notice
    androidx.compose.animation.AnimatedVisibility(
        visible = notice != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
        modifier = modifier,
    ) {
        Text(
            last,
            color = PicklesColors.Navy,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(PicklesColors.TeamA).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
