package app.pickles.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pickles.engine.CourtSituation
import app.pickles.engine.MatchState
import app.pickles.engine.PlayerPosition
import app.pickles.engine.RallyOutcome
import app.pickles.engine.Role
import app.pickles.engine.Side
import app.pickles.engine.TeamId
import app.pickles.ui.theme.PicklesColors
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Court geometry in feet (Rule 3.A): 20 ft x 44 ft, non-volley zone 7 ft each side of the net.
private const val COURT_W = 20f
private const val COURT_L = 44f
private const val NVZ = 7f
private const val MARGIN_X = 3f
private const val MARGIN_Y = 5.5f
private const val WORLD_W = COURT_W + 2 * MARGIN_X
private const val WORLD_H = COURT_L + 2 * MARGIN_Y

/**
 * Pure presentation of the match state on a top-down court. Everything drawn here comes from
 * [MatchState] and the [CourtSituation] the engine derived from it; the court has no click
 * handlers except the player name labels (which only open the rename dialog).
 *
 * Orientation: the "near" team is drawn at the bottom of the screen facing up, so its right side is
 * screen-right. The far team faces down, so its right side is screen-left.
 *
 * In [landscape] (the referee's view from the net post) the same court is rotated a quarter turn:
 * the "near" team stands on the LEFT facing the net (its right side is screen-down) and the other
 * team on the RIGHT (its right side is screen-up). Positions are computed in the portrait frame and
 * mapped with [toScreen], which is a pure rotation, so left/right for every player is preserved.
 */
