package app.pickles.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Doubles side-out scoring. Rules 4.A, 5.B, 5.B.2, 5.B.3, 5.B.5, 5.B.6, 6.B.2, 7.E.
 * Team A: Ana (A1, starting server), Ben (A2). Team B: Cara (B1, starting server), Dev (B2).
 */
class DoublesTest {

    // Scenario 4 - Rules 5.B, 5.B.2, 6.B.2: game starts 0-0-2 with the starting server on the right.
    @Test
    fun `initial state is 0-0-2 with the starting server serving from the right`() {
        val s = Fixtures.doubles()
        val sit = s.sit
        assertEquals("0-0-2", sit.scoreCall)
        assertEquals(TeamId.A, sit.servingTeam)
        assertEquals("A1", sit.server.id)
        assertEquals(2, sit.serverNumber)
        assertEquals(Side.RIGHT, sit.serveFrom)
        assertEquals("B1", sit.receiver.id) // B's starting server is on the right at 0 (5.B.3)
        assertEquals(Side.RIGHT, sit.receiveIn)
        assertTrue(sit.isStartOfGameException)
    }

    // Rule 5.B: at the start of the game only the starting server serves before a side out.
    @Test
    fun `losing the first rally of the game is an immediate side out`() {
        val s = Fixtures.doubles().rally(TeamId.B)
        assertEquals(RallyOutcome.SIDE_OUT, s.lastRally!!.outcome)
        assertEquals(TeamId.B, s.sit.servingTeam)
        assertEquals("B1", s.sit.server.id)
        assertEquals(1, s.sit.serverNumber)
        assertEquals("0-0-1", s.sit.scoreCall)
        assertFalse(s.sit.isStartOfGameException)
    }

    // Scenario 5 - Rule 5.B.5: first server loses -> second server, same team, no point awarded.
    @Test
    fun `first server to second server transition`() {
        val s0 = Fixtures.doubles().rally(TeamId.B) // side out: B serving, Cara server 1, 0-0-1
        val s = s0.rally(TeamId.A)
        assertEquals(RallyOutcome.SECOND_SERVER, s.lastRally!!.outcome)
        assertEquals(TeamId.B, s.sit.servingTeam)
        assertEquals("B2", s.sit.server.id)
        assertEquals(2, s.sit.serverNumber)
        assertEquals("0-0-2", s.sit.scoreCall)
        assertEquals(Side.LEFT, s.sit.serveFrom) // Dev is the partner: left at even score (5.B.3)
        assertEquals("A2", s.sit.receiver.id) // A at 0: Ben (partner) on the left
        assertEquals(0, s.game.scoreA)
        assertEquals(0, s.game.scoreB)
    }

    // Scenario 6 - Rule 5.B.6: second server loses -> side out to the other team.
    @Test
    fun `side out after the second server loses a rally`() {
        val s = Fixtures.doubles().rallies("B A A") // A's 0-0-2 loses, B1 loses, B2 loses
        assertEquals(RallyOutcome.SIDE_OUT, s.lastRally!!.outcome)
        assertEquals(TeamId.A, s.sit.servingTeam)
        assertEquals(1, s.sit.serverNumber)
        assertEquals("A1", s.sit.server.id) // A at 0 -> starting server on the right is first server (5.B.2)
        assertEquals(Side.RIGHT, s.sit.serveFrom)
        assertEquals("0-0-1", s.sit.scoreCall)
    }

    // Scenario 7 - Rule 4.A: serving team scores; the server switches sides; partner does not "follow".
    @Test
    fun `serving team scores and server switches sides`() {
        var s = Fixtures.doubles().rally(TeamId.A)
        assertEquals(1, s.game.scoreA)
        assertEquals("1-0-2", s.sit.scoreCall)
        assertEquals("A1", s.sit.server.id)
        assertEquals(Side.LEFT, s.sit.serveFrom)
        assertEquals("B2", s.sit.receiver.id) // B at 0: Dev is on the left
        s = s.rally(TeamId.A)
        assertEquals("2-0-2", s.sit.scoreCall)
        assertEquals(Side.RIGHT, s.sit.serveFrom)
        assertEquals("B1", s.sit.receiver.id)
        // Receiving team's positions never changed: their score did not change (5.B.3).
        assertEquals(Side.RIGHT, s.sit.position("B1").side)
        assertEquals(Side.LEFT, s.sit.position("B2").side)
    }

    // Scenario 11 - Rule 4.A: the receiving team never gets a point for winning a rally.
    @Test
    fun `receiving team never scores under side-out scoring`() {
        var s = Fixtures.doubles()
        repeat(40) {
            val receiving = s.game.servingTeam.other
            val before = s.game.score(receiving)
            val beforeServing = s.game.score(s.game.servingTeam)
            s = s.rally(receiving)
            assertEquals(before, s.game.score(receiving))
            assertEquals(beforeServing, s.game.score(receiving.other))
        }
        assertEquals(0, s.game.scoreA)
        assertEquals(0, s.game.scoreB)
    }

