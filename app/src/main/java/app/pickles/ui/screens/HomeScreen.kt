package app.pickles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pickles.engine.GameMode
import app.pickles.engine.MatchState
import app.pickles.engine.MatchStatus
import app.pickles.engine.PicklesEngine
import app.pickles.ui.components.BallLogo
import app.pickles.ui.components.teamLabel
import app.pickles.ui.theme.PicklesColors
import app.pickles.ui.components.enter
import app.pickles.ui.components.pressScale
import androidx.compose.foundation.layout.Box

@Composable
fun HomeScreen(
    match: MatchState?,
    onSingles: () -> Unit,
    onDoubles: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onHelp: () -> Unit,
) {
    val unfinished = match != null && match.status != MatchStatus.COMPLETED
    var confirmFor by remember { mutableStateOf<GameMode?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        BallLogo(Modifier.size(84.dp).enter(0))
        Spacer(Modifier.height(12.dp))
        Text("Pickles", fontSize = 44.sp, fontWeight = FontWeight.Black, color = PicklesColors.Volt, modifier = Modifier.enter(1))
        Text(
            "Tap who won the rally. Pickles handles the rest.",
            color = PicklesColors.Muted,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.enter(2),
        )
        Spacer(Modifier.height(28.dp))

        if (match != null) {
            Box(Modifier.enter(3)) { ResumeCard(match, unfinished, onResume, onDiscard = { confirmDiscard = true }) }
            Spacer(Modifier.height(24.dp))
        }

        Text("New match", color = PicklesColors.Muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        BigChoice("Singles", "1 vs 1", Modifier.enter(4)) { if (unfinished) confirmFor = GameMode.SINGLES else onSingles() }
        Spacer(Modifier.height(12.dp))
        BigChoice("Doubles", "2 vs 2", Modifier.enter(5)) { if (unfinished) confirmFor = GameMode.DOUBLES else onDoubles() }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onHelp) { Text("Rules & scoring guide", color = PicklesColors.Volt, fontSize = 16.sp) }
        Spacer(Modifier.height(8.dp))
        Text(
            "Works fully offline. No account, ads or tracking.",
            color = PicklesColors.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    confirmFor?.let { mode ->
        AlertDialog(
            onDismissRequest = { confirmFor = null },
            title = { Text("Start a new match?") },
            text = { Text("Your unfinished match will be replaced when the new one starts.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmFor = null
                    if (mode == GameMode.SINGLES) onSingles() else onDoubles()
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { confirmFor = null }) { Text("Cancel") } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this match?") },
            text = { Text("The saved match and its history will be deleted.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ResumeCard(match: MatchState, unfinished: Boolean, onResume: () -> Unit, onDiscard: () -> Unit) {
    val sit = PicklesEngine.situation(match)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PicklesColors.Panel).padding(18.dp),
    ) {
        val mode = if (match.settings.mode == GameMode.SINGLES) "Singles" else "Doubles"
        Text(
            if (unfinished) "Unfinished $mode match" else "Last $mode match",
            color = PicklesColors.Volt,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${teamLabel(match, app.pickles.engine.TeamId.A)}  vs  ${teamLabel(match, app.pickles.engine.TeamId.B)}",
            color = androidx.compose.ui.graphics.Color.White,
        )
        Spacer(Modifier.height(4.dp))
        val detail = when (match.status) {
            MatchStatus.IN_PROGRESS -> "Game ${match.game.gameNumber} · Call ${sit.scoreCall} · ${sit.server.name} to serve"
            MatchStatus.BETWEEN_GAMES -> "Game ${match.game.gameNumber} finished ${match.game.scoreA}-${match.game.scoreB}. Next game ready."
            MatchStatus.COMPLETED -> "Team ${match.matchWinner} won · " + match.completedGames.joinToString("  ") { "${it.scoreA}-${it.scoreB}" }
        }
        Text(detail, color = PicklesColors.Muted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onResume,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PicklesColors.Volt, contentColor = PicklesColors.Navy),
            ) { Text(if (unfinished) "Resume match" else "View match", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onDiscard, modifier = Modifier.height(52.dp)) { Text("Discard") }
        }
    }
}

@Composable
private fun BigChoice(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = src,
        modifier = modifier.pressScale(src).fillMaxWidth().height(84.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PicklesColors.PanelHigh, contentColor = androidx.compose.ui.graphics.Color.White),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = PicklesColors.Muted)
        }
        Text("›", fontSize = 36.sp, color = PicklesColors.Volt)
    }
}
