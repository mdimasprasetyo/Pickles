package app.pickles.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Game/match completion, multi-game flow, end changes, setup validation and tap safety. */
class MatchFlowTest {

    // Scenario 16 - Rules 4.B, 15.C: game to 11, win by 2.
    @Test
    fun `game ends at 11 only with a two point lead`() {
        // A serves and wins 10 straight: 10-0.
        var s = Fixtures.singles().rallies("AAAAAAAAAA")
        assertEquals(MatchStatus.IN_PROGRESS, s.status)
        s = s.rally(TeamId.A)
        assertEquals(MatchStatus.COMPLETED, s.status)
        assertEquals(TeamId.A, s.matchWinner)
        assertTrue(s.lastRally!!.gameWon && s.lastRally!!.matchWon)
        assertEquals(listOf(GameResult(1, 11, 0, TeamId.A)), s.completedGames)
    }

    @Test
    fun `deuce requires win by two`() {
        // Build 10-10 in singles: A to 10, side out, B to 10.
        var s = Fixtures.singles().rallies("AAAAAAAAAA B BBBBBBBBBB")
        assertEquals(10, s.game.scoreA)
        assertEquals(10, s.game.scoreB)
        s = s.rally(TeamId.B) // B 11-10, not enough
        assertEquals(MatchStatus.IN_PROGRESS, s.status)
        s = s.rallies("A A") // side out to A, A 11-11
        assertEquals(11, s.game.scoreA)
        s = s.rally(TeamId.A) // 12-11
        assertEquals(MatchStatus.IN_PROGRESS, s.status)
        s = s.rally(TeamId.A) // 13-11
        assertEquals(MatchStatus.COMPLETED, s.status)
        assertEquals(TeamId.A, s.matchWinner)
    }

    @Test
    fun `win by one recreational setting ends at the target`() {
        var s = Fixtures.singles(pointsToWin = 5, winBy = 1).rallies("AAAA B BBBB")
        assertEquals(4, s.game.scoreA)
        assertEquals(4, s.game.scoreB)
        s = s.rally(TeamId.B)
        assertEquals(MatchStatus.COMPLETED, s.status)
        assertEquals(TeamId.B, s.matchWinner)
        assertFalse(s.settings.isOfficialTournamentFormat)
    }

    // Scenario 17 - invalid transitions are rejected.
    @Test
    fun `rally results are rejected after the match is completed`() {
        val done = Fixtures.singles(pointsToWin = 2).rallies("AA")
        assertEquals(MatchStatus.COMPLETED, done.status)
        val r = PicklesEngine.applyRallyResult(done, TeamId.B)
        assertIs<Transition.Rejected>(r)
        assertEquals(RejectReason.MATCH_COMPLETED, r.reason)
        assertIs<Transition.Rejected>(PicklesEngine.startNextGame(done))
    }

    // Scenario 17 - rapid repeated taps: a duplicate tap carrying the same rendered revision applies once.
    @Test
    fun `duplicate taps with the same rendered revision are applied once`() {
        val s = Fixtures.doubles()
        val rendered = s.revision
        val first = PicklesEngine.applyRallyResult(s, TeamId.A, rendered).applied()
        val second = PicklesEngine.applyRallyResult(first, TeamId.A, rendered)
        assertIs<Transition.Rejected>(second)
        assertEquals(RejectReason.STALE_REVISION, second.reason)
        assertEquals(1, first.game.scoreA)
        assertEquals(1, first.rallies.size)
        // Taps made after re-rendering are accepted.
        val third = PicklesEngine.applyRallyResult(first, TeamId.A, first.revision).applied()
        assertEquals(2, third.game.scoreA)
        // A stale undo is rejected too, and an undo cannot resurrect an old revision.
        assertIs<Transition.Rejected>(PicklesEngine.undo(third, rendered))
        val undone = PicklesEngine.undo(third, third.revision).applied()
        assertIs<Transition.Rejected>(PicklesEngine.applyRallyResult(undone, TeamId.A, first.revision))
    }

    @Test
    fun `many rapid sequential taps each produce exactly one rally`() {
        var s = Fixtures.doubles()
        repeat(500) { i ->
            if (s.status != MatchStatus.IN_PROGRESS) return@repeat
            val winner = if (i % 3 == 0) TeamId.B else TeamId.A
            val rev = s.revision
            s = PicklesEngine.applyRallyResult(s, winner, rev).applied()
            // the same tap delivered again is ignored
            assertIs<Transition.Rejected>(PicklesEngine.applyRallyResult(s, winner, rev))
            assertEquals(s.undoStack.size, s.log.size)
        }
        assertEquals(s.rallies.size, s.undoStack.size)
    }

