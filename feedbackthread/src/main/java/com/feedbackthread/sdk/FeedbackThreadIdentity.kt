package com.feedbackthread.sdk

/**
 * The identity-precedence rule shared by every SDK surface that needs a
 * stable per-person ID: a developer-supplied external user ID wins when
 * present and non-blank; otherwise fall back to the on-device anonymous
 * voter ID (see [anonymousVoterId]).
 *
 * Extracted as a pure function - independent of [android.content.Context] -
 * so the precedence itself is unit-testable without Robolectric, and so
 * there is exactly one place this precedence is implemented. The feature
 * request board, the standalone feedback screen, and My Requests all
 * resolve through this same function.
 */
internal fun resolveVoterId(externalUserId: String?, anonymousVoterId: () -> String): String =
    externalUserId?.trim()?.takeIf { it.isNotEmpty() } ?: anonymousVoterId()

/**
 * Public entry point for host apps that need the same stable identity the
 * drop-in screens use - e.g. calling [FeedbackThreadClient.myUpdates] at app
 * launch to badge a menu item before the user ever opens My Requests. Pass
 * your own user ID when you have one; with null this returns (creating on
 * first use) the SDK's persisted on-device anonymous voter ID.
 */
public fun feedbackThreadVoterId(context: android.content.Context, externalUserId: String? = null): String =
    resolveVoterId(externalUserId) { anonymousVoterId(context) }
