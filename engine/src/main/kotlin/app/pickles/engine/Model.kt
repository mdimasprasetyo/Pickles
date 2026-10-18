package app.pickles.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Pickles match-state model.
 *
 * Source of truth: 2026 USA Pickleball Official Rulebook
 * (https://usapickleball.org/docs/rules/USAP-Official-Rulebook.pdf).
 * Rule numbers quoted in KDoc refer to that edition. See [RuleRefs].
 *
 * Everything the UI shows (court, scoreboard, highlights, score call) is derived
 * from a single immutable [MatchState] through [PicklesEngine.situation].
 */

@Serializable
enum class TeamId {
    A, B;

    val other: TeamId get() = if (this == A) B else A
}

@Serializable
enum class GameMode { SINGLES, DOUBLES }

/**
 * [SIDE_OUT] is standard scoring (Rule 4.A) and the default.
 * [RALLY] is the provisional rally-scoring option (Rule 14.A) and must be chosen explicitly.
 */
@Serializable
enum class ScoringSystem { SIDE_OUT, RALLY }

/**
 * A side of one team's end of the court, from that team's own perspective facing the net.
 * "Right" is the even court (Rules 5.A.1, 5.B.3).
 */
@Serializable
enum class Side {
    RIGHT, LEFT;

    val opposite: Side get() = if (this == RIGHT) LEFT else RIGHT

    companion object {
        /** Zero or even -> RIGHT, odd -> LEFT (Rules 5.A.1, 5.B.3). */
        fun forScore(score: Int): Side = if (score % 2 == 0) RIGHT else LEFT
    }
}

@Serializable
enum class MatchStatus {
    /** A game is being played; rally results are accepted. */
    IN_PROGRESS,

    /** A game of a multi-game match just ended; waiting for the next game to start. */
    BETWEEN_GAMES,

    /** The match has a winner. Only undo or restart are possible. */
    COMPLETED,
}

@Serializable
data class Player(
    /** Stable identifier, never changes for the life of a match: "A1", "A2", "B1", "B2". */
    val id: String,
    val name: String,
    val team: TeamId,
)

@Serializable
data class MatchSettings(
    val mode: GameMode,
    val scoring: ScoringSystem = ScoringSystem.SIDE_OUT,
    /** Points needed to win a game. Standard is 11 (Section 1; Rule 15.C). */
    val pointsToWin: Int = 11,
    /** Required winning margin. Official play is win by 2 (Rule 15.C). */
    val winBy: Int = 2,
    /** Number of games in the match: 1, 3 or 5 (Rule 15.C). */
    val bestOf: Int = 1,
    /** Change ends at the midpoint of a single game or deciding game (Rule 21.B). */
    val endChangeInDecidingGame: Boolean = true,
) {
    val gamesNeededToWin: Int get() = bestOf / 2 + 1

    /** Midpoint score that triggers an end change (Rule 21.B.1-3: 11->6, 15->8, 21->11; 15.C.2.d: 7->4). */
    val endChangeAt: Int get() = (pointsToWin + 1) / 2

    /**
     * True when the format is one of the tournament formats listed in Rule 15.C.
     * Anything else is presented in the app as a recreational setting.
     */
    val isOfficialTournamentFormat: Boolean
        get() {
            if (winBy != 2) return false
            return when (scoring) {
                ScoringSystem.SIDE_OUT ->
                    (pointsToWin == 11 && bestOf in setOf(1, 3, 5)) ||
                        (pointsToWin in setOf(15, 21) && bestOf == 1)
                ScoringSystem.RALLY ->
                    pointsToWin in setOf(11, 15, 21) && bestOf in setOf(1, 3, 5)
            }
        }
}

/** Everything chosen on the setup screen. */
@Serializable
data class MatchSetup(
    val settings: MatchSettings,
    /** One name for singles, two for doubles. */
    val teamA: List<String>,
    val teamB: List<String>,
    /** Index into [teamA] of team A's starting server (doubles). */
    val startingServerIndexA: Int = 0,
    /** Index into [teamB] of team B's starting server (doubles). */
    val startingServerIndexB: Int = 0,
    /** Team that serves first in game 1 (chosen by any fair method). */
    val firstServingTeam: TeamId = TeamId.A,
)

/** State of the game currently being played (or the one that just finished). */
@Serializable
data class GameState(
    val gameNumber: Int,
    val scoreA: Int,
    val scoreB: Int,
    val servingTeam: TeamId,
    /** Id of the correct server for the next rally. */
    val serverId: String,
    /**
     * Doubles side-out scoring: 1 or 2 (Rules 5.B.2, 5.B.5, 5.B.6, 6.B.2).
     * Singles and rally scoring always use 1 (rally scoring has one server, Rule 14.A.5).
     */
    val serverNumber: Int,
    /** Team that served first in this game (Rule 21.F.2 alternates it each game). */
    val firstServingTeam: TeamId,
    /**
     * Starting server of each team for this game (Rule 2 "Starting Server", 5.B.3).
     * Their correct side is RIGHT when their team score is even. In singles this is the single player.
     */
    val startingServerA: String,
    val startingServerB: String,
    /** Court orientation: false = Team A at the near (bottom) end of the screen. Display only. */
    val endsSwapped: Boolean = false,
    /** Whether the mid-game end change (Rule 21.B) already happened in this game. */
    val endChangeDone: Boolean = false,
    /** False until the first side out of the game (ends the 0-0-2 single-server rotation, Rule 5.B). */
    val firstRotationOver: Boolean = false,
    val winner: TeamId? = null,
) {
    fun score(team: TeamId): Int = if (team == TeamId.A) scoreA else scoreB
    fun startingServer(team: TeamId): String = if (team == TeamId.A) startingServerA else startingServerB
    val receivingTeam: TeamId get() = servingTeam.other
}

@Serializable
data class GameResult(val gameNumber: Int, val scoreA: Int, val scoreB: Int, val winner: TeamId)

/** What a rally did, used for the "recent scoring event" highlight and the log. */
@Serializable
enum class RallyOutcome {
    /** Serving side won the rally and scored (Rule 4.A). */
    POINT,

    /** Rally scoring only: receiving side won, scored and gained the serve (Rules 14.A.2, 14.A.5). */
    POINT_AND_SIDE_OUT,

    /** Doubles: first server's team lost the rally, partner now serves (Rule 5.B.5). */
    SECOND_SERVER,

    /** Serve passes to the opponents (Rules 5.A.3, 5.B, 5.B.6). */
    SIDE_OUT,
}

@Serializable
data class RallyRecord(
    /** 1-based index of the rally within the match. */
    val number: Int,
    val gameNumber: Int,
    val winner: TeamId,
    val outcome: RallyOutcome,
    val scoreAAfter: Int,
    val scoreBAfter: Int,
    val gameWon: Boolean,
    val matchWon: Boolean,
    val endChange: Boolean,
)

@Serializable
sealed interface MatchEvent {
    @Serializable
    @SerialName("rally")
    data class Rally(val record: RallyRecord) : MatchEvent

    @Serializable
    @SerialName("next_game")
    data class NextGame(val gameNumber: Int) : MatchEvent
}

/** Everything that undo restores. */
@Serializable
data class Snapshot(
    val status: MatchStatus,
    val game: GameState,
    val completedGames: List<GameResult>,
    val lastRally: RallyRecord?,
)

/** The single authoritative match state. Immutable. */
@Serializable
data class MatchState(
    val schemaVersion: Int = SCHEMA_VERSION,
    val matchId: String,
    val setup: MatchSetup,
    val settings: MatchSettings,
    val players: List<Player>,
    val status: MatchStatus,
    val game: GameState,
    val completedGames: List<GameResult>,
    /** The most recent rally, for the court's "recent scoring event" highlight. */
    val lastRally: RallyRecord?,
    /** Rally history (and game starts), oldest first. */
    val log: List<MatchEvent>,
    /** Previous states, newest last. One entry per undoable event in [log]. */
    val undoStack: List<Snapshot>,
    /** Monotonic counter, bumped by every committed transition. Used to reject stale/duplicate taps. */
    val revision: Long,
) {
    fun player(id: String): Player = players.first { it.id == id }
    fun team(team: TeamId): List<Player> = players.filter { it.team == team }
    fun gamesWon(team: TeamId): Int = completedGames.count { it.winner == team }
    val rallies: List<RallyRecord> get() = log.filterIsInstance<MatchEvent.Rally>().map { it.record }
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val matchWinner: TeamId?
        get() = if (status == MatchStatus.COMPLETED) TeamId.entries.first { gamesWon(it) >= settings.gamesNeededToWin } else null

    internal fun snapshot() = Snapshot(status, game, completedGames, lastRally)

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** Result of any engine transition. Invalid transitions are rejected, never silently applied. */
sealed interface Transition {
    data class Applied(val state: MatchState) : Transition
    data class Rejected(val reason: RejectReason, val message: String) : Transition
}

enum class RejectReason {
    INVALID_SETUP,
    MATCH_COMPLETED,
    GAME_NOT_IN_PROGRESS,
    NOT_BETWEEN_GAMES,
    STALE_REVISION,
    NOTHING_TO_UNDO,
    INVALID_PLAYER,
    INVALID_NAME,
}

/** Role of a player for the next rally. */
enum class Role {
    /** The correct server: position is required (Rules 5.B.1, 7.A). */
    SERVER,

    /** The correct receiver: position is required (Rules 5.B.1, 5.C.1.c). */
    RECEIVER,

    /** Server's partner: no positional restriction while not serving/receiving (Rule 5.B.4). */
    SERVER_PARTNER,

    /** Receiver's partner: no positional restriction while not serving/receiving (Rule 5.B.4). */
    RECEIVER_PARTNER,
}

/**
 * A player's score-dependent reference side.
 * [required] is true only for the correct server and correct receiver.
 * For partners, [side] is the conventional side implied by Rule 5.B.3 but the player is free
 * to stand anywhere on their end (Rule 5.B.4).
 */
data class PlayerPosition(val player: Player, val side: Side, val role: Role) {
    val required: Boolean get() = role == Role.SERVER || role == Role.RECEIVER
}

/** Everything the UI needs for the next rally, derived purely from [MatchState]. */
data class CourtSituation(
    val servingTeam: TeamId,
    val receivingTeam: TeamId,
    val server: Player,
    /** 1 or 2 in side-out doubles, null otherwise. */
    val serverNumber: Int?,
    /** Serving area the serve must come from (Rules 5.A.1, 5.B.3, 7.A.3). */
    val serveFrom: Side,
    val receiver: Player,
    /** Receiving team's service court, diagonally opposite the server (Rules 5.A.1, 7.E). */
    val receiveIn: Side,
    val positions: List<PlayerPosition>,
    val servingScore: Int,
    val receivingScore: Int,
    /** The score as it must be called (Rules 6.B.1, 6.B.2, 14.A.3). */
    val scoreCall: String,
    /** True at 0-0-2: the starting server serving as second server (Rules 5.B, 5.B.2). */
    val isStartOfGameException: Boolean,
) {
    fun position(playerId: String): PlayerPosition = positions.first { it.player.id == playerId }
}