    @Test
    fun `invalid setups are rejected`() {
        val base = MatchSetup(MatchSettings(GameMode.DOUBLES), listOf("a", "b"), listOf("c", "d"))
        assertIs<Transition.Applied>(PicklesEngine.newMatch(base))
        assertIs<Transition.Rejected>(PicklesEngine.newMatch(base.copy(teamA = listOf("a"))))
        assertIs<Transition.Rejected>(PicklesEngine.newMatch(base.copy(startingServerIndexB = 2)))
        assertIs<Transition.Rejected>(PicklesEngine.newMatch(base.copy(teamB = listOf("c", " "))))
        assertIs<Transition.Rejected>(PicklesEngine.newMatch(base.copy(settings = base.settings.copy(bestOf = 2))))
        assertIs<Transition.Rejected>(PicklesEngine.newMatch(base.copy(settings = base.settings.copy(winBy = 3))))
        assertIs<Transition.Rejected>(PicklesEngine.newMatch(base.copy(settings = base.settings.copy(pointsToWin = 0))))
        assertIs<Transition.Rejected>(
            PicklesEngine.newMatch(MatchSetup(MatchSettings(GameMode.SINGLES), listOf("a", "b"), listOf("c"))),
        )
    }

    // Rules 21.F.2, 21.F.3: best of 3, ends and initial service change; starting server may change.
    @Test
    fun `best of three flows through games with alternating first service`() {
        var s = Fixtures.doubles(bestOf = 3, pointsToWin = 3)
        s = s.rallies("AAA") // A wins game 1, 3-0 in the 0-0-2 rotation
        assertEquals(MatchStatus.BETWEEN_GAMES, s.status)
        assertIs<Transition.Rejected>(PicklesEngine.applyRallyResult(s, TeamId.A))
        s = PicklesEngine.startNextGame(s, startingServerB = "B2").applied()
        assertEquals(MatchStatus.IN_PROGRESS, s.status)
        assertEquals(2, s.game.gameNumber)
        assertEquals(TeamId.B, s.game.servingTeam) // initial service changes (21.F.2)
        assertTrue(s.game.endsSwapped) // ends change (21.F.2)
        assertEquals("B2", s.sit.server.id) // new starting server (21.F.3)
        assertEquals("0-0-2", s.sit.scoreCall)
        s = s.rallies("BBB") // B wins game 2
        assertEquals(MatchStatus.BETWEEN_GAMES, s.status)
        s = PicklesEngine.startNextGame(s).applied()
        assertEquals(TeamId.A, s.game.servingTeam)
        assertFalse(s.game.endsSwapped)
        s = s.rallies("AAA")
        assertEquals(MatchStatus.COMPLETED, s.status)
        assertEquals(TeamId.A, s.matchWinner)
        assertEquals(listOf(TeamId.A, TeamId.B, TeamId.A), s.completedGames.map { it.winner })
        // Undo the next-game start as well.
        val back = s.undone().undone().undone().undone()
        assertEquals(MatchStatus.BETWEEN_GAMES, back.status)
        assertEquals(2, back.game.gameNumber)
    }

    // Rule 21.B.1 & 21.B.4: end change at 6 in a single game to 11; same server continues.
    @Test
    fun `end change at the midpoint keeps the same server`() {
        var s = Fixtures.doubles(endChange = true).rallies("AAAAA")
        assertFalse(s.game.endsSwapped)
        val server = s.sit.server.id
        s = s.rally(TeamId.A)
        assertEquals(6, s.game.scoreA)
        assertTrue(s.game.endsSwapped)
        assertTrue(s.lastRally!!.endChange)
        assertEquals(server, s.sit.server.id)
        assertEquals("6-0-2", s.sit.scoreCall)
        s = s.rallies("AAAA B")
        assertTrue(s.game.endsSwapped) // only once
        assertNull(s.matchWinner)
    }

    @Test
    fun `official format classification`() {
        assertTrue(MatchSettings(GameMode.DOUBLES).isOfficialTournamentFormat)
        assertTrue(MatchSettings(GameMode.DOUBLES, pointsToWin = 11, bestOf = 3).isOfficialTournamentFormat)
        assertTrue(MatchSettings(GameMode.SINGLES, pointsToWin = 21).isOfficialTournamentFormat)
        assertFalse(MatchSettings(GameMode.SINGLES, pointsToWin = 21, bestOf = 3).isOfficialTournamentFormat)
        assertFalse(MatchSettings(GameMode.SINGLES, pointsToWin = 9).isOfficialTournamentFormat)
        assertFalse(MatchSettings(GameMode.SINGLES, winBy = 1).isOfficialTournamentFormat)
    }
}
