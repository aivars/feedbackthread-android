package com.loopline.sdk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch

private sealed interface SubmissionPhase {
    data object Editing : SubmissionPhase
    data object Submitting : SubmissionPhase
    data object Sent : SubmissionPhase
    data class Failed(val message: String) : SubmissionPhase
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun LooplineFeedbackScreen(
    client: LooplineClient,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    appVersion: String? = null,
    externalUserId: String? = null,
    onSubmitted: (LooplineFeedback) -> Unit = {},
) {
    var kind by remember { mutableStateOf(LooplineFeedbackKind.REQUEST) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<SubmissionPhase>(SubmissionPhase.Editing) }
    val scope = rememberCoroutineScope()
    val canSubmit = title.isNotBlank() && message.isNotBlank() && phase !is SubmissionPhase.Submitting

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Feedback") },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                },
            )
        },
    ) { contentPadding ->
        if (phase is SubmissionPhase.Sent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (kind == LooplineFeedbackKind.REQUEST) "Request submitted for review" else "Feedback sent",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (kind == LooplineFeedbackKind.REQUEST) {
                        "It will appear in the feature list after it is approved."
                    } else {
                        "Thank you for helping us improve the app."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("What would you like to share?", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LooplineFeedbackKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(option.title) },
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Short title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Details") },
                    minLines = 5,
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                    ),
                )

                if (phase is SubmissionPhase.Failed) {
                    Text(
                        text = (phase as SubmissionPhase.Failed).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = {
                        phase = SubmissionPhase.Submitting
                        scope.launch {
                            try {
                                val feedback = client.submit(
                                    LooplineFeedbackSubmission(
                                        kind = kind,
                                        title = title.trim(),
                                        text = message.trim(),
                                        appVersion = appVersion,
                                        externalUserId = externalUserId,
                                    ),
                                )
                                phase = SubmissionPhase.Sent
                                onSubmitted(feedback)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                phase = SubmissionPhase.Failed(error.message ?: "Feedback could not be sent.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSubmit,
                ) {
                    if (phase is SubmissionPhase.Submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Send feedback")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LooplineFeedbackScreenPreview() {
    MaterialTheme {
        LooplineFeedbackScreen(
            client = LooplineClient { submission, _ ->
                LooplineFeedback(
                    id = "FDBK-preview",
                    kind = submission.kind,
                    source = "android",
                    title = submission.title,
                    excerpt = submission.text,
                    version = submission.appVersion ?: "Preview",
                    status = "Submitted",
                    count = 1,
                    note = "",
                    responseDraft = "",
                    responseState = "Not started",
                    createdAt = "2026-07-16T12:00:00.000Z",
                    updatedAt = "2026-07-16T12:00:00.000Z",
                )
            },
            onDismiss = {},
            appVersion = "1.0.0 (4)",
        )
    }
}
