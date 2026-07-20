package com.feedbackthread.sdk

import java.util.UUID

/**
 * Tracks the idempotency key used by a resubmittable form (feedback, bug, or
 * review) so a retry after a failed submission reuses the same key instead
 * of minting a new one — which would let the server see it as a distinct
 * submission and create a duplicate.
 *
 * A new key is only generated for the first attempt, after a successful
 * submission, or after the user materially edits the content being
 * submitted.
 */
internal class FeedbackThreadResubmissionKey(
    private val generator: () -> String = { UUID.randomUUID().toString() },
) {
    private var key: String? = null

    /** Returns the key to use for the next submission attempt, generating and caching one if none is pending. */
    fun beginAttempt(): String = key ?: generator().also { key = it }

    /** Call once a submission succeeds so the next attempt starts a fresh key. */
    fun submissionSucceeded() {
        key = null
    }

    /**
     * Call when the user materially edits the content being submitted so a
     * later retry doesn't reuse a key that was minted for different content.
     */
    fun contentChanged() {
        key = null
    }
}