    // Scenarios 8, 9, 10 - full trace: server identity, receiver identity and left/right courts
    // derived from score + starting positions (Rules 5.B.2, 5.B.3, 6.B.2, 7.E).
    @Test
    fun `rule fixture trace of server receiver and courts`() {
        data class Step(val winner: Char, val call: String, val server: String, val from: Side, val receiver: String)
        val trace = listOf(
            Step('A', "1-0-2", "A1", Side.LEFT, "B2"),
            Step('B', "0-1-1", "B1", Side.RIGHT, "A2"), // side out after the 0-0-2 rotation
            Step('A', "0-1-2", "B2", Side.LEFT, "A1"),
            Step('B', "1-1-2", "B2", Side.RIGHT, "A2"),
            Step('B', "2-1-2", "B2", Side.LEFT, "A1"),
            Step('A', "1-2-1", "A2", Side.RIGHT, "B1"), // A at 1: Ben (partner) is on the right
            Step('A', "2-2-1", "A2", Side.LEFT, "B2"),
            Step('B', "2-2-2", "A1", Side.RIGHT, "B1"),
            Step('A', "3-2-2", "A1", Side.LEFT, "B2"),
            Step('B', "2-3-1", "B1", Side.RIGHT, "A2"), // B at 2: Cara on the right
            Step('B', "3-3-1", "B1", Side.LEFT, "A1"),
            Step('A', "3-3-2", "B2", Side.RIGHT, "A2"),
            Step('A', "3-3-1", "A2", Side.RIGHT, "B2"), // A at 3: Ben on the right
        )
        var s = Fixtures.doubles()
        for ((i, step) in trace.withIndex()) {
            s = s.rally(TeamId.valueOf(step.winner.toString()))
            val sit = s.sit
            val n = i + 1
            assertEquals(step.call, sit.scoreCall, "call after rally $n")
            assertEquals(step.server, sit.server.id, "server after rally $n")
            assertEquals(step.from, sit.serveFrom, "serving court after rally $n")
            assertEquals(step.from, sit.receiveIn, "receiving court after rally $n")
            assertEquals(step.receiver, sit.receiver.id, "receiver after rally $n")
        }
    }

    // Scenario 18 - different starting positions: Ben (A2) and Dev (B2) are the starting servers.
    @Test
    fun `different starting servers change who serves and receives`() {
        var s = Fixtures.doubles(firstServing = TeamId.B, startA = 1, startB = 1)
        assertEquals("0-0-2", s.sit.scoreCall)
        assertEquals("B2", s.sit.server.id)
        assertEquals(Side.RIGHT, s.sit.serveFrom)
        assertEquals("A2", s.sit.receiver.id)
        assertEquals(Side.LEFT, s.sit.position("A1").side)
        s = s.rally(TeamId.B) // 1-0-2, Dev to the left
        assertEquals(Side.LEFT, s.sit.serveFrom)
        assertEquals("A1", s.sit.receiver.id)
        s = s.rally(TeamId.A) // side out; A at 0 -> Ben on the right is first server
        assertEquals("A2", s.sit.server.id)
        assertEquals(1, s.sit.serverNumber)
        assertEquals("0-1-1", s.sit.scoreCall)
        assertEquals(Side.RIGHT, s.sit.serveFrom)
        assertEquals("B1", s.sit.receiver.id) // B at 1 -> Cara (partner) on the right
    }

    // Rule 5.B.4 / 5.C: only the server and receiver have required positions.
    @Test
    fun `only server and receiver are required positions`() {
        val sit = Fixtures.doubles().rallies("A B A").sit
        val required = sit.positions.filter { it.required }.map { it.player.id }.toSet()
        assertEquals(setOf(sit.server.id, sit.receiver.id), required)
        assertEquals(2, sit.positions.count { !it.required })
        sit.positions.filter { !it.required }.forEach {
            assertTrue(it.role == Role.SERVER_PARTNER || it.role == Role.RECEIVER_PARTNER)
        }
    }

    // Partners are always on opposite reference sides; positions only change with their own team's score.
    @Test
    fun `positions change only when that team's score changes`() {
        var s = Fixtures.doubles()
        repeat(200) { i ->
            val before = s.sit
            val winner = if ((i * 7 + i / 3) % 3 == 0) TeamId.B else TeamId.A
            if (s.status != MatchStatus.IN_PROGRESS) return
            s = s.rally(winner)
            val after = s.sit
            for (team in TeamId.entries) {
                val ids = s.team(team).map { it.id }
                assertEquals(after.position(ids[0]).side.opposite, after.position(ids[1]).side)
                val prevScore = PicklesEngine.undo(s).applied().game.score(team)
                if (prevScore == s.game.score(team)) {
                    ids.forEach { assertEquals(before.position(it).side, after.position(it).side) }
                } else {
                    ids.forEach { assertEquals(before.position(it).side.opposite, after.position(it).side) }
                }
            }
        }
    }
}
