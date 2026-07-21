package com.feedbackthread.sdk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.concurrent.CancellationException
import kotlinx.coroutines.launch

private sealed interface MyRequestsLoadPhase {
    data object Loading : MyRequestsLoadPhase
    data object Loaded : MyRequestsLoadPhase
    data class Failed(val message: String) : MyRequestsLoadPhase
}

/**
 * A drop-in "My requests" surface: closes the loop for the end user who
 * submitted feedback through the SDK. Unlike [FeedbackThreadBoard]
 * (the public board), this always shows the caller's own cards, including
 * ones still waiting for review that never appear anywhere public.
 *
 * @param externalUserId The same stable identity already used for voting and
 * submission - a developer-supplied user ID, or (when null/blank) the
 * SDK's own persisted anonymous ID (see [anonymousVoterId]).
 * @param onUnreadCountChange Called after every load/refresh with the number
 * of shipped-but-unacknowledged cards, so a host app can badge its own menu
 * item without re-implementing the fetch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun FeedbackThreadMyRequestsScreen(
    client: FeedbackThreadClient,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    externalUserId: String? = null,
    onUnreadCountChange: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val voterId = remember(externalUserId) {
        resolveVoterId(externalUserId) { anonymousVoterId(context) }
    }
    var myRequests by remember { mutableStateOf<List<FeedbackThreadMyRequest>>(emptyList()) }
    var phase by remember { mutableStateOf<MyRequestsLoadPhase>(MyRequestsLoadPhase.Loading) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        if (myRequests.isEmpty()) phase = MyRequestsLoadPhase.Loading
        try {
            val requestsResult = client.myRequests(voterId)
            val updatesResult = client.myUpdates(voterId)
            myRequests = requestsResult
            phase = MyRequestsLoadPhase.Loaded
            onUnreadCountChange(updatesResult.unreadCount)
            // Viewing this list is the acknowledgement: once the caller has
            // seen the Shipped section (rendered from `myRequests`, not from
            // `updatesResult`), mark those shipped cards read so the badge
            // clears. Best-effort - a failure here doesn't affect what's on
            // screen.
            if (updatesResult.updates.isNotEmpty()) {
                val ids = updatesResult.updates.map { it.id }
                scope.launch {
                    val remaining = runCatching { client.acknowledgeUpdates(ids, voterId) }
                        .getOrDefault(updatesResult.unreadCount)
                    onUnreadCountChange(remaining)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            phase = MyRequestsLoadPhase.Failed(error.message ?: "Your requests could not be loaded.")
        }
    }

    LaunchedEffect(client, voterId) {
        load()
    }

    val pendingReview = myRequests.filter {
        it.status.feedbackThreadRequestStage().myRequestsSection() == FeedbackThreadMyRequestsSection.WAITING_FOR_REVIEW
    }
    val inProgress = myRequests.filter {
        it.status.feedbackThreadRequestStage().myRequestsSection() == FeedbackThreadMyRequestsSection.IN_PROGRESS
    }
    val shipped = myRequests.filter {
        it.status.feedbackThreadRequestStage().myRequestsSection() == FeedbackThreadMyRequestsSection.SHIPPED
    }
    val closed = myRequests.filter {
        it.status.feedbackThreadRequestStage().myRequestsSection() == FeedbackThreadMyRequestsSection.CLOSED
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("My requests") },
                navigationIcon = { TextButton(onClick = onDismiss) { Text("Done") } },
            )
        },
    ) { contentPadding ->
        when {
            phase is MyRequestsLoadPhase.Loading && myRequests.isEmpty() -> {
                MyRequestsMessage(
                    title = "Loading your requests…",
                    showProgress = true,
                    modifier = Modifier.padding(contentPadding),
                )
            }

            phase is MyRequestsLoadPhase.Failed && myRequests.isEmpty() -> {
                MyRequestsMessage(
                    title = "Couldn’t load your requests",
                    message = (phase as MyRequestsLoadPhase.Failed).message,
                    actionTitle = "Try again",
                    onAction = { scope.launch { load() } },
                    modifier = Modifier.padding(contentPadding),
                )
            }

            myRequests.isEmpty() -> {
                MyRequestsMessage(
                    title = "No requests yet",
                    message = "Anything you submit shows up here, including while it's waiting for review.",
                    modifier = Modifier.padding(contentPadding),
                )
            }

            else -> {
                LazyColumn(modifier = Modifier.padding(contentPadding)) {
                    myRequestsSection("Waiting for review", pendingReview)
                    myRequestsSection("In progress", inProgress)
                    myRequestsSection("Shipped", shipped)
                    myRequestsSection("Closed", closed)
                }
            }
        }
    }
}

private fun LazyListScope.myRequestsSection(
    title: String,
    entries: List<FeedbackThreadMyRequest>,
) {
    if (entries.isEmpty()) return
    item {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(entries, key = { it.id }) { request ->
        MyRequestRow(request)
        HorizontalDivider()
    }
}

@Composable
private fun MyRequestRow(request: FeedbackThreadMyRequest) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = request.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyRequestStatusBadge(request.status)
            request.shippedInVersion?.let { version -> ShippedInVersionBadge(version) }
        }
    }
}

@Composable
private fun MyRequestStatusBadge(status: String) {
    val statusColor = when (status.feedbackThreadRequestStage()) {
        FeedbackThreadRequestStage.PendingReview -> MaterialTheme.colorScheme.outline
        FeedbackThreadRequestStage.InReview -> MaterialTheme.colorScheme.tertiary
        FeedbackThreadRequestStage.Planned -> MaterialTheme.colorScheme.secondary
        FeedbackThreadRequestStage.InProgress -> MaterialTheme.colorScheme.primary
        FeedbackThreadRequestStage.Completed -> MaterialTheme.colorScheme.primary
        FeedbackThreadRequestStage.Rejected -> MaterialTheme.colorScheme.error
        is FeedbackThreadRequestStage.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = status.feedbackThreadRequestLabel(),
        modifier = Modifier
            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = statusColor,
    )
}

@Composable
private fun ShippedInVersionBadge(version: String) {
    val shippedColor = Color(0xFF2E7D32)
    Text(
        text = "✓ Shipped in $version",
        modifier = Modifier
            .background(shippedColor.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = shippedColor,
    )
}

@Composable
private fun MyRequestsMessage(
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
            TextButton(onClick = onAction, modifier = Modifier.padding(top = 20.dp)) {
                Text(actionTitle)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedbackThreadMyRequestsScreenPreview() {
    val previewRequests = listOf(
        FeedbackThreadMyRequest(
            id = "FDBK-1",
            title = "Breathing reminders",
            status = "Submitted",
            createdAt = "2026-07-17T12:00:00.000Z",
            voteCount = 1,
        ),
        FeedbackThreadMyRequest(
            id = "FDBK-2",
            title = "More training plans",
            status = "Planned",
            createdAt = "2026-07-15T12:00:00.000Z",
            voteCount = 12,
        ),
        FeedbackThreadMyRequest(
            id = "FDBK-3",
            title = "Health integration",
            status = "Released",
            createdAt = "2026-07-14T12:00:00.000Z",
            voteCount = 9,
            shippedInVersion = "2.4.0",
        ),
    )
    MaterialTheme {
        FeedbackThreadMyRequestsScreen(
            client = FeedbackThreadClient(
                submissionHandler = { _, _ -> error("Not used in this preview") },
                requestListHandler = { emptyList() },
                voteHandler = { id, voted, _, _ -> FeedbackThreadVoteResult(id, 1, voted) },
                myRequestsHandler = { previewRequests },
                myUpdatesHandler = {
                    FeedbackThreadMyUpdatesResult(
                        updates = listOf(
                            FeedbackThreadMyUpdate(
                                id = "FDBK-3",
                                title = "Health integration",
                                shippedVersion = "2.4.0",
                                publishedAt = "2026-07-14T12:00:00.000Z",
                            ),
                        ),
                        unreadCount = 1,
                    )
                },
            ),
            onDismiss = {},
        )
    }
}
