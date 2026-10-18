package app.pickles.engine

import java.util.UUID

/**
 * The deterministic scoring engine. Every function is pure: it takes a [MatchState]
 * and returns a new one (or a rejection). Nothing here touches the UI or storage.
 *
 * Rule references (2026 USA Pickleball Official Rulebook):
 *  - 4.A    Standard scoring: a point is scored only by serving and winning the rally.
 *  - 4.B    The first side to score the winning point wins the game.
 *  - 5.A.1  Singles: server's score even -> serve from right, received in opponent's right court; odd -> left.
 *  - 5.A.2  Singles: server keeps serving, alternating sides after each point, until losing a rally.
 *  - 5.A.3  Singles: side out after the server loses a rally.
 *  - 5.B    Doubles: both players serve before a side out, except at the start of each game.
 *  - 5.B.2  After a side out, the player correctly positioned on the right per the team score is the
 *           first server; partner is second server. The starting server of the team that starts the
 *           game is designated second server for that first rotation ("0-0-2").
 *  - 5.B.3  Correct positions: the starting server is on the right when the team score is zero/even and
 *           on the left when odd; the partner is on the opposite side.
 *  - 5.B.4  Except while serving and receiving, there is no restriction on player positions.
 *  - 5.B.5  First server serves until the team loses a rally, then the second server serves.
 *  - 5.B.6  Second server serves until the team loses a rally, which results in a side out.
 *  - 6.B.1  Singles score call: server's score, receiver's score.
 *  - 6.B.2  Doubles score call: serving score, receiving score, server number; games start "0-0-2".
 *  - 7.E    Serve goes to the service court diagonally opposite the server's correct position.
 *  - 14.A   (Provisional) rally scoring: rally winner scores; one server per side; two-number call.
 *  - 15.C   Tournament formats; all win by two (win by one only for team play).
 *  - 21.B   End change at 6 / 8 / 11 in a single game or deciding game to 11 / 15 / 21.
 *  - 21.F.2 Change ends and initial service for the next game of a match.
 *  - 21.F.3 Teams may change their starting server between games.
 */
object PicklesEngine {

    // ------------------------------------------------------------------ setup

    /** Creates a rule-compliant initial match state, or rejects an invalid setup. */
    fun newMatch(setup: MatchSetup, matchId: String = UUID.randomUUID().toString()): Transition {
        val s = setup.settings
        val size = if (s.mode == GameMode.SINGLES) 1 else 2
        fun reject(msg: String) = Transition.Rejected(RejectReason.INVALID_SETUP, msg)
        if (setup.teamA.size != size || setup.teamB.size != size) return reject("Each team needs $size player(s)")
        if (s.pointsToWin !in 1..99) return reject("Points to win must be between 1 and 99")
        if (s.winBy !in 1..2) return reject("Win by must be 1 or 2")
        if (s.bestOf !in setOf(1, 3, 5)) return reject("A match is 1, best of 3, or best of 5 games")
        if (setup.startingServerIndexA !in 0 until size || setup.startingServerIndexB !in 0 until size) {
            return reject("Starting server must be a player on the team")
        }
        val names = (setup.teamA + setup.teamB).map { cleanName(it) }
        if (names.any { it == null }) return reject("Player names must be 1-$MAX_NAME characters")

        val players = buildList {
            setup.teamA.forEachIndexed { i, n -> add(Player("A${i + 1}", cleanName(n)!!, TeamId.A)) }
            setup.teamB.forEachIndexed { i, n -> add(Player("B${i + 1}", cleanName(n)!!, TeamId.B)) }
        }
        val startA = "A${setup.startingServerIndexA + 1}"
        val startB = "B${setup.startingServerIndexB + 1}"
        val game = initialGame(
            settings = s,
            gameNumber = 1,
            firstServingTeam = setup.firstServingTeam,
            startingServerA = startA,
            startingServerB = startB,
            endsSwapped = false,
        )
        val state = MatchState(
            matchId = matchId,
            setup = setup,
            settings = s,
            players = players,
            status = MatchStatus.IN_PROGRESS,
            game = game,
            completedGames = emptyList(),
            lastRally = null,
            log = emptyList(),
            undoStack = emptyList(),
            revision = 0,
        )
        check(validate(state).isEmpty()) { "newMatch produced an invalid state: ${validate(state)}" }
        return Transition.Applied(state)
    }

