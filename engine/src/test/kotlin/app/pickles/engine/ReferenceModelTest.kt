package app.pickles.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-checks the engine (which derives positions from score parity + starting server, Rule 5.B.3)
 * against an independent "physical" model of how players actually move on court in traditional
 * side-out doubles: partners swap sides only when their team scores while serving; the receiving
 * team never moves; after a side out the player standing on the right serves first.
 * Thousands of random rally sequences must agree on every rally.
 */
class ReferenceModelTest {

    private class PhysicalDoubles(firstServing: TeamId, startA: String, startB: String, partnerA: String, partnerB: String) {
        val side = mutableMapOf(startA to Side.RIGHT, partnerA to Side.LEFT, startB to Side.RIGHT, partnerB to Side.LEFT)
        val teamOf = mapOf(startA to TeamId.A, partnerA to TeamId.A, startB to TeamId.B, partnerB to TeamId.B)
        val score = mutableMapOf(TeamId.A to 0, TeamId.B to 0)
        var serving = firstServing
        var server = if (firstServing == TeamId.A) startA else startB
        var serverNumber = 2

        fun partner(id: String) = teamOf.keys.first { it != id && teamOf[it] == teamOf[id] }
        fun onRight(team: TeamId) = teamOf.keys.first { teamOf[it] == team && side[it] == Side.RIGHT }
        val receiver: String get() = teamOf.keys.first { teamOf[it] == serving.other && side[it] == side[server] }

        fun rally(winner: TeamId) {
            if (winner == serving) {
                score[serving] = score.getValue(serving) + 1
                val p = partner(server)
                val tmp = side.getValue(server); side[server] = side.getValue(p); side[p] = tmp
            } else if (serverNumber == 1) {
                server = partner(server); serverNumber = 2
            } else {
                serving = serving.other
                server = onRight(serving)
                serverNumber = 1
            }
        }
    }

    @Test
    fun `engine matches the physical movement model over random doubles games`() {
        val rnd = Random(20260101)
        repeat(3000) { game ->
            val first = if (rnd.nextBoolean()) TeamId.A else TeamId.B
            val sa = rnd.nextInt(2)
            val sb = rnd.nextInt(2)
            var s = Fixtures.doubles(firstServing = first, startA = sa, startB = sb, pointsToWin = 99)
            val ref = PhysicalDoubles(
                first,
                "A${sa + 1}", "B${sb + 1}", "A${2 - sa}", "B${2 - sb}",
            )
            val bias = rnd.nextDouble(0.3, 0.7)
            repeat(rnd.nextInt(5, 120)) { r ->
                val winner = if (rnd.nextDouble() < bias) TeamId.A else TeamId.B
                s = s.rally(winner)
                ref.rally(winner)
                val sit = s.sit
                val ctx = "game $game rally ${r + 1}"
                assertEquals(ref.serving, sit.servingTeam, ctx)
                assertEquals(ref.server, sit.server.id, ctx)
                assertEquals(ref.serverNumber, sit.serverNumber, ctx)
                assertEquals(ref.side[ref.server], sit.serveFrom, ctx)
                assertEquals(ref.receiver, sit.receiver.id, ctx)
                assertEquals(ref.score[TeamId.A], s.game.scoreA, ctx)
                assertEquals(ref.score[TeamId.B], s.game.scoreB, ctx)
                for (id in listOf("A1", "A2", "B1", "B2")) {
                    assertEquals(ref.side[id], sit.position(id).side, "$ctx player $id")
                }
            }
        }
    }

    @Test
    fun `singles engine matches the parity model over random games`() {
        val rnd = Random(7)
        repeat(2000) {
            val first = if (rnd.nextBoolean()) TeamId.A else TeamId.B
            var s = Fixtures.singles(firstServing = first, pointsToWin = 99)
            var serving = first
            val score = mutableMapOf(TeamId.A to 0, TeamId.B to 0)
            repeat(rnd.nextInt(5, 150)) {
                val winner = if (rnd.nextBoolean()) TeamId.A else TeamId.B
                if (winner == serving) score[serving] = score.getValue(serving) + 1 else serving = serving.other
                s = s.rally(winner)
                assertEquals(serving, s.sit.servingTeam)
                val expectedSide = if (score.getValue(serving) % 2 == 0) Side.RIGHT else Side.LEFT
                assertEquals(expectedSide, s.sit.serveFrom)
                assertEquals(expectedSide, s.sit.receiveIn)
                assertEquals("${score[serving]}-${score[serving.other]}", s.sit.scoreCall)
            }
        }
    }

    @Test
    fun `undo then redo is deterministic over random sequences`() {
        val rnd = Random(99)
        repeat(300) {
            var s = Fixtures.doubles(pointsToWin = 15, bestOf = 3)
            repeat(rnd.nextInt(1, 80)) {
                if (s.status == MatchStatus.BETWEEN_GAMES) s = PicklesEngine.startNextGame(s).applied()
                if (s.status == MatchStatus.COMPLETED) return@repeat
                val w = if (rnd.nextBoolean()) TeamId.A else TeamId.B
                val next = s.rally(w)
                assertEquals(s.content(), next.undone().content())
                assertEquals(next.content(), next.undone().rally(w).content())
                s = next
            }
        }
    }
}
