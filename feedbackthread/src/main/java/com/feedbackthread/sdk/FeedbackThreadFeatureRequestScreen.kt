package com.feedbackthread.sdk

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.coroutineScope
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
        // Unknown statuses are included under "All" rather than dropped, so a
        // status the SDK doesn't recognize yet doesn't silently hide cards.
        ALL -> true
        IN_REVIEW -> status.feedbackThreadRequestStage() == FeedbackThreadRequestStage.InReview
        PLANNED -> status.feedbackThreadRequestStage() == FeedbackThreadRequestStage.Planned
        IN_PROGRESS -> status.feedbackThreadRequestStage() == FeedbackThreadRequestStage.InProgress
        COMPLETED -> status.feedbackThreadRequestStage() == FeedbackThreadRequestStage.Completed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun FeedbackThreadFeatureRequestScreen(
    client: FeedbackThreadClient,
    onDismiss: () -> Unit,
    onAddRequest: () -> Unit,
    modifier: Modifier = Modifier,
    externalUserId: String? = null,
    customerTierProvider: (() -> FeedbackThreadCustomerTier?)? = null,
) {
    val context = LocalContext.current
    val voterId = remember(externalUserId) {
        resolveVoterId(externalUserId) { anonymousVoterId(context) }
    }
    var requests by remember { mutableStateOf<List<FeedbackThreadFeatureRequest>>(emptyList()) }
    var phase by remember { mutableStateOf<RequestLoadPhase>(RequestLoadPhase.Loading) }
    var votingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFilter by rememberSaveable { mutableStateOf(RequestFilter.ALL) }
    var selectedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var showMyRequests by rememberSaveable { mutableStateOf(false) }
    var unreadCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val visibleRequests = requests.filter { RequestFilter.ALL.includes(it.status) }
    val filteredRequests = visibleRequests.filter { selectedFilter.includes(it.status) }
    val selectedRequest = visibleRequests.firstOrNull { it.id == selectedRequestId }

    // Best-effort: badging the toolbar icon is a nicety, not something that
    // should ever surface an error or block the board from loading.
    suspend fun refreshUnreadCount() {
        unreadCount = runCatching { client.myUpdates(voterId).unreadCount }.getOrDefault(unreadCount)
    }

    suspend fun loadRequests() {
        if (requests.isEmpty()) phase = RequestLoadPhase.Loading
        coroutineScope {
            // Fired alongside the request fetch rather than after it, so
            // opening the board doesn't delay the badge any longer than it
            // has to.
            launch { refreshUnreadCount() }
            try {
                requests = client.requests(voterId)
                phase = RequestLoadPhase.Loaded
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                phase = RequestLoadPhase.Failed(error.message ?: "Feature requests could not be loaded.")
            }
        }
    }

    LaunchedEffect(client, voterId) {
        loadRequests()
    }

    LaunchedEffect(selectedRequestId, visibleRequests) {
        if (selectedRequestId != null && selectedRequest == null && requests.isNotEmpty()) {
            selectedRequestId = null
        }
    }

    BackHandler(enabled = selectedRequest != null) {
        selectedRequestId = null
    }

    if (showMyRequests) {
        // The board currently delegates "add" to an external onAddRequest
        // callback rather than internal navigation, so My Requests is shown
        // via an internal state switch instead: this full-screen composable
        // replaces the board until dismissed, with its own "Done" acting as
        // the back affordance.
        FeedbackThreadMyRequestsScreen(
            client = client,
            onDismiss = {
                showMyRequests = false
                scope.launch { refreshUnreadCount() }
            },
            modifier = modifier,
            externalUserId = externalUserId,
            onUnreadCountChange = { unreadCount = it },
        )
        BackHandler {
            showMyRequests = false
            scope.launch { refreshUnreadCount() }
        }
        return
    }

    val toggleVote: (FeedbackThreadFeatureRequest) -> Unit = { request ->
        if (request.id !in votingIds) {
            votingIds = votingIds + request.id
            scope.launch {
                try {
                    val result = client.setVote(
                        requestId = request.id,
                        voted = !request.voted,
                        externalUserId = voterId,
                        customerTier = customerTierProvider?.invoke(),
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
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (selectedRequest == null) "Feature requests" else "Request") },
                navigationIcon = {
                    if (selectedRequest == null) {
                        TextButton(onClick = onDismiss) { Text("Done") }
                    } else {
                        TextButton(onClick = { selectedRequestId = null }) { Text("Back") }
                    }
                },
                actions = {
                    if (selectedRequest == null) {
                        IconButton(onClick = { showMyRequests = true }) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Person, contentDescription = "My requests")
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            // Pinned in the thumb zone rather than a top-app-bar action, so
            // the primary action stays reachable with one hand.
            if (selectedRequest == null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = onAddRequest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text("Suggest a feature")
                    }
                }
            }
        },
    ) { contentPadding ->
        if (selectedRequest != null) {
            FeatureRequestDetail(
                request = selectedRequest,
                isVoting = selectedRequest.id in votingIds,
                errorMessage = (phase as? RequestLoadPhase.Failed)?.message,
                onVote = { toggleVote(selectedRequest) },
                modifier = Modifier.padding(contentPadding),
            )
        } else when {
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
                                    onVote = { toggleVote(request) },
                                    onOpen = { selectedRequestId = request.id },
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
    request: FeedbackThreadFeatureRequest,
    isVoting: Boolean,
    onVote: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FeatureRequestVoteButton(
            votes = request.votes,
            isVoted = request.voted,
            isVoting = isVoting,
            onVote = onVote,
        )
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(vertical = 4.dp),
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (request.kind == FeedbackThreadFeedbackKind.BUG) {
                    BugKindBadge()
                }
                FeatureRequestStatusBadge(request.status)
                request.shippedInVersion?.let { version ->
                    ShippedInVersionBadge(version)
                }
            }
        }
    }
}

