package app.pickles.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** Singles service sequence. Rules 4.A, 5.A.1, 5.A.2, 5.A.3, 6.B.1. */
class SinglesTest {

    // Scenario 1 - Rule 5.A.1: server's score zero -> serve from the right; received in the right court.
    @Test
    fun `starting service is from the right even court`() {
        val s = Fixtures.singles()
        assertEquals(TeamId.A, s.sit.servingTeam)
        assertEquals("A1", s.sit.server.id)
        assertEquals(Side.RIGHT, s.sit.serveFrom)
        assertEquals("B1", s.sit.receiver.id)
        assertEquals(Side.RIGHT, s.sit.receiveIn)
        assertEquals("0-0", s.sit.scoreCall) // Rule 6.B.1
        assertEquals(null, s.sit.serverNumber)
    }

    // Scenario 2 - Rules 5.A.1, 5.A.2: server alternates sides after each point won.
    @Test
    fun `service court switches after each point`() {
        var s = Fixtures.singles()
        val expected = listOf(Side.LEFT, Side.RIGHT, Side.LEFT, Side.RIGHT)
        for ((i, side) in expected.withIndex()) {
            s = s.rally(TeamId.A)
            assertEquals(i + 1, s.game.scoreA)
            assertEquals("A1", s.sit.server.id)
            assertEquals(side, s.sit.serveFrom)
            assertEquals(side, s.sit.receiveIn)
        }
        assertEquals("4-0", s.sit.scoreCall)
    }

    // Scenario 3 - Rule 5.A.3: side out after the server loses a rally; no point to the receiver (4.A).
    @Test
    fun `side out after the server loses a rally`() {
        val s = Fixtures.singles().rallies("AAA").rally(TeamId.B)
        assertEquals(3, s.game.scoreA)
        assertEquals(0, s.game.scoreB)
        assertEquals(TeamId.B, s.sit.servingTeam)
        assertEquals("B1", s.sit.server.id)
        assertEquals(Side.RIGHT, s.sit.serveFrom) // B's score is 0 -> right (5.A.1)
        assertEquals(Side.RIGHT, s.sit.receiveIn)
        assertEquals("0-3", s.sit.scoreCall)
        assertEquals(RallyOutcome.SIDE_OUT, s.lastRally!!.outcome)
    }

    // Rule 5.A.1: the receiver's position follows the SERVER's score, not their own.
    @Test
    fun `receiver court follows the server score`() {
        // A reaches 3, side out, B scores 1 -> B serves from left (odd) and A receives in its left court.
        val s = Fixtures.singles().rallies("AAA B B")
        assertEquals(1, s.game.scoreB)
        assertEquals(Side.LEFT, s.sit.serveFrom)
        assertEquals(Side.LEFT, s.sit.receiveIn)
        assertEquals("1-3", s.sit.scoreCall)
    }

    // Scenario 18 (singles part) - the other player starting.
    @Test
    fun `team B serving first starts from the right and alternates correctly`() {
        var s = Fixtures.singles(firstServing = TeamId.B)
        assertEquals("B1", s.sit.server.id)
        assertEquals(Side.RIGHT, s.sit.serveFrom)
        s = s.rallies("BB A")
        assertEquals(2, s.game.scoreB)
        assertEquals("A1", s.sit.server.id)
        assertEquals("0-2", s.sit.scoreCall)
        s = s.rally(TeamId.A)
        assertEquals(Side.LEFT, s.sit.serveFrom)
        assertEquals("1-2", s.sit.scoreCall)
    }

    // Multi-rally sequence with a full expected trace (Rules 4.A, 5.A.1-3, 6.B.1).
    @Test
    fun `long singles sequence keeps score call and sides consistent`() {
        // (winner, expected call after, expected server, expected side)
        val trace = listOf(
            Triple('A', "1-0", "A1" to Side.LEFT),
            Triple('A', "2-0", "A1" to Side.RIGHT),
            Triple('B', "0-2", "B1" to Side.RIGHT),
            Triple('B', "1-2", "B1" to Side.LEFT),
            Triple('B', "2-2", "B1" to Side.RIGHT),
            Triple('B', "3-2", "B1" to Side.LEFT),
            Triple('A', "2-3", "A1" to Side.RIGHT),
            Triple('A', "3-3", "A1" to Side.LEFT),
            Triple('B', "3-3", "B1" to Side.LEFT),
            Triple('A', "3-3", "A1" to Side.LEFT),
        )
        var s = Fixtures.singles()
        for ((i, step) in trace.withIndex()) {
            s = s.rally(TeamId.valueOf(step.first.toString()))
            assertEquals(step.second, s.sit.scoreCall, "call after rally ${i + 1}")
            assertEquals(step.third.first, s.sit.server.id, "server after rally ${i + 1}")
            assertEquals(step.third.second, s.sit.serveFrom, "side after rally ${i + 1}")
            assertEquals(step.third.second, s.sit.receiveIn, "receiving court after rally ${i + 1}")
        }
    }
}
