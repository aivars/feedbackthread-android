package com.loopline.sdk

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
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

private enum class RequestFilter(val title: String) {
    ALL("All"),
    IN_REVIEW("In review"),
    PLANNED("Planned"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed");

    fun includes(status: String): Boolean = when (this) {
        ALL -> status.publicRequestFilter() != null
        else -> status.publicRequestFilter() == this
    }
}

private fun String.publicRequestFilter(): RequestFilter? = when (this) {
    "Under review" -> RequestFilter.IN_REVIEW
    "Planned" -> RequestFilter.PLANNED
    "In progress", "Ready to release" -> RequestFilter.IN_PROGRESS
    "Released" -> RequestFilter.COMPLETED
    else -> null
}

private fun String.publicRequestLabel(): String = publicRequestFilter()?.title ?: this

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
    var selectedFilter by rememberSaveable { mutableStateOf(RequestFilter.ALL) }
    val scope = rememberCoroutineScope()
    val visibleRequests = requests.filter { RequestFilter.ALL.includes(it.status) }
    val filteredRequests = visibleRequests.filter { selectedFilter.includes(it.status) }

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
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRequest) {
                Icon(Icons.Default.Add, contentDescription = "Add request")
            }
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

            visibleRequests.isEmpty() -> {
                RequestMessage(
                    title = "No approved requests yet",
                    message = "Share an idea and it will appear after review.",
                    actionTitle = "Add request",
                    onAction = onAddRequest,
                    modifier = Modifier.padding(contentPadding),
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(RequestFilter.entries, key = { it.name }) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text("${filter.title} (${visibleRequests.count { filter.includes(it.status) }})")
                                },
                            )
                        }
                    }
                    if (phase is RequestLoadPhase.Failed) {
                        Text(
                            text = (phase as RequestLoadPhase.Failed).message,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (filteredRequests.isEmpty()) {
                        RequestMessage(
                            title = "No ${selectedFilter.title.lowercase()} requests",
                            message = "Choose another status to see more requests.",
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredRequests, key = { it.id }) { request ->
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
    }
}

@Composable
private fun FeatureRequestRow(
    request: LooplineFeatureRequest,
    isVoting: Boolean,
    onVote: () -> Unit,
) {
    val statusColor = when (request.status.publicRequestFilter()) {
        RequestFilter.IN_REVIEW -> MaterialTheme.colorScheme.tertiary
        RequestFilter.PLANNED -> MaterialTheme.colorScheme.secondary
        RequestFilter.IN_PROGRESS -> MaterialTheme.colorScheme.primary
        RequestFilter.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
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
                text = request.status.publicRequestLabel(),
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
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
        LooplineFeatureRequest(
            id = "FDBK-3",
            title = "Health integration",
            description = "Include completed sessions in Health Connect.",
            votes = 9,
            target = LooplineRequestTarget.ANDROID,
            status = "Released",
            voted = false,
            updatedAt = "2026-07-14T12:00:00.000Z",
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
