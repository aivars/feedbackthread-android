package com.loopline.sdk

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch

private sealed interface RequestLoadPhase {
    data object Loading : RequestLoadPhase
    data object Loaded : RequestLoadPhase
    data class Failed(val message: String) : RequestLoadPhase
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun LooplineFeatureRequestScreen(
    client: LooplineClient,
    onDismiss: () -> Unit,
    onAddRequest: () -> Unit,
    modifier: Modifier = Modifier,
    externalUserId: String? = null,
) {
    val context = LocalContext.current
    val voterId = remember(externalUserId) {
        externalUserId?.trim()?.takeIf { it.isNotEmpty() } ?: anonymousVoterId(context)
    }
    var requests by remember { mutableStateOf<List<LooplineFeatureRequest>>(emptyList()) }
    var phase by remember { mutableStateOf<RequestLoadPhase>(RequestLoadPhase.Loading) }
    var votingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    suspend fun loadRequests() {
        if (requests.isEmpty()) phase = RequestLoadPhase.Loading
        try {
            requests = client.requests(voterId)
            phase = RequestLoadPhase.Loaded
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            phase = RequestLoadPhase.Failed(error.message ?: "Feature requests could not be loaded.")
        }
    }

    LaunchedEffect(client, voterId) {
        loadRequests()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Feature requests") },
                navigationIcon = {
                    TextButton(onClick = onDismiss) { Text("Done") }
                },
                actions = {
                    TextButton(onClick = onAddRequest) { Text("Add request") }
                },
            )
        },
    ) { contentPadding ->
        when {
            phase is RequestLoadPhase.Loading && requests.isEmpty() -> {
                RequestMessage(
                    title = "Loading requests…",
                    showProgress = true,
                    modifier = Modifier.padding(contentPadding),
                )
            }

            phase is RequestLoadPhase.Failed && requests.isEmpty() -> {
                RequestMessage(
                    title = "Couldn’t load requests",
                    message = (phase as RequestLoadPhase.Failed).message,
                    actionTitle = "Try again",
                    onAction = { scope.launch { loadRequests() } },
                    modifier = Modifier.padding(contentPadding),
                )
            }

            requests.isEmpty() -> {
                RequestMessage(
                    title = "No feature requests yet",
                    message = "Be the first to share an idea.",
                    actionTitle = "Add request",
                    onAction = onAddRequest,
                    modifier = Modifier.padding(contentPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    if (phase is RequestLoadPhase.Failed) {
                        item {
                            Text(
                                text = (phase as RequestLoadPhase.Failed).message,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(requests, key = { it.id }) { request ->
                        FeatureRequestRow(
                            request = request,
                            isVoting = request.id in votingIds,
                            onVote = {
                                if (request.id !in votingIds) {
                                    votingIds = votingIds + request.id
                                    scope.launch {
                                        try {
                                            val result = client.setVote(
                                                requestId = request.id,
                                                voted = !request.voted,
                                                externalUserId = voterId,
                                            )
                                            requests = requests.map { current ->
                                                if (current.id == result.feedbackId) {
                                                    current.copy(votes = result.votes, voted = result.voted)
                                                } else {
                                                    current
                                                }
                                            }
                                        } catch (error: CancellationException) {
                                            throw error
                                        } catch (error: Exception) {
                                            phase = RequestLoadPhase.Failed(
                                                error.message ?: "Your vote could not be saved.",
                                            )
                                        } finally {
                                            votingIds = votingIds - request.id
                                        }
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRequestRow(
    request: LooplineFeatureRequest,
    isVoting: Boolean,
    onVote: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TextButton(onClick = onVote, enabled = !isVoting) {
            if (isVoting) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp))
            } else {
                Text(if (request.voted) "▲ ${request.votes}" else "△ ${request.votes}")
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = request.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = request.status,
                style = MaterialTheme.typography.labelMedium,
                color = if (request.status in setOf("Released", "Closed")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun RequestMessage(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
    showProgress: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator()
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = if (showProgress) 16.dp else 0.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        message?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionTitle != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 20.dp)) {
                Text(actionTitle)
            }
        }
    }
}

private fun anonymousVoterId(context: Context): String {
    val preferences = context.getSharedPreferences("loopline_sdk", Context.MODE_PRIVATE)
    return preferences.getString("anonymous_voter_id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("anonymous_voter_id", it).apply()
    }
}

@Preview(showBackground = true)
@Composable
private fun LooplineFeatureRequestScreenPreview() {
    val previewRequests = listOf(
        LooplineFeatureRequest(
            id = "FDBK-1",
            title = "Breathing reminders",
            description = "Remind me when it is time to practice.",
            votes = 34,
            target = LooplineRequestTarget.ANDROID,
            status = "In progress",
            voted = true,
            updatedAt = "2026-07-16T12:00:00.000Z",
        ),
        LooplineFeatureRequest(
            id = "FDBK-2",
            title = "More training plans",
            description = "Add a longer progression for experienced users.",
            votes = 12,
            target = LooplineRequestTarget.ANDROID,
            status = "Planned",
            voted = false,
            updatedAt = "2026-07-15T12:00:00.000Z",
        ),
    )
    MaterialTheme {
        LooplineFeatureRequestScreen(
            client = LooplineClient(
                submissionHandler = { _, _ -> error("Not used in this preview") },
                requestListHandler = { previewRequests },
                voteHandler = { id, voted, _ ->
                    LooplineVoteResult(id, if (voted) 13 else 12, voted)
                },
            ),
            onDismiss = {},
            onAddRequest = {},
        )
    }
}
