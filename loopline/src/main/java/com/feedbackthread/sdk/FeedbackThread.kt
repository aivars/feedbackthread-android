package com.feedbackthread.sdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

public typealias FeedbackThreadFeedbackKind = com.loopline.sdk.LooplineFeedbackKind
public typealias FeedbackThreadFeedbackSubmission = com.loopline.sdk.LooplineFeedbackSubmission
public typealias FeedbackThreadFeedback = com.loopline.sdk.LooplineFeedback
public typealias FeedbackThreadRequestTarget = com.loopline.sdk.LooplineRequestTarget
public typealias FeedbackThreadFeatureRequest = com.loopline.sdk.LooplineFeatureRequest
public typealias FeedbackThreadVoteResult = com.loopline.sdk.LooplineVoteResult
public typealias FeedbackThreadConfiguration = com.loopline.sdk.LooplineConfiguration
public typealias FeedbackThreadException = com.loopline.sdk.LooplineException
public typealias FeedbackThreadClient = com.loopline.sdk.LooplineClient

@Composable
public fun FeedbackThreadFeedbackScreen(
    client: FeedbackThreadClient,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    appVersion: String? = null,
    externalUserId: String? = null,
    onSubmitted: (FeedbackThreadFeedback) -> Unit = {},
) {
    com.loopline.sdk.LooplineFeedbackScreen(
        client = client,
        onDismiss = onDismiss,
        modifier = modifier,
        appVersion = appVersion,
        externalUserId = externalUserId,
        onSubmitted = onSubmitted,
    )
}

@Composable
public fun FeedbackThreadFeatureRequestScreen(
    client: FeedbackThreadClient,
    onDismiss: () -> Unit,
    onAddRequest: () -> Unit,
    modifier: Modifier = Modifier,
    externalUserId: String? = null,
) {
    com.loopline.sdk.LooplineFeatureRequestScreen(
        client = client,
        onDismiss = onDismiss,
        onAddRequest = onAddRequest,
        modifier = modifier,
        externalUserId = externalUserId,
    )
}
