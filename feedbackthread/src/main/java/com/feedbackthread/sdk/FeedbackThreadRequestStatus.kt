package com.feedbackthread.sdk

/**
 * The canonical stages of the public feature-request board.
 *
 * Statuses the SDK doesn't recognize are preserved as [Unknown] rather than
 * dropped, so a status added on the server later doesn't silently hide cards
 * from a board built against an older SDK.
 */
internal sealed class FeedbackThreadRequestStage {
    /**
     * Submitted, not yet moderated - only ever seen by the reporter (see
     * FeedbackThreadClient.myRequests), never on the public board.
     */
    internal data object PendingReview : FeedbackThreadRequestStage()
    internal data object InReview : FeedbackThreadRequestStage()
    internal data object Planned : FeedbackThreadRequestStage()
    internal data object InProgress : FeedbackThreadRequestStage()
    internal data object Completed : FeedbackThreadRequestStage()

    /**
     * Declined during moderation - only ever seen by the reporter, same as
     * [PendingReview].
     */
    internal data object Rejected : FeedbackThreadRequestStage()
    internal data class Unknown(val rawValue: String) : FeedbackThreadRequestStage()
}

/** Maps a raw feature-request status string to its public board stage. */
internal fun String.feedbackThreadRequestStage(): FeedbackThreadRequestStage = when (this) {
    "Submitted" -> FeedbackThreadRequestStage.PendingReview
    "Under review", "In review" -> FeedbackThreadRequestStage.InReview
    "Planned" -> FeedbackThreadRequestStage.Planned
    "In progress", "Ready to release" -> FeedbackThreadRequestStage.InProgress
    "Released" -> FeedbackThreadRequestStage.Completed
    "Rejected" -> FeedbackThreadRequestStage.Rejected
    else -> FeedbackThreadRequestStage.Unknown(this)
}

/**
 * A user-facing label for the status: the known stage's display name, or the
 * raw status sensibly capitalized when the stage isn't recognized.
 */
internal fun String.feedbackThreadRequestLabel(): String = when (val stage = feedbackThreadRequestStage()) {
    FeedbackThreadRequestStage.PendingReview -> "Waiting for review"
    FeedbackThreadRequestStage.InReview -> "In review"
    FeedbackThreadRequestStage.Planned -> "Planned"
    FeedbackThreadRequestStage.InProgress -> "In progress"
    FeedbackThreadRequestStage.Completed -> "Completed"
    FeedbackThreadRequestStage.Rejected -> "Rejected"
    is FeedbackThreadRequestStage.Unknown -> stage.rawValue.sensiblyCapitalized()
}

/**
 * Which section of the "My requests" list a status belongs in. A pure,
 * exhaustive mapping - directly unit-testable, unlike the Compose screens -
 * so every stage (including ones the SDK doesn't recognize yet) is
 * guaranteed to land in exactly one section rather than being silently
 * dropped.
 */
internal enum class FeedbackThreadMyRequestsSection {
    WAITING_FOR_REVIEW,
    IN_PROGRESS,
    SHIPPED,
    CLOSED,
}

/**
 * Buckets this stage into its "My requests" section. Unknown stages fold
 * into [FeedbackThreadMyRequestsSection.IN_PROGRESS] (their raw status label
 * still renders via [feedbackThreadRequestLabel]) so a status the SDK
 * doesn't recognize yet doesn't produce a card that fits no section and
 * effectively vanishes from the list.
 */
internal fun FeedbackThreadRequestStage.myRequestsSection(): FeedbackThreadMyRequestsSection = when (this) {
    FeedbackThreadRequestStage.PendingReview -> FeedbackThreadMyRequestsSection.WAITING_FOR_REVIEW
    FeedbackThreadRequestStage.InReview,
    FeedbackThreadRequestStage.Planned,
    FeedbackThreadRequestStage.InProgress,
    is FeedbackThreadRequestStage.Unknown,
    -> FeedbackThreadMyRequestsSection.IN_PROGRESS
    FeedbackThreadRequestStage.Completed -> FeedbackThreadMyRequestsSection.SHIPPED
    FeedbackThreadRequestStage.Rejected -> FeedbackThreadMyRequestsSection.CLOSED
}

/** Capitalizes each word of a raw status string, e.g. "on_hold" -> "On Hold". */
internal fun String.sensiblyCapitalized(): String =
    trim()
        .split('_', '-', ' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word -> word.substring(0, 1).uppercase() + word.substring(1).lowercase() }
