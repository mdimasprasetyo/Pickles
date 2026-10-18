package app.pickles.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pickles.ui.theme.PicklesColors
import app.pickles.ui.components.enter
import androidx.compose.foundation.lazy.itemsIndexed

private data class HelpTopic(val title: String, val body: List<String>, val rules: String)

/**
 * Plain-language guide. Every topic cites the 2026 USA Pickleball Official Rulebook section it
 * summarises so the app can be audited when the rulebook changes. Where the app adds its own
 * behaviour (not a rule), the text says so.
 */
private val topics = listOf(
    HelpTopic(
        "How Pickles works",
        listOf(
            "After each rally, tap the team that won it. Pickles works out the score, who serves next, the server number, the serving side and the correct receiver.",
            "Undo reverses the last rally completely. Your match is saved on the phone after every rally, so you can close the app and resume later, even offline.",
        ),
        "App behaviour, not a rule",
    ),
    HelpTopic(
        "Standard (side-out) scoring",
        listOf(
            "Only the serving side can score. Win the rally while serving: you get a point. Win it while receiving: you get no point, but you move toward getting the serve.",
            "Games are normally to 11, win by 2. Tournament games may be to 15 or 21, also win by 2.",
        ),
        "Rules 4.A, 4.B, 15.C; Section 1",
    ),
    HelpTopic(
        "Singles serving positions",
        listOf(
            "When the server's score is 0 or even, serve from the right; when it is odd, serve from the left. The receiver stands in the diagonally opposite service court.",
            "The server keeps serving and switches sides after each point until losing a rally. Then it's a side out and the opponent serves.",
            "The score is called as two numbers: server's score, then receiver's score.",
        ),
        "Rules 5.A.1, 5.A.2, 5.A.3, 6.B.1",
    ),
    HelpTopic(
        "Doubles score call",
        listOf(
            "Call three numbers: serving team's score, receiving team's score, then the server number (1 or 2). Example: 7–5–2 means the serving team has 7, the receivers have 5, and the second server is serving.",
            "Every game starts with the call 0–0–2.",
        ),
        "Rule 6.B.2",
    ),
    HelpTopic(
        "The starting-server exception (0–0–2)",
        listOf(
            "At the start of each game, only one player on the team that serves first gets to serve before the side out. That player, the starting server, counts as the second server, which is why the game begins 0–0–2.",
            "After that first side out, both players on each team serve in every rotation.",
        ),
        "Rules 5.B, 5.B.2, 6.B.2",
    ),
    HelpTopic(
        "First and second servers",
        listOf(
            "After a side out, the player who is correctly on the right side for the team's score serves first (server 1). The partner is server 2.",
            "Server 1 serves, switching sides after each point, until the team loses a rally. Then server 2 serves from their own correct side. The receivers do not get a point.",
        ),
        "Rules 5.B.2, 5.B.5",
    ),
    HelpTopic(
        "Side outs",
        listOf(
            "When the second server's team loses a rally, it's a side out: the serve passes to the other team, whose first serve comes from the right side.",
            "In singles, every lost rally by the server is a side out.",
        ),
        "Rules 5.A.3, 5.B.6",
    ),
    HelpTopic(
        "Score-based positions",
        listOf(
            "Each doubles team's correct sides depend on its own score and its starting server. When the team score is 0 or even, the starting server is on the right and the partner on the left. When the score is odd, they are reversed.",
            "So partners only switch when their own team scores a point while serving. The receiving team never switches because it can't score in side-out play.",
            "Pickles marks each team's starting server at setup and uses this rule to find the correct server and receiver every rally.",
        ),
        "Rule 5.B.3",
    ),
    HelpTopic(
        "Correct server and receiver",
        listOf(
            "The correct server must serve from the correct side, and the serve goes diagonally to the opposite service court. The correct receiver is the receiving player whose correct side matches.",
            "On the court, the server stands in the highlighted serving area behind the baseline; the arrow points to the highlighted receiving court and the receiver.",
            "If the wrong player returns the serve while the correct server and receiver were in position, it's a fault on the receiving team.",
        ),
        "Rules 5.B.1, 5.B.3, 5.C.1.c, 7.E",
    ),
    HelpTopic(
        "Required vs flexible positions",
        listOf(
            "Only the server and the receiver have required positions. Their partners may stand anywhere on their own end of the court, on or off the court. The server's partner does not have to stand behind the baseline.",
            "Pickles shows partners faded and labelled FLEXIBLE at their usual score-based side, as a reference only.",
        ),
        "Rules 5.B.1, 5.B.4",
    ),
    HelpTopic(
        "Rally scoring (provisional option)",
        listOf(
            "Only used if you choose it at setup. Every rally scores a point for the side that wins it. In doubles there is one server per side, and the score is called with two numbers.",
            "When the receiving side wins a rally, they score and take the serve; their player on the right for the new score serves.",
        ),
        "Rules 14.A.2–14.A.5, 15.C.2",
    ),
    HelpTopic(
        "Changing ends",
        listOf(
            "In a match, teams change ends and initial service for each new game. Teams may also change their starting server between games.",
            "In a single game or deciding game, teams change ends when the first side reaches 6 (game to 11), 8 (to 15) or 11 (to 21). The same server continues. Pickles flips the court picture when this happens.",
        ),
        "Rules 21.B.1–21.B.4, 21.F.2, 21.F.3",
    ),
    HelpTopic(
        "Official vs recreational settings",
        listOf(
            "Official tournament formats are win by 2 and: best 2 of 3 or 3 of 5 games to 11, or one game to 15 or 21 (one game to 11 only in some round robins). Win by 1 is allowed only for team play.",
            "Any other combination is labelled recreational in Pickles.",
        ),
        "Rule 15.C",
    ),
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", fontSize = 18.sp, color = PicklesColors.Volt) }
            Spacer(Modifier.weight(1f))
            Text("Rules & scoring guide", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.padding(32.dp))
        }
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Based on the 2026 USA Pickleball Official Rulebook. Rule numbers refer to that edition. This is a plain-language summary; the rulebook is the authority.",
                    color = PicklesColors.Muted,
                    fontSize = 14.sp,
                )
            }
            itemsIndexed(topics) { i, t ->
                Column(
                    Modifier.enter(i.coerceAtMost(6)).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PicklesColors.Panel).padding(16.dp),
                ) {
                    Text(
                        t.title,
                        color = PicklesColors.Volt,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        modifier = Modifier.semantics { heading() },
                    )
                    t.body.forEach {
                        Text(it, color = Color.White, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    Text(t.rules, color = PicklesColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                }
            }
            item {
                Text(
                    "Rulebook: usapickleball.org/rules",
                    color = PicklesColors.Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
