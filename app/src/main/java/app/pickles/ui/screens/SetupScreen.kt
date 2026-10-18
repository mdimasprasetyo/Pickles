package app.pickles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pickles.engine.GameMode
import app.pickles.engine.MatchSettings
import app.pickles.engine.MatchSetup
import app.pickles.engine.PicklesEngine
import app.pickles.engine.ScoringSystem
import app.pickles.engine.TeamId
import app.pickles.ui.components.Chip
import app.pickles.ui.theme.PicklesColors
import app.pickles.ui.components.pressScale
import kotlin.random.Random

@Composable
fun SetupScreen(
    mode: GameMode,
    previous: MatchSetup?,
    onBack: () -> Unit,
    onStart: (MatchSetup) -> Unit,
    onHelp: () -> Unit,
) {
    val doubles = mode == GameMode.DOUBLES
    val defaultsA = if (doubles) listOf("Player A1", "Player A2") else listOf("Player A")
    val defaultsB = if (doubles) listOf("Player B1", "Player B2") else listOf("Player B")
    val namesA = remember { mutableStateListOf(*(previous?.teamA ?: defaultsA).toTypedArray()) }
    val namesB = remember { mutableStateListOf(*(previous?.teamB ?: defaultsB).toTypedArray()) }
    var startA by rememberSaveable { mutableIntStateOf(previous?.startingServerIndexA ?: 0) }
    var startB by rememberSaveable { mutableIntStateOf(previous?.startingServerIndexB ?: 0) }
    var firstServe by rememberSaveable { mutableStateOf(previous?.firstServingTeam ?: TeamId.A) }
    val ps = previous?.settings
    var scoring by rememberSaveable { mutableStateOf(ps?.scoring ?: ScoringSystem.SIDE_OUT) }
    var points by rememberSaveable { mutableIntStateOf(ps?.pointsToWin ?: 11) }
    var winBy by rememberSaveable { mutableIntStateOf(ps?.winBy ?: 2) }
    var bestOf by rememberSaveable { mutableIntStateOf(ps?.bestOf ?: 1) }
    var endChange by rememberSaveable { mutableStateOf(ps?.endChangeInDecidingGame ?: true) }
    var tossNote by remember { mutableStateOf<String?>(null) }

    val settings = MatchSettings(mode, scoring, points, winBy, bestOf, endChange)
    val namesValid = (namesA + namesB).all { PicklesEngine.cleanName(it) != null }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", fontSize = 18.sp, color = PicklesColors.Volt) }
            Spacer(Modifier.weight(1f))
            Text(if (doubles) "Doubles setup" else "Singles setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onHelp) { Text("Rules", color = PicklesColors.Volt) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TeamCard(TeamId.A, namesA, if (doubles) startA else null) { startA = it }
            TeamCard(TeamId.B, namesB, if (doubles) startB else null) { startB = it }

            Section("Who serves first?") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("Team A", firstServe == TeamId.A, Modifier.weight(1f), PicklesColors.TeamA) { firstServe = TeamId.A }
                    Chip("Team B", firstServe == TeamId.B, Modifier.weight(1f), PicklesColors.TeamB) { firstServe = TeamId.B }
                    TextButton(onClick = {
                        firstServe = if (Random.nextBoolean()) TeamId.A else TeamId.B
                        tossNote = "Coin toss: Team $firstServe serves first"
                    }) { Text("Toss", color = PicklesColors.Volt) }
                }
                Hint(tossNote ?: "Use any fair method, such as a coin toss.")
            }

            Section("Scoring") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("Side-out (standard)", scoring == ScoringSystem.SIDE_OUT, Modifier.weight(1f)) { scoring = ScoringSystem.SIDE_OUT }
                    Chip("Rally (provisional)", scoring == ScoringSystem.RALLY, Modifier.weight(1f)) { scoring = ScoringSystem.RALLY }
                }
                Hint(
                    if (scoring == ScoringSystem.SIDE_OUT) "Only the serving side scores (Rule 4.A)."
                    else "Every rally scores a point; one server per side (provisional Rule 14.A).",
                )
            }

            Section("Game to") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(11, 15, 21).forEach { p -> Chip("$p", points == p, Modifier.weight(1f)) { points = p } }
                    Stepper(points, onChange = { points = it.coerceIn(1, 99) })
                }
            }

            Section("Win by") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("2 (official)", winBy == 2, Modifier.weight(1f)) { winBy = 2 }
                    Chip("1 (recreational)", winBy == 1, Modifier.weight(1f)) { winBy = 1 }
                }
            }

            Section("Games") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("1 game", bestOf == 1, Modifier.weight(1f)) { bestOf = 1 }
                    Chip("Best of 3", bestOf == 3, Modifier.weight(1f)) { bestOf = 3 }
                    Chip("Best of 5", bestOf == 5, Modifier.weight(1f)) { bestOf = 5 }
                }
            }

            Section("End change") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Change ends mid-game", color = Color.White)
                        Hint("In a single game or deciding game, at ${settings.endChangeAt} points (Rule 21.B). Same server continues.")
                    }
                    Switch(checked = endChange, onCheckedChange = { endChange = it })
                }
            }

            FormatBadge(settings)
            Spacer(Modifier.height(8.dp))
        }
        val startSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Button(
            onClick = {
                onStart(
                    MatchSetup(
                        settings = settings,
                        teamA = namesA.toList(),
                        teamB = namesB.toList(),
                        startingServerIndexA = startA,
                        startingServerIndexB = startB,
                        firstServingTeam = firstServe,
                    ),
                )
            },
            enabled = namesValid,
            interactionSource = startSrc,
            modifier = Modifier.padding(16.dp).pressScale(startSrc).fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PicklesColors.Volt, contentColor = PicklesColors.Navy),
        ) { Text("Start match", fontSize = 20.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun TeamCard(team: TeamId, names: MutableList<String>, startingIndex: Int?, onStarting: (Int) -> Unit) {
    val color = PicklesColors.team(team)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PicklesColors.Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(10.dp).height(22.dp).background(color, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Text("Team ${team.name}", color = color, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        names.forEachIndexed { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= PicklesEngine.MAX_NAME) names[i] = it },
                    label = { Text(if (names.size == 1) "Player" else "Player ${i + 1}") },
                    singleLine = true,
                    isError = PicklesEngine.cleanName(name) == null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                if (startingIndex != null) {
                    Row(
                        Modifier.clickable { onStarting(i) }.padding(start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = startingIndex == i,
                            onClick = { onStarting(i) },
                            colors = RadioButtonDefaults.colors(selectedColor = color),
                        )
                        Text("Starts", color = PicklesColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        if (startingIndex != null) {
            Hint("The starting server begins on the right side and is the team's first server of the game (Rules 5.B.2, 5.B.3).")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PicklesColors.Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = PicklesColors.Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(value - 1) }) { Text("–", fontSize = 22.sp, color = PicklesColors.Volt) }
        Text("$value", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        TextButton(onClick = { onChange(value + 1) }) { Text("+", fontSize = 22.sp, color = PicklesColors.Volt) }
    }
}

@Composable
private fun FormatBadge(settings: MatchSettings) {
    val official = settings.isOfficialTournamentFormat
    val color = if (official) PicklesColors.Volt else PicklesColors.TeamA
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.14f)).padding(14.dp),
    ) {
        Text(
            if (official) "Official tournament format" else "Recreational format",
            color = color,
            fontWeight = FontWeight.Black,
        )
        Text(
            if (official) {
                "Matches a format listed in Rule 15.C" +
                    if (settings.scoring == ScoringSystem.RALLY) " (rally scoring is provisional, 15.C.2)." else "."
            } else {
                "Not a Rule 15.C tournament format. Fine for casual play."
            },
            color = PicklesColors.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
