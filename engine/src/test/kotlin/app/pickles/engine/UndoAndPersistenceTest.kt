package app.pickles.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoAndPersistenceTest {

    // Scenario 12
    @Test
    fun `undo after a point restores the exact previous state`() {
        val before = Fixtures.doubles().rallies("A")
        val after = before.rally(TeamId.A)
        assertEquals(RallyOutcome.POINT, after.lastRally!!.outcome)
        val undone = after.undone()
        assertEquals(before.content(), undone.content())
        assertEquals("1-0-2", undone.sit.scoreCall)
        assertEquals(Side.LEFT, undone.sit.serveFrom)
        assertTrue(undone.revision > after.revision, "revision is monotonic so stale taps stay rejected")
    }

    // Scenario 13
    @Test
    fun `undo after a server transition`() {
        val before = Fixtures.doubles().rallies("B") // B serving, server 1
        val after = before.rally(TeamId.A) // -> server 2
        assertEquals(RallyOutcome.SECOND_SERVER, after.lastRally!!.outcome)
        val undone = after.undone()
        assertEquals(before.content(), undone.content())
        assertEquals("B1", undone.sit.server.id)
        assertEquals(1, undone.sit.serverNumber)
    }

    // Scenario 14
    @Test
    fun `undo after a side out`() {
        val before = Fixtures.doubles().rallies("A A")
        val after = before.rally(TeamId.B)
        assertEquals(RallyOutcome.SIDE_OUT, after.lastRally!!.outcome)
        val undone = after.undone()
        assertEquals(before.content(), undone.content())
        assertEquals(TeamId.A, undone.sit.servingTeam)
        assertEquals("2-0-2", undone.sit.scoreCall)
        assertEquals(2, undone.rallies.size)
    }

    @Test
    fun `undo all the way back to the initial state`() {
        val start = Fixtures.doubles()
        var s = start.rallies("ABBAABABBBAABAAABBA")
        while (s.canUndo) s = s.undone()
        assertEquals(start.content(), s.content())
        assertIs<Transition.Rejected>(PicklesEngine.undo(s))
    }

    @Test
    fun `undo reopens a completed match and a finished game`() {
        val nearEnd = Fixtures.singles(pointsToWin = 3).rallies("AA")
        val done = nearEnd.rally(TeamId.A)
        assertEquals(MatchStatus.COMPLETED, done.status)
        val reopened = done.undone()
        assertEquals(MatchStatus.IN_PROGRESS, reopened.status)
        assertEquals(nearEnd.content(), reopened.content())
    }

    // Scenario 15 - persistence round trip, then the match continues identically.
    @Test
    fun `match is restored after app restart and continues identically`() {
        val s = Fixtures.doubles(bestOf = 3, pointsToWin = 11).rallies("AABABBABAAABBBA")
        val restored = MatchCodec.decode(MatchCodec.encode(s))
        assertNotNull(restored)
        assertEquals(s, restored)
        val seq = "BABAABBBAAB"
        val a = s.rallies(seq)
        val b = restored.rallies(seq)
        assertEquals(a, b)
        assertEquals(a.content(), b.content())
        // Undo history survives the restart too.
        assertEquals(s.undone().content(), restored.undone().content())
    }

    @Test
    fun `corrupt or inconsistent saved data is rejected`() {
        assertNull(MatchCodec.decode("not json"))
        assertNull(MatchCodec.decode("{}"))
        val s = Fixtures.doubles().rallies("AB")
        val broken = MatchCodec.encode(s.copy(game = s.game.copy(serverId = "A1"))) // B is serving
        assertNull(MatchCodec.decode(broken))
    }

    @Test
    fun `renaming keeps identity and does not affect scoring or undo`() {
        val s = Fixtures.doubles().rallies("AAB")
        val renamed = PicklesEngine.renamePlayer(s, "A2", "  Benjamin  ").applied()
        assertEquals("Benjamin", renamed.player("A2").name)
        assertEquals(s.game, renamed.game)
        assertEquals(s.sit.server.id, renamed.sit.server.id)
        assertEquals("Benjamin", renamed.undone().player("A2").name)
        assertIs<Transition.Rejected>(PicklesEngine.renamePlayer(s, "A2", "   "))
        assertIs<Transition.Rejected>(PicklesEngine.renamePlayer(s, "Z9", "X"))
    }
}