    /**
     * First server of a game: the serving team's starting server.
     * Side-out doubles: designated second server for the first rotation (Rule 5.B.2), called 0-0-2 (6.B.2).
     */
    private fun initialGame(
        settings: MatchSettings,
        gameNumber: Int,
        firstServingTeam: TeamId,
        startingServerA: String,
        startingServerB: String,
        endsSwapped: Boolean,
    ): GameState {
        val server = if (firstServingTeam == TeamId.A) startingServerA else startingServerB
        val serverNumber = if (usesTwoServers(settings)) 2 else 1
        return GameState(
            gameNumber = gameNumber,
            scoreA = 0,
            scoreB = 0,
            servingTeam = firstServingTeam,
            serverId = server,
            serverNumber = serverNumber,
            firstServingTeam = firstServingTeam,
            startingServerA = startingServerA,
            startingServerB = startingServerB,
            endsSwapped = endsSwapped,
        )
    }

    /** Both partners serve in each rotation only in side-out doubles (Rule 5.B; 14.A.5 removes it). */
    fun usesTwoServers(settings: MatchSettings): Boolean =
        settings.mode == GameMode.DOUBLES && settings.scoring == ScoringSystem.SIDE_OUT

    // ------------------------------------------------------------------ positions

    /**
     * Correct side for a doubles player when serving or receiving (Rule 5.B.3).
     * Starting server: RIGHT on zero/even team score, LEFT on odd. Partner: the opposite.
     */
    fun doublesCorrectSide(playerId: String, teamScore: Int, startingServerId: String): Side {
        val startingServerSide = Side.forScore(teamScore)
        return if (playerId == startingServerId) startingServerSide else startingServerSide.opposite
    }

    /** Player on [team] who is correctly positioned on [side] for the team's current score (Rule 5.B.3). */
    private fun doublesPlayerOn(state: MatchState, game: GameState, team: TeamId, side: Side): Player =
        state.team(team).first { doublesCorrectSide(it.id, game.score(team), game.startingServer(team)) == side }

    /** Derives server, receiver, sides, required positions and the score call from [state]. */
    fun situation(state: MatchState): CourtSituation {
        val g = state.game
        val serving = g.servingTeam
        val receiving = serving.other
        val server = state.player(g.serverId)
        val servingScore = g.score(serving)
        val receivingScore = g.score(receiving)
        val twoServers = usesTwoServers(state.settings)

        return when (state.settings.mode) {
            GameMode.SINGLES -> {
                // Rule 5.A.1: both positions are determined by the server's score.
                val side = Side.forScore(servingScore)
                val receiver = state.team(receiving).single()
                CourtSituation(
                    servingTeam = serving,
                    receivingTeam = receiving,
                    server = server,
                    serverNumber = null,
                    serveFrom = side,
                    receiver = receiver,
                    receiveIn = side,
                    positions = listOf(
                        PlayerPosition(server, side, Role.SERVER),
                        PlayerPosition(receiver, side, Role.RECEIVER),
                    ),
                    servingScore = servingScore,
                    receivingScore = receivingScore,
                    scoreCall = "$servingScore-$receivingScore",
                    isStartOfGameException = false,
                )
            }
            GameMode.DOUBLES -> {
                // Rule 5.B.3: serving side comes from the server's team score and starting position.
                val serveFrom = doublesCorrectSide(server.id, servingScore, g.startingServer(serving))
                // Rule 7.E: received diagonally; the correct receiver is the receiving player whose
                // correct side (by the receiving team's score, Rule 5.B.3) matches the serving side.
                val receiver = doublesPlayerOn(state, g, receiving, serveFrom)
                val positions = state.players.map { p ->
                    val teamScore = g.score(p.team)
                    val side = doublesCorrectSide(p.id, teamScore, g.startingServer(p.team))
                    val role = when {
                        p.id == server.id -> Role.SERVER
                        p.id == receiver.id -> Role.RECEIVER
                        p.team == serving -> Role.SERVER_PARTNER
                        else -> Role.RECEIVER_PARTNER
                    }
                    PlayerPosition(p, side, role)
                }
                val call = if (twoServers) {
                    "$servingScore-$receivingScore-${g.serverNumber}" // Rule 6.B.2
                } else {
                    "$servingScore-$receivingScore" // Rule 14.A.3
                }
                CourtSituation(
                    servingTeam = serving,
                    receivingTeam = receiving,
                    server = server,
                    serverNumber = if (twoServers) g.serverNumber else null,
                    serveFrom = serveFrom,
                    receiver = receiver,
                    receiveIn = serveFrom,
                    positions = positions,
                    servingScore = servingScore,
                    receivingScore = receivingScore,
                    scoreCall = call,
                    isStartOfGameException = twoServers && isFirstRotationOfGame(g),
                )
            }
        }
    }