@Composable
fun CourtView(
    match: MatchState,
    sit: CourtSituation,
    flipView: Boolean,
    onPlayerTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val bottomTeam = if (match.game.endsSwapped xor flipView) TeamId.B else TeamId.A

    fun isBottom(team: TeamId) = team == bottomTeam

    /** World x-range (feet, from left court edge) of a team's side. */
    fun sideX(team: TeamId, side: Side): Pair<Float, Float> {
        val screenRight = if (isBottom(team)) side == Side.RIGHT else side == Side.LEFT
        return if (screenRight) 10f to 20f else 0f to 10f
    }

    fun serviceCourtY(team: TeamId): Pair<Float, Float> =
        if (isBottom(team)) (COURT_L / 2 + NVZ) to COURT_L else 0f to (COURT_L / 2 - NVZ)

    fun markerPoint(p: PlayerPosition): Offset {
        val (x0, x1) = sideX(p.player.team, p.side)
        val x = (x0 + x1) / 2
        val bottom = isBottom(p.player.team)
        val y = when (p.role) {
            Role.SERVER -> if (bottom) COURT_L + 2.6f else -2.6f // behind the baseline, in the serving area
            Role.RECEIVER -> if (bottom) COURT_L - 3.2f else 3.2f
            Role.SERVER_PARTNER -> if (bottom) COURT_L - 3.2f else 3.2f // anywhere on their end (5.B.4)
            Role.RECEIVER_PARTNER -> if (bottom) COURT_L / 2 + NVZ + 2.5f else COURT_L / 2 - NVZ - 2.5f
        }
        return Offset(x + MARGIN_X, y + MARGIN_Y)
    }

    /** Portrait world point (feet incl. margins) -> on-screen world point (feet). */
    fun toScreen(o: Offset): Offset = if (landscape) Offset(WORLD_H - o.y, o.x) else o

    // Recent scoring event banner, keyed to the last rally.
    var banner by remember { mutableStateOf<Pair<String, Color>?>(null) }
    val flash = remember { Animatable(0f) }
    val last = match.lastRally
    LaunchedEffect(last?.number, match.game.gameNumber) {
        if (last == null) {
            banner = null
            return@LaunchedEffect
        }
        val color = PicklesColors.team(last.winner)
        banner = when {
            last.matchWon -> "Match to Team ${last.winner}" to color
            last.gameWon -> "Game to Team ${last.winner}" to color
            last.endChange -> "Point Team ${last.winner} · Change ends" to color
            last.outcome == RallyOutcome.POINT -> "Point Team ${last.winner}" to color
            last.outcome == RallyOutcome.POINT_AND_SIDE_OUT -> "Point Team ${last.winner} · Side out" to color
            last.outcome == RallyOutcome.SECOND_SERVER -> "Second server" to PicklesColors.Volt
            else -> "Side out" to PicklesColors.Volt
        }
        flash.snapTo(1f)
        flash.animateTo(0f, tween(900))
        kotlinx.coroutines.delay(900)
        if (!last.gameWon) banner = null
    }

    val infinite = rememberInfiniteTransition(label = "court")
    val dash by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "dash")
    val pulse by infinite.animateFloat(0.18f, 0.38f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val screenW = if (landscape) WORLD_H else WORLD_W
        val screenH = if (landscape) WORLD_W else WORLD_H
        val scaleDp: Dp = minOf(maxWidth / screenW, maxHeight / screenH)
        val worldWdp = scaleDp * screenW
        val worldHdp = scaleDp * screenH
        val density = LocalDensity.current

        Box(
            Modifier.size(worldWdp, worldHdp).semantics {
                contentDescription = "Court. ${sit.server.name} serves from the ${sideWord(sit.serveFrom)} " +
                    "to ${sit.receiver.name} in the ${sideWord(sit.receiveIn)} service court."
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
              val s = size.width / screenW
              // Landscape: rotate the portrait drawing 90 degrees clockwise about the origin and shift it
              // right by the portrait height, i.e. (x, y) -> (H - y, x), matching toScreen().
              withTransform({
                if (landscape) {
                    translate(left = WORLD_H * s)
                    rotate(90f, pivot = Offset.Zero)
                }
              }) {
                fun o(x: Float, y: Float) = Offset((x + MARGIN_X) * s, (y + MARGIN_Y) * s)
                fun rect(x0: Float, y0: Float, x1: Float, y1: Float, color: Color) =
                    drawRect(color, o(x0, y0), Size((x1 - x0) * s, (y1 - y0) * s))

                // Court surface (no surround fill: the court sits directly on the app background).
                rect(0f, 0f, COURT_W, COURT_L, PicklesColors.Court)
                rect(0f, COURT_L / 2 - NVZ, COURT_W, COURT_L / 2 + NVZ, PicklesColors.Kitchen)

                // Serving team's end tint and recent-event flash on the rally winner's end.
                fun endRect(team: TeamId, color: Color) {
                    if (isBottom(team)) rect(0f, COURT_L / 2, COURT_W, COURT_L, color) else rect(0f, 0f, COURT_W, COURT_L / 2, color)
                }
                endRect(sit.servingTeam, PicklesColors.team(sit.servingTeam).copy(alpha = 0.10f))
                last?.let { if (flash.value > 0f) endRect(it.winner, PicklesColors.team(it.winner).copy(alpha = 0.35f * flash.value)) }

                // Correct receiving court (Rule 7.E): pulsing highlight.
                val (rx0, rx1) = sideX(sit.receivingTeam, sit.receiveIn)
                val (ry0, ry1) = serviceCourtY(sit.receivingTeam)
                rect(rx0, ry0, rx1, ry1, PicklesColors.Volt.copy(alpha = pulse))

                // Serving area behind the baseline on the correct side (Rules 5.A.1, 5.B.3, 7.A.3).
                val (sx0, sx1) = sideX(sit.servingTeam, sit.serveFrom)
                val serveTeamBottom = isBottom(sit.servingTeam)
                val sy0 = if (serveTeamBottom) COURT_L else -4.6f
                val sy1 = if (serveTeamBottom) COURT_L + 4.6f else 0f
                rect(sx0, sy0, sx1, sy1, PicklesColors.team(sit.servingTeam).copy(alpha = 0.35f))

                // Lines (Rule 3.A.4).
                val line = 0.22f * s
                val white = PicklesColors.Line
                drawRect(white, o(0f, 0f), Size(COURT_W * s, COURT_L * s), style = Stroke(line)) // baselines + sidelines
                drawLine(white, o(0f, COURT_L / 2 - NVZ), o(COURT_W, COURT_L / 2 - NVZ), line) // NVZ lines
                drawLine(white, o(0f, COURT_L / 2 + NVZ), o(COURT_W, COURT_L / 2 + NVZ), line)
                drawLine(white, o(10f, 0f), o(10f, COURT_L / 2 - NVZ), line) // centerlines
                drawLine(white, o(10f, COURT_L / 2 + NVZ), o(10f, COURT_L), line)
                // Outline the receiving court in volt.
                drawRect(PicklesColors.Volt, o(rx0, ry0), Size((rx1 - rx0) * s, (ry1 - ry0) * s), style = Stroke(line * 1.6f))

                // Net with posts just outside the sidelines.
                drawLine(Color(0xFF0E1B2E), o(-1f, COURT_L / 2), o(COURT_W + 1f, COURT_L / 2), 0.55f * s)
                drawLine(Color(0xFFE8EEF7), o(-1f, COURT_L / 2), o(COURT_W + 1f, COURT_L / 2), 0.14f * s)
                drawCircle(Color(0xFFE8EEF7), 0.45f * s, o(-1f, COURT_L / 2))
                drawCircle(Color(0xFFE8EEF7), 0.45f * s, o(COURT_W + 1f, COURT_L / 2))

                // Serve arrow: from the server's spot to the centre of the receiving court.
                val from = sit.positions.first { it.role == Role.SERVER }.let(::markerPoint)
                val to = Offset((rx0 + rx1) / 2 + MARGIN_X, (ry0 + ry1) / 2 + MARGIN_Y)
                drawServeArrow(from * s, to * s, s, dash)
              }
            }

            // End labels (which team is at which end).
            TeamEndTag(
                "TEAM ${bottomTeam.name}", PicklesColors.team(bottomTeam),
                Modifier.align(if (landscape) Alignment.TopStart else Alignment.BottomStart).padding(4.dp),
            )
            TeamEndTag(
                "TEAM ${bottomTeam.other.name}", PicklesColors.team(bottomTeam.other),
                Modifier.align(if (landscape) Alignment.TopEnd else Alignment.TopStart).padding(4.dp),
            )

            // Players.
            sit.positions.forEach { p ->
                val target = toScreen(markerPoint(p))
                val ax = remember(p.player.id, landscape) { Animatable(target.x) }
                val ay = remember(p.player.id, landscape) { Animatable(target.y) }
                LaunchedEffect(target) {
                    kotlinx.coroutines.coroutineScope {
                        launch { ax.animateTo(target.x, tween(420)) }
                        launch { ay.animateTo(target.y, tween(420)) }
                    }
                }
                var markerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                val px = with(density) { (scaleDp * ax.value).toPx() }
                val py = with(density) { (scaleDp * ay.value).toPx() }
                PlayerMarker(
                    p = p,
                    serverNumber = sit.serverNumber,
                    maxLabel = scaleDp * if (landscape) 8f else 9.5f,
                    onTap = { onPlayerTap(p.player.id) },
                    modifier = Modifier
                        .onSizeChanged { markerSize = it }
                        .offset { IntOffset((px - markerSize.width / 2f).roundToInt(), (py - markerSize.height * 0.30f).roundToInt()) },
                )
            }

            // Recent event banner.
            AnimatedVisibility(
                visible = banner != null,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                val (text, color) = banner ?: ("" to Color.White)
                Text(
                    text,
                    color = PicklesColors.Navy,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private operator fun Offset.times(f: Float) = Offset(x * f, y * f)

private fun DrawScope.drawServeArrow(from: Offset, to: Offset, s: Float, phase: Float) {
    val angle = atan2(to.y - from.y, to.x - from.x)
    val head = 1.6f * s
    val end = Offset(to.x - cos(angle) * head * 0.6f, to.y - sin(angle) * head * 0.6f)
    val dashLen = 1.1f * s
    drawLine(
        PicklesColors.Volt,
        from,
        end,
        strokeWidth = 0.38f * s,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, dashLen * 0.8f), -phase * dashLen * 1.8f),
    )
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(to.x - cos(angle - 0.45f) * head, to.y - sin(angle - 0.45f) * head)
        lineTo(to.x - cos(angle + 0.45f) * head, to.y - sin(angle + 0.45f) * head)
        close()
    }
    drawPath(path, PicklesColors.Volt)
}

@Composable
private fun TeamEndTag(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        color = PicklesColors.Navy,
        fontWeight = FontWeight.Black,
        fontSize = 10.sp,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun PlayerMarker(p: PlayerPosition, serverNumber: Int?, maxLabel: Dp, onTap: () -> Unit, modifier: Modifier) {
    val teamColor = PicklesColors.team(p.player.team)
    val required = p.required
    val badge = when (p.role) {
        Role.SERVER -> if (serverNumber != null) "SERVER $serverNumber" else "SERVER"
        Role.RECEIVER -> "RECEIVER"
        else -> "FLEXIBLE"
    }
    Column(
        modifier
            .alpha(if (required) 1f else 0.62f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Edit name of ${p.player.name}", onClick = onTap)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (required) teamColor else teamColor.copy(alpha = 0.35f))
                .border(
                    width = if (p.role == Role.SERVER) 3.dp else 2.dp,
                    color = when (p.role) {
                        Role.SERVER -> PicklesColors.Volt
                        Role.RECEIVER -> Color.White
                        else -> teamColor
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (p.role == Role.SERVER) "●" else initials(p.player.name),
                color = if (p.role == Role.SERVER) PicklesColors.Volt.copy(alpha = 1f) else PicklesColors.Navy,
                fontWeight = FontWeight.Black,
                fontSize = if (p.role == Role.SERVER) 14.sp else 12.sp,
            )
        }
        Text(
            p.player.name,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = maxLabel)
                .clip(RoundedCornerShape(6.dp))
                .background(PicklesColors.Navy.copy(alpha = 0.78f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Text(
            badge,
            color = when (p.role) {
                Role.SERVER -> PicklesColors.Volt
                Role.RECEIVER -> Color.White
                else -> PicklesColors.Muted
            },
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
        )
    }
}

private fun initials(name: String): String =
    name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

fun sideWord(side: Side): String = if (side == Side.RIGHT) "right" else "left"
