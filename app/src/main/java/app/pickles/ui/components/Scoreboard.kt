package app.pickles.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pickles.engine.CourtSituation
import app.pickles.engine.GameMode
import app.pickles.engine.MatchState
import app.pickles.engine.MatchStatus
import app.pickles.engine.ScoringSystem
import app.pickles.engine.TeamId
import app.pickles.ui.theme.PicklesColors
import app.pickles.ui.theme.ScoreDigits

/** Broadcast-style scoreboard. Purely derived from [MatchState] / [CourtSituation]. */
@Composable
fun Scoreboard(match: MatchState, sit: CourtSituation, modifier: Modifier = Modifier) {
    val live = match.status == MatchStatus.IN_PROGRESS
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(PicklesColors.Panel),
    ) {
        TeamRow(match, sit, TeamId.A, live)
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(PicklesColors.Navy))
        TeamRow(match, sit, TeamId.B, live)
        CallStrip(match, sit, live)
    }
}

@Composable
private fun TeamRow(match: MatchState, sit: CourtSituation, team: TeamId, live: Boolean) {
    val color = PicklesColors.team(team)
    val serving = live && sit.servingTeam == team
    val score = match.game.score(team)
    val rowBg by animateFloatAsState(if (serving) 1f else 0f, label = "rowbg")
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(color.copy(alpha = 0.10f * rowBg))
            .semantics {
                contentDescription = "Team ${team.name}, ${teamLabel(match, team)}, score $score" +
                    if (serving) ", serving" else ""
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(8.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TEAM ${team.name}", color = color, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
                if (serving) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (sit.serverNumber != null) "SERVING · ${sit.serverNumber}" else "SERVING",
                        color = PicklesColors.Navy,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PicklesColors.Volt).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                buildAnnotatedString {
                    match.team(team).forEachIndexed { i, p ->
                        if (i > 0) append("  /  ")
                        val isServer = serving && p.id == sit.server.id
                        if (isServer) {
                            withStyle(SpanStyle(color = PicklesColors.Volt, fontWeight = FontWeight.Black)) { append("● ${p.name}") }
                        } else {
                            withStyle(SpanStyle(color = Color.White)) { append(p.name) }
                        }
                    }
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (match.settings.bestOf > 1) {
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(match.settings.gamesNeededToWin) { i ->
                    Box(
                        Modifier.size(9.dp).clip(CircleShape)
                            .background(if (i < match.gamesWon(team)) color else PicklesColors.PanelHigh),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = score,
            transitionSpec = {
                (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
            },
            label = "score",
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.widthIn(min = 72.dp).padding(end = 12.dp),
        ) { value ->
            Text(
                "$value",
                style = ScoreDigits,
                color = if (serving) Color.White else Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun CallStrip(match: MatchState, sit: CourtSituation, live: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(PicklesColors.PanelHigh).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CALL", color = PicklesColors.Muted, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                if (live) sit.scoreCall.replace("-", " – ") else "—",
                color = PicklesColors.Volt,
                style = app.pickles.ui.theme.CallStyle,
                fontSize = 30.sp,
                modifier = Modifier.semantics { contentDescription = "Score call ${sit.scoreCall}" },
            )
            Spacer(Modifier.weight(1f))
            val format = buildString {
                append(if (match.settings.mode == GameMode.SINGLES) "Singles" else "Doubles")
                if (match.settings.bestOf > 1) append(" · G${match.game.gameNumber}/${match.settings.bestOf}")
                append(" · to ${match.settings.pointsToWin}")
                if (match.settings.scoring == ScoringSystem.RALLY) append(" · Rally")
            }
            Text(format, color = PicklesColors.Muted, fontSize = 11.sp)
        }
        if (live) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(sit.server.name) }
                    if (sit.serverNumber != null) append(" (server ${sit.serverNumber})")
                    append(" serves from the ")
                    withStyle(SpanStyle(color = PicklesColors.Volt, fontWeight = FontWeight.Bold)) {
                        append(if (sit.serveFrom == app.pickles.engine.Side.RIGHT) "RIGHT (even)" else "LEFT (odd)")
                    }
                    append(" court  →  ")
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append(sit.receiver.name) }
                    append(" receives")
                },
                color = PicklesColors.Muted,
                fontSize = 13.sp,
                maxLines = 2,
            )
            if (sit.isStartOfGameException) {
                Text(
                    "Start of game: only this server serves before the side out (0-0-2, Rule 5.B).",
                    color = PicklesColors.Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.alpha(0.9f),
                )
            }
        }
    }
}