    /**
     * True while the team that started the game is still in its single-server first rotation
     * (Rules 5.B, 5.B.2): no side out has happened yet in this game.
     */
    private fun isFirstRotationOfGame(g: GameState): Boolean = !g.firstRotationOver

    // ------------------------------------------------------------------ transitions

    /**
     * nextState = applyRallyResult(currentState, rallyWinner).
     *
     * @param expectedRevision when non-null, the transition is rejected unless it matches the state's
     *   revision. The UI passes the revision it rendered, so a duplicate tap on a stale screen is
     *   processed at most once.
     */
    fun applyRallyResult(state: MatchState, rallyWinner: TeamId, expectedRevision: Long? = null): Transition {
        when (state.status) {
            MatchStatus.COMPLETED -> return Transition.Rejected(RejectReason.MATCH_COMPLETED, "The match is over")
            MatchStatus.BETWEEN_GAMES -> return Transition.Rejected(RejectReason.GAME_NOT_IN_PROGRESS, "Start the next game first")
            MatchStatus.IN_PROGRESS -> Unit
        }
        if (expectedRevision != null && expectedRevision != state.revision) {
            return Transition.Rejected(RejectReason.STALE_REVISION, "Ignored a duplicate or stale tap")
        }

        val s = state.settings
        val g = state.game
        val serving = g.servingTeam
        val outcome: RallyOutcome
        var next: GameState

        when (s.scoring) {
            ScoringSystem.SIDE_OUT -> {
                if (rallyWinner == serving) {
                    // Rule 4.A: the serving side scores. The server keeps serving and, because the
                    // team score parity changes, now serves from the other side (5.A.2, 5.B.5, 5.B.6).
                    next = g.addPoint(serving)
                    outcome = RallyOutcome.POINT
                } else if (usesTwoServers(s) && g.serverNumber == 1) {
                    // Rule 5.B.5: first server's team lost the rally -> partner serves as server 2.
                    // No point for the receiving team (Rule 4.A).
                    val partner = state.team(serving).first { it.id != g.serverId }
                    next = g.copy(serverId = partner.id, serverNumber = 2)
                    outcome = RallyOutcome.SECOND_SERVER
                } else {
                    // Singles: Rule 5.A.3. Doubles server 2 (incl. the 0-0-2 starting server): Rules 5.B, 5.B.6.
                    // No point for the receiving team (Rule 4.A).
                    next = sideOut(state, g)
                    outcome = RallyOutcome.SIDE_OUT
                }
            }
            ScoringSystem.RALLY -> {
                // Rule 14.A.2: the rally winner scores.
                next = g.addPoint(rallyWinner)
                outcome = if (rallyWinner == serving) {
                    RallyOutcome.POINT
                } else {
                    // Rule 14.A.5: one server; losing the rally is a side out.
                    next = sideOut(state, next)
                    RallyOutcome.POINT_AND_SIDE_OUT
                }
            }
        }

        // Rule 4.B / 15.C: game won at the target score with the required margin.
        val scorer = when (outcome) {
            RallyOutcome.POINT -> serving
            RallyOutcome.POINT_AND_SIDE_OUT -> rallyWinner
            else -> null
        }
        var gameWon = false
        var matchWon = false
        var endChange = false
        var status = MatchStatus.IN_PROGRESS
        var completed = state.completedGames
        if (scorer != null) {
            val mine = next.score(scorer)
            val theirs = next.score(scorer.other)
            if (mine >= s.pointsToWin && mine - theirs >= s.winBy) {
                gameWon = true
                next = next.copy(winner = scorer)
                completed = completed + GameResult(next.gameNumber, next.scoreA, next.scoreB, scorer)
                matchWon = completed.count { it.winner == scorer } >= s.gamesNeededToWin
                status = if (matchWon) MatchStatus.COMPLETED else MatchStatus.BETWEEN_GAMES
            } else if (s.endChangeInDecidingGame && !next.endChangeDone && isDecidingGame(s, next.gameNumber) &&
                maxOf(next.scoreA, next.scoreB) >= s.endChangeAt
            ) {
                // Rule 21.B: change ends at the midpoint; 21.B.4: same server continues.
                endChange = true
                next = next.copy(endsSwapped = !next.endsSwapped, endChangeDone = true)
            }
        }

        val record = RallyRecord(
            number = state.rallies.size + 1,
            gameNumber = g.gameNumber,
            winner = rallyWinner,
            outcome = outcome,
            scoreAAfter = next.scoreA,
            scoreBAfter = next.scoreB,
            gameWon = gameWon,
            matchWon = matchWon,
            endChange = endChange,
        )
        val newState = state.copy(
            status = status,
            game = next,
            completedGames = completed,
            lastRally = record,
            log = state.log + MatchEvent.Rally(record),
            undoStack = state.undoStack + state.snapshot(),
            revision = state.revision + 1,
        )
        return Transition.Applied(newState)
    }

