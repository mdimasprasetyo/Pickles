package app.pickles.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Provisional rally scoring option, Rule 14.A. Only active when explicitly selected. */
class RallyScoringTest {

    @Test
    fun `side-out scoring is the default`() {
        assertEquals(ScoringSystem.SIDE_OUT, MatchSettings(GameMode.DOUBLES).scoring)
        assertEquals("0-0-2", Fixtures.doubles().sit.scoreCall)
    }

    // Rules 14.A.2, 14.A.3, 14.A.4, 14.A.5
    @Test
    fun `doubles rally scoring awards every rally and uses one server`() {
        var s = Fixtures.doubles(scoring = ScoringSystem.RALLY)
        assertEquals("0-0", s.sit.scoreCall) // two-number call (14.A.3)
        assertEquals(null, s.sit.serverNumber)
        assertFalse(s.sit.isStartOfGameException)
        s = s.rally(TeamId.B) // receiving team wins: point AND serve (14.A.2, 14.A.5)
        assertEquals(1, s.game.scoreB)
        assertEquals(RallyOutcome.POINT_AND_SIDE_OUT, s.lastRally!!.outcome)
        assertEquals(TeamId.B, s.sit.servingTeam)
        // B at 1 (odd): Cara (starting server) is left, Dev is on the right -> Dev serves (14.A.4).
        assertEquals("B2", s.sit.server.id)
        assertEquals(Side.RIGHT, s.sit.serveFrom)
        assertEquals("1-0", s.sit.scoreCall)
        s = s.rally(TeamId.B)
        assertEquals("2-0", s.sit.scoreCall)
        assertEquals(Side.LEFT, s.sit.serveFrom)
        s = s.rally(TeamId.A) // A gets the point and the serve; A at 1 -> Ben on the right
        assertEquals(1, s.game.scoreA)
        assertEquals("A2", s.sit.server.id)
        assertEquals("1-2", s.sit.scoreCall)
    }

    @Test
    fun `singles rally scoring awards the receiver and passes serve`() {
        var s = Fixtures.singles(scoring = ScoringSystem.RALLY)
        s = s.rally(TeamId.B)
        assertEquals(1, s.game.scoreB)
        assertEquals(TeamId.B, s.sit.servingTeam)
        assertEquals(Side.LEFT, s.sit.serveFrom) // server's score 1 is odd
        assertEquals("1-0", s.sit.scoreCall)
    }

    @Test
    fun `rally scoring game can be won by the receiving team`() {
        var s = Fixtures.singles(scoring = ScoringSystem.RALLY, pointsToWin = 3).rallies("AA")
        s = s.rallies("BBB") // B scores 3 while A served first; 3-2 is not win by two
        assertEquals(MatchStatus.IN_PROGRESS, s.status)
        s = s.rally(TeamId.B)
        assertEquals(MatchStatus.COMPLETED, s.status)
        assertEquals(TeamId.B, s.matchWinner)
    }
}