/**
 * One-strip scoreboard for the landscape referee view. Teams are ordered to match the court:
 * [leftTeam] is drawn on the left, exactly where that team stands on the court above.
 */
@Composable
fun CompactScoreboard(match: MatchState, sit: CourtSituation, leftTeam: TeamId, modifier: Modifier = Modifier) {
    val live = match.status == MatchStatus.IN_PROGRESS
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(PicklesColors.Panel).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactTeam(match, sit, leftTeam, live, alignEnd = false, modifier = Modifier.weight(1f))
        Column(
            Modifier.weight(1.1f).fillMaxHeight().background(PicklesColors.PanelHigh).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (live) sit.scoreCall.replace("-", " – ") else "—",
                color = PicklesColors.Volt,
                style = app.pickles.ui.theme.CallStyle,
                fontSize = 28.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { contentDescription = "Score call ${sit.scoreCall}" },
            )
            val format = buildString {
                append(if (match.settings.mode == GameMode.SINGLES) "Singles" else "Doubles")
                if (match.settings.bestOf > 1) append(" · G${match.game.gameNumber}/${match.settings.bestOf}")
                append(" · to ${match.settings.pointsToWin}")
                if (match.settings.scoring == ScoringSystem.RALLY) append(" · Rally")
            }
            Text(
                if (live) {
                    val from = if (sit.serveFrom == app.pickles.engine.Side.RIGHT) "right" else "left"
                    "${sit.server.name} serves from the $from · $format"
                } else format,
                color = PicklesColors.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CompactTeam(match, sit, leftTeam.other, live, alignEnd = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompactTeam(match: MatchState, sit: CourtSituation, team: TeamId, live: Boolean, alignEnd: Boolean, modifier: Modifier) {
    val color = PicklesColors.team(team)
    val serving = live && sit.servingTeam == team
    val score = match.game.score(team)
    val rowBg by animateFloatAsState(if (serving) 1f else 0f, label = "cbg")
    Row(
        modifier.fillMaxHeight().background(color.copy(alpha = 0.12f * rowBg))
            .semantics {
                contentDescription = "Team ${team.name}, ${teamLabel(match, team)}, score $score" + if (serving) ", serving" else ""
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bar = @Composable { Box(Modifier.width(6.dp).fillMaxHeight().background(color)) }
        val digits = @Composable {
            AnimatedContent(
                targetState = score,
                transitionSpec = { (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) },
                label = "cscore",
                modifier = Modifier.padding(horizontal = 10.dp),
            ) { value -> Text("$value", style = ScoreDigits.copy(fontSize = 44.sp, lineHeight = 44.sp), color = Color.White) }
        }
        val info = @Composable { m: Modifier ->
            Column(m, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TEAM ${team.name}", color = color, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false)
                    if (serving) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (sit.serverNumber != null) "SERVING · ${sit.serverNumber}" else "SERVING",
                            color = PicklesColors.Navy, fontWeight = FontWeight.Black, fontSize = 9.sp, maxLines = 1, softWrap = false,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(PicklesColors.Volt).padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    if (match.settings.bestOf > 1) {
                        Spacer(Modifier.width(6.dp))
                        repeat(match.settings.gamesNeededToWin) { i ->
                            Box(Modifier.padding(horizontal = 1.dp).size(7.dp).clip(CircleShape)
                                .background(if (i < match.gamesWon(team)) color else PicklesColors.PanelHigh))
                        }
                    }
                }
                Text(
                    teamLabel(match, team),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!alignEnd) {
            bar(); info(Modifier.weight(1f).padding(start = 8.dp)); digits()
        } else {
            digits(); info(Modifier.weight(1f).padding(end = 8.dp)); bar()
        }
    }
}