    /**
     * Side out (Rules 5.A.3, 5.B.2, 5.B.6, 14.A.4): serve passes to the opponents. In doubles their
     * first server is the player correctly positioned on the right for their score.
     */
    private fun sideOut(state: MatchState, g: GameState): GameState {
        val newServing = g.servingTeam.other
        val server = when (state.settings.mode) {
            GameMode.SINGLES -> state.team(newServing).single()
            GameMode.DOUBLES -> doublesPlayerOn(state, g, newServing, Side.RIGHT)
        }
        return g.copy(servingTeam = newServing, serverId = server.id, serverNumber = 1, firstRotationOver = true)
    }

    private fun GameState.addPoint(team: TeamId): GameState =
        if (team == TeamId.A) copy(scoreA = scoreA + 1) else copy(scoreB = scoreB + 1)

    /** Single-game match, or the last possible game of a multi-game match (Rule 21.B). */
    fun isDecidingGame(settings: MatchSettings, gameNumber: Int): Boolean =
        settings.bestOf == 1 || gameNumber == settings.bestOf

    /**
     * Starts the next game of a multi-game match. Ends and initial service change (Rule 21.F.2);
     * each team may change its starting server (Rule 21.F.3). Undoable.
     */
    fun startNextGame(
        state: MatchState,
        startingServerA: String? = null,
        startingServerB: String? = null,
        expectedRevision: Long? = null,
    ): Transition {
        if (state.status != MatchStatus.BETWEEN_GAMES) {
            return Transition.Rejected(RejectReason.NOT_BETWEEN_GAMES, "No game is waiting to start")
        }
        if (expectedRevision != null && expectedRevision != state.revision) {
            return Transition.Rejected(RejectReason.STALE_REVISION, "Ignored a duplicate or stale tap")
        }
        val g = state.game
        val a = startingServerA ?: g.startingServerA
        val b = startingServerB ?: g.startingServerB
        if (state.players.none { it.id == a && it.team == TeamId.A } || state.players.none { it.id == b && it.team == TeamId.B }) {
            return Transition.Rejected(RejectReason.INVALID_PLAYER, "Starting server must be on their team")
        }
        val nextNumber = g.gameNumber + 1
        val game = initialGame(
            settings = state.settings,
            gameNumber = nextNumber,
            firstServingTeam = g.firstServingTeam.other,
            startingServerA = a,
            startingServerB = b,
            endsSwapped = !g.endsSwapped,
        )
        return Transition.Applied(
            state.copy(
                status = MatchStatus.IN_PROGRESS,
                game = game,
                lastRally = null,
                log = state.log + MatchEvent.NextGame(nextNumber),
                undoStack = state.undoStack + state.snapshot(),
                revision = state.revision + 1,
            ),
        )
    }

