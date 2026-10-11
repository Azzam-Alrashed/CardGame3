package com.azzamalrashed.cardgame3.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azzamalrashed.cardgame3.R

/** One button in a [ChoiceDialog] or [MessageDialog]. */
data class Choice(val label: String, val destructive: Boolean = false, val action: () -> Unit)

/**
 * A list of choices plus Cancel: Android's take on iOS's confirmation dialogs ("Leave the table?",
 * "Remove Sami?", "Report Sami?"). Choosing anything closes it.
 */
@Composable
fun ChoiceDialog(title: String, choices: List<Choice>, onDismiss: () -> Unit, message: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, style = Theme.body(20f, FontWeight.ExtraBold), color = Theme.ink) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (message != null) Text(message, style = Theme.body(15f, FontWeight.Medium), color = Theme.ink.copy(alpha = 0.7f))
                for (choice in choices) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            choice.action()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            choice.label,
                            style = Theme.body(17f, FontWeight.Bold),
                            color = if (choice.destructive) Theme.destructive else Theme.ink,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), style = Theme.body(17f, FontWeight.Bold), color = Theme.ink)
            }
        },
    )
}

/** An alert with a title, a message and one or more buttons (iOS's `.alert`). The last choice is the main one. */
@Composable
fun MessageDialog(title: String, message: String, choices: List<Choice>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, style = Theme.body(20f, FontWeight.ExtraBold), color = Theme.ink) },
        text = { Text(message, style = Theme.body(16f, FontWeight.Medium), color = Theme.ink.copy(alpha = 0.8f)) },
        confirmButton = {
            choices.lastOrNull()?.let { choice ->
                TextButton(onClick = {
                    onDismiss()
                    choice.action()
                }) { Text(choice.label, style = Theme.body(17f, FontWeight.Bold), color = Theme.ink) }
            }
        },
        dismissButton = {
            for (choice in choices.dropLast(1)) {
                TextButton(onClick = {
                    onDismiss()
                    choice.action()
                }) {
                    Text(
                        choice.label,
                        style = Theme.body(17f, FontWeight.Bold),
                        color = if (choice.destructive) Theme.destructive else Theme.ink,
                    )
                }
            }
        },
    )
}
