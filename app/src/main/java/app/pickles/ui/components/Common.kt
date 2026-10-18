package app.pickles.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pickles.engine.MatchState
import app.pickles.engine.PicklesEngine
import app.pickles.engine.TeamId
import app.pickles.ui.theme.PicklesColors

/** "Ana / Ben" for doubles, "Ana" for singles. */
fun teamLabel(match: MatchState, team: TeamId): String = match.team(team).joinToString(" / ") { it.name }

@Composable
fun BallLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        drawCircle(PicklesColors.Volt, r)
        val hole = r * 0.11f
        val c = center
        val pts = listOf(
            Offset(0f, 0f), Offset(-0.42f, -0.3f), Offset(0.42f, -0.3f), Offset(-0.42f, 0.3f), Offset(0.42f, 0.3f),
            Offset(0f, -0.62f), Offset(0f, 0.62f), Offset(-0.7f, 0f), Offset(0.7f, 0f),
        )
        pts.forEach { drawCircle(PicklesColors.Navy, hole, Offset(c.x + it.x * r, c.y + it.y * r)) }
    }
}

/** Selectable pill used for options on setup and dialogs. */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, accent: Color = PicklesColors.Volt, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent else PicklesColors.PanelHigh)
            .border(1.dp, if (selected) accent else PicklesColors.PanelHigh, RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) PicklesColors.Navy else Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(currentName) }
    val valid = PicklesEngine.cleanName(text) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit player name") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= PicklesEngine.MAX_NAME) text = it },
                singleLine = true,
                isError = !valid,
                supportingText = { Text("Up to ${PicklesEngine.MAX_NAME} characters") },
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