    /** Restores the complete previous state: scores, server, number, sides, status and rally history. */
    fun undo(state: MatchState, expectedRevision: Long? = null): Transition {
        val previous = state.undoStack.lastOrNull()
            ?: return Transition.Rejected(RejectReason.NOTHING_TO_UNDO, "Nothing to undo")
        if (expectedRevision != null && expectedRevision != state.revision) {
            return Transition.Rejected(RejectReason.STALE_REVISION, "Ignored a duplicate or stale tap")
        }
        return Transition.Applied(
            state.copy(
                status = previous.status,
                game = previous.game,
                completedGames = previous.completedGames,
                lastRally = previous.lastRally,
                log = state.log.dropLast(1),
                undoStack = state.undoStack.dropLast(1),
                revision = state.revision + 1,
            ),
        )
    }

    /** Renames a player. Identity, team and starting position are unaffected; not an undoable rally. */
    fun renamePlayer(state: MatchState, playerId: String, newName: String): Transition {
        if (state.players.none { it.id == playerId }) {
            return Transition.Rejected(RejectReason.INVALID_PLAYER, "Unknown player")
        }
        val name = cleanName(newName) ?: return Transition.Rejected(RejectReason.INVALID_NAME, "Names must be 1-$MAX_NAME characters")
        return Transition.Applied(
            state.copy(
                players = state.players.map { if (it.id == playerId) it.copy(name = name) else it },
                revision = state.revision + 1,
            ),
        )
    }

    /** A fresh match with the same players, starting configuration and settings. */
    fun restart(state: MatchState): Transition {
        val setup = state.setup.copy(
            teamA = state.team(TeamId.A).map { it.name },
            teamB = state.team(TeamId.B).map { it.name },
        )
        return newMatch(setup)
    }

    const val MAX_NAME = 24

    fun cleanName(raw: String): String? {
        val n = raw.trim().replace(Regex("\\s+"), " ")
        return if (n.isEmpty() || n.length > MAX_NAME) null else n
    }

    // ------------------------------------------------------------------ invariants

    /** Returns a list of invariant violations; empty means the state is internally consistent. */
    fun validate(state: MatchState): List<String> {
        val e = mutableListOf<String>()
        val s = state.settings
        val g = state.game
        val size = if (s.mode == GameMode.SINGLES) 1 else 2
        if (state.schemaVersion != MatchState.SCHEMA_VERSION) e += "unsupported schema"
        if (state.team(TeamId.A).size != size || state.team(TeamId.B).size != size) e += "wrong team sizes"
        if (state.players.map { it.id }.toSet().size != state.players.size) e += "duplicate player ids"
        val server = state.players.firstOrNull { it.id == g.serverId }
        if (server == null || server.team != g.servingTeam) e += "server is not on the serving team"
        if (state.players.none { it.id == g.startingServerA && it.team == TeamId.A }) e += "bad starting server A"
        if (state.players.none { it.id == g.startingServerB && it.team == TeamId.B }) e += "bad starting server B"
        if (g.scoreA < 0 || g.scoreB < 0) e += "negative score"
        if (usesTwoServers(s)) {
            if (g.serverNumber !in 1..2) e += "server number out of range"
        } else if (g.serverNumber != 1) e += "server number must be 1"
        if (state.undoStack.size != state.log.size) e += "undo stack and log out of sync"
        if (state.status == MatchStatus.IN_PROGRESS && g.winner != null) e += "in-progress game has a winner"
        if (state.status != MatchStatus.IN_PROGRESS && g.winner == null) e += "finished game has no winner"
        if (state.status == MatchStatus.COMPLETED && TeamId.entries.none { state.gamesWon(it) >= s.gamesNeededToWin }) e += "completed without winner"
        return e
    }
}