@Composable
private fun BugKindBadge() {
    Text(
        text = "Bug",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun FeatureRequestDetail(
    request: FeedbackThreadFeatureRequest,
    isVoting: Boolean,
    errorMessage: String?,
    onVote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                text = request.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeatureRequestVoteButton(
                    votes = request.votes,
                    isVoted = request.voted,
                    isVoting = isVoting,
                    onVote = onVote,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FeatureRequestStatusBadge(request.status)
                        request.shippedInVersion?.let { version ->
                            ShippedInVersionBadge(version)
                        }
                    }
                    Text(
                        text = "Android",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { HorizontalDivider() }
        item {
            Text(
                text = request.description,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun FeatureRequestVoteButton(
    votes: Int,
    isVoted: Boolean,
    isVoting: Boolean,
    onVote: () -> Unit,
) {
    TextButton(
        onClick = onVote,
        enabled = !isVoting,
        modifier = Modifier
            .widthIn(min = 64.dp)
            .heightIn(min = 56.dp)
            .semantics {
                contentDescription = if (isVoted) {
                    "Remove vote, $votes votes"
                } else {
                    "Vote, $votes votes"
                }
            },
    ) {
        if (isVoting) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp))
        } else {
            Text(if (isVoted) "▲ $votes" else "△ $votes")
        }
    }
}

@Composable
private fun FeatureRequestStatusBadge(status: String) {
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

// internal (not private): shared with FeedbackThreadMyRequestsScreen and
// FeedbackThreadFeedbackScreen (via resolveVoterId), which need the exact
// same identity-precedence rule (explicit externalUserId, or this persisted
// anonymous id) so a submission/vote and a later "my requests"/"my updates"
// lookup resolve to the same voter.
internal fun anonymousVoterId(context: Context): String {
    val preferences = context.getSharedPreferences("feedbackthread_sdk", Context.MODE_PRIVATE)
    preferences.getString("anonymous_voter_id", null)?.let { return it }
    return UUID.randomUUID().toString().also {
        preferences.edit().putString("anonymous_voter_id", it).apply()
    }
}

@Preview(showBackground = true)
@Composable
private fun FeedbackThreadFeatureRequestScreenPreview() {
    val previewRequests = listOf(
        FeedbackThreadFeatureRequest(
            id = "FDBK-1",
            title = "Breathing reminders",
            description = "Remind me when it is time to practice.",
            votes = 34,
            target = FeedbackThreadRequestTarget.ANDROID,
            status = "In progress",
            voted = true,
            updatedAt = "2026-07-16T12:00:00.000Z",
        ),
        FeedbackThreadFeatureRequest(
            id = "FDBK-2",
            title = "More training plans",
            description = "Add a longer progression for experienced users.",
            votes = 12,
            target = FeedbackThreadRequestTarget.ANDROID,
            status = "Planned",
            voted = false,
            updatedAt = "2026-07-15T12:00:00.000Z",
        ),
        FeedbackThreadFeatureRequest(
            id = "FDBK-3",
            title = "Health integration",
            description = "Include completed sessions in Health Connect.",
            votes = 9,
            target = FeedbackThreadRequestTarget.ANDROID,
            status = "Released",
            voted = false,
            updatedAt = "2026-07-14T12:00:00.000Z",
            shippedInVersion = "2.4.0",
        ),
    )
    MaterialTheme {
        FeedbackThreadFeatureRequestScreen(
            client = FeedbackThreadClient(
                submissionHandler = { _, _ -> error("Not used in this preview") },
                requestListHandler = { previewRequests },
                voteHandler = { id, voted, _, _ ->
                    FeedbackThreadVoteResult(id, if (voted) 13 else 12, voted)
                },
            ),
            onDismiss = {},
            onAddRequest = {},
        )
    }
}
