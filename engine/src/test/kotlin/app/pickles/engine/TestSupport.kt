package app.pickles.engine

import kotlin.test.assertTrue
import kotlin.test.fail

/** Shared fixtures and helpers for engine tests. */
object Fixtures {
    fun singles(
        firstServing: TeamId = TeamId.A,
        pointsToWin: Int = 11,
        winBy: Int = 2,
        bestOf: Int = 1,
        scoring: ScoringSystem = ScoringSystem.SIDE_OUT,
        endChange: Boolean = false,
    ): MatchState = PicklesEngine.newMatch(
        MatchSetup(
            settings = MatchSettings(GameMode.SINGLES, scoring, pointsToWin, winBy, bestOf, endChange),
            teamA = listOf("Ana"),
            teamB = listOf("Cara"),
            firstServingTeam = firstServing,
        ),
        matchId = "test-singles",
    ).applied()

    /** Team A: Ana (A1), Ben (A2). Team B: Cara (B1), Dev (B2). */
    fun doubles(
        firstServing: TeamId = TeamId.A,
        startA: Int = 0,
        startB: Int = 0,
        pointsToWin: Int = 11,
        winBy: Int = 2,
        bestOf: Int = 1,
        scoring: ScoringSystem = ScoringSystem.SIDE_OUT,
        endChange: Boolean = false,
    ): MatchState = PicklesEngine.newMatch(
        MatchSetup(
            settings = MatchSettings(GameMode.DOUBLES, scoring, pointsToWin, winBy, bestOf, endChange),
            teamA = listOf("Ana", "Ben"),
            teamB = listOf("Cara", "Dev"),
            startingServerIndexA = startA,
            startingServerIndexB = startB,
            firstServingTeam = firstServing,
        ),
        matchId = "test-doubles",
    ).applied()
}

fun Transition.applied(): MatchState = when (this) {
    is Transition.Applied -> state
    is Transition.Rejected -> fail("Expected transition to apply but it was rejected: $reason ($message)")
}

fun MatchState.rally(winner: TeamId): MatchState =
    PicklesEngine.applyRallyResult(this, winner).applied().also(::assertValid)

fun MatchState.rallies(sequence: String): MatchState =
    sequence.filter { it == 'A' || it == 'B' }.fold(this) { s, c -> s.rally(TeamId.valueOf(c.toString())) }

fun MatchState.undone(): MatchState = PicklesEngine.undo(this).applied().also(::assertValid)

val MatchState.sit: CourtSituation get() = PicklesEngine.situation(this)

fun assertValid(state: MatchState) {
    val errors = PicklesEngine.validate(state)
    assertTrue(errors.isEmpty(), "Invariant violations: $errors")
}

/** Equality ignoring the monotonic revision counter (which deliberately keeps increasing on undo). */
fun MatchState.content(): MatchState = copy(revision = 0)
