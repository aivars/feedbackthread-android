package com.feedbackthread.sdk

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    data class Failed(val message: FeedbackThreadText) : SubmissionPhase
}

/**
 * A standalone "send feedback" screen: usable on its own (e.g. from a
 * Settings screen) without the request board around it. Submissions are
 * tied to the same on-device anonymous ID the board and My Requests use,
 * unless an external ID is supplied - so a submission made from here still
 * shows up in "My requests" and can receive shipped updates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun FeedbackThreadFeedbackScreen(
    client: FeedbackThreadClient,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    appVersion: String? = null,
    externalUserId: String? = null,
    customerTierProvider: (() -> FeedbackThreadCustomerTier?)? = null,
    onSubmitted: (FeedbackThreadFeedback) -> Unit = {},
) {
    val context = LocalContext.current
    val voterId = remember(externalUserId) {
        resolveVoterId(externalUserId) { anonymousVoterId(context) }
    }
    var kind by remember { mutableStateOf(FeedbackThreadFeedbackKind.REQUEST) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<SubmissionPhase>(SubmissionPhase.Editing) }
    val resubmissionKey = remember { FeedbackThreadResubmissionKey() }
    val scope = rememberCoroutineScope()
    val canSubmit = title.isNotBlank() && message.isNotBlank() && phase !is SubmissionPhase.Submitting

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feedbackthread_form_title)) },
                navigationIcon = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.feedbackthread_cancel))
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
                    if (kind == FeedbackThreadFeedbackKind.REQUEST) {
                        stringResource(R.string.feedbackthread_form_sent_request_title)
                    } else {
                        stringResource(R.string.feedbackthread_form_sent_feedback_title)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (kind == FeedbackThreadFeedbackKind.REQUEST) {
                        stringResource(R.string.feedbackthread_form_sent_request_message)
                    } else {
                        stringResource(R.string.feedbackthread_form_sent_feedback_message)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.feedbackthread_done))
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
                Text(stringResource(R.string.feedbackthread_form_prompt), style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FeedbackThreadFeedbackKind.submittableEntries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = {
                                kind = option
                                resubmissionKey.contentChanged()
                            },
                            label = { Text(stringResource(option.localizedTitle)) },
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        resubmissionKey.contentChanged()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.feedbackthread_form_title_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = {
                        message = it
                        resubmissionKey.contentChanged()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.feedbackthread_form_details_label)) },
                    minLines = 5,
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                    ),
                )

                if (phase is SubmissionPhase.Failed) {
                    Text(
                        text = (phase as SubmissionPhase.Failed).message.resolve(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = {
                        phase = SubmissionPhase.Submitting
                        // Reused across retries of the same content so a network failure
                        // followed by tapping Send again can't create a duplicate
                        // submission server-side.
                        val idempotencyKey = resubmissionKey.beginAttempt()
                        scope.launch {
                            try {
                                val feedback = client.submit(
                                    submission = FeedbackThreadFeedbackSubmission(
                                        kind = kind,
                                        title = title.trim(),
                                        text = message.trim(),
                                        appVersion = appVersion ?: feedbackThreadAppVersion(context),
                                        externalUserId = voterId,
                                        customerTier = customerTierProvider?.invoke(),
                                    ),
                                    idempotencyKey = idempotencyKey,
                                )
                                phase = SubmissionPhase.Sent
                                resubmissionKey.submissionSucceeded()
                                onSubmitted(feedback)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                phase = SubmissionPhase.Failed(
                                    error.feedbackThreadDisplayText(R.string.feedbackthread_form_send_failed_fallback),
                                )
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
                        Text(stringResource(R.string.feedbackthread_form_send))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedbackThreadFeedbackScreenPreview() {
    MaterialTheme {
        FeedbackThreadFeedbackScreen(
            client = FeedbackThreadClient { submission, _ ->
                FeedbackThreadFeedback(
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
