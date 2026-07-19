// Deprecated Loopline-prefixed compatibility layer, kept for source compatibility with
// existing integrators (Apnea, FocusLock) while they migrate to the com.feedbackthread.sdk
// package. Planned removal: 0.3.0.
//
// The real implementations now live in com.feedbackthread.sdk. Everything below is a thin
// typealias (or, for composables, a one-line delegating wrapper, since Kotlin typealiases
// cannot target top-level functions) pointing at the real declarations. There is exactly one
// alias layer left: no double-wrapping through an intermediate com.feedbackthread.sdk wrapper.
package com.loopline.sdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.feedbackthread.sdk.FeedbackThreadClient
import com.feedbackthread.sdk.FeedbackThreadFeatureRequestScreen
import com.feedbackthread.sdk.FeedbackThreadFeedback
import com.feedbackthread.sdk.FeedbackThreadFeedbackScreen

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadFeedbackKind",
    ReplaceWith("FeedbackThreadFeedbackKind", "com.feedbackthread.sdk.FeedbackThreadFeedbackKind"),
)
public typealias LooplineFeedbackKind = com.feedbackthread.sdk.FeedbackThreadFeedbackKind

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadCustomerTier",
    ReplaceWith("FeedbackThreadCustomerTier", "com.feedbackthread.sdk.FeedbackThreadCustomerTier"),
)
public typealias LooplineCustomerTier = com.feedbackthread.sdk.FeedbackThreadCustomerTier

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadFeedbackSubmission",
    ReplaceWith("FeedbackThreadFeedbackSubmission", "com.feedbackthread.sdk.FeedbackThreadFeedbackSubmission"),
)
public typealias LooplineFeedbackSubmission = com.feedbackthread.sdk.FeedbackThreadFeedbackSubmission

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadFeedback",
    ReplaceWith("FeedbackThreadFeedback", "com.feedbackthread.sdk.FeedbackThreadFeedback"),
)
public typealias LooplineFeedback = com.feedbackthread.sdk.FeedbackThreadFeedback

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadRequestTarget",
    ReplaceWith("FeedbackThreadRequestTarget", "com.feedbackthread.sdk.FeedbackThreadRequestTarget"),
)
public typealias LooplineRequestTarget = com.feedbackthread.sdk.FeedbackThreadRequestTarget

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadFeatureRequest",
    ReplaceWith("FeedbackThreadFeatureRequest", "com.feedbackthread.sdk.FeedbackThreadFeatureRequest"),
)
public typealias LooplineFeatureRequest = com.feedbackthread.sdk.FeedbackThreadFeatureRequest

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadVoteResult",
    ReplaceWith("FeedbackThreadVoteResult", "com.feedbackthread.sdk.FeedbackThreadVoteResult"),
)
public typealias LooplineVoteResult = com.feedbackthread.sdk.FeedbackThreadVoteResult

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadConfiguration",
    ReplaceWith("FeedbackThreadConfiguration", "com.feedbackthread.sdk.FeedbackThreadConfiguration"),
)
public typealias LooplineConfiguration = com.feedbackthread.sdk.FeedbackThreadConfiguration

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadException",
    ReplaceWith("FeedbackThreadException", "com.feedbackthread.sdk.FeedbackThreadException"),
)
public typealias LooplineException = com.feedbackthread.sdk.FeedbackThreadException

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadClient",
    ReplaceWith("FeedbackThreadClient", "com.feedbackthread.sdk.FeedbackThreadClient"),
)
public typealias LooplineClient = com.feedbackthread.sdk.FeedbackThreadClient

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadFeedbackScreen",
    ReplaceWith(
        "FeedbackThreadFeedbackScreen(client, onDismiss, modifier, appVersion, externalUserId, onSubmitted)",
        "com.feedbackthread.sdk.FeedbackThreadFeedbackScreen",
    ),
)
@Composable
public fun LooplineFeedbackScreen(
    client: FeedbackThreadClient,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    appVersion: String? = null,
    externalUserId: String? = null,
    onSubmitted: (FeedbackThreadFeedback) -> Unit = {},
) {
    FeedbackThreadFeedbackScreen(
        client = client,
        onDismiss = onDismiss,
        modifier = modifier,
        appVersion = appVersion,
        externalUserId = externalUserId,
        onSubmitted = onSubmitted,
    )
}

@Deprecated(
    "Renamed to com.feedbackthread.sdk.FeedbackThreadFeatureRequestScreen",
    ReplaceWith(
        "FeedbackThreadFeatureRequestScreen(client, onDismiss, onAddRequest, modifier, externalUserId)",
        "com.feedbackthread.sdk.FeedbackThreadFeatureRequestScreen",
    ),
)
@Composable
public fun LooplineFeatureRequestScreen(
    client: FeedbackThreadClient,
    onDismiss: () -> Unit,
    onAddRequest: () -> Unit,
    modifier: Modifier = Modifier,
    externalUserId: String? = null,
) {
    FeedbackThreadFeatureRequestScreen(
        client = client,
        onDismiss = onDismiss,
        onAddRequest = onAddRequest,
        modifier = modifier,
        externalUserId = externalUserId,
    )
}
