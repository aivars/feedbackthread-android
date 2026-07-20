package com.feedbackthread.sdk

/**
 * The canonical stages of the public feature-request board.
 *
 * Statuses the SDK doesn't recognize are preserved as [Unknown] rather than
 * dropped, so a status added on the server later doesn't silently hide cards
 * from a board built against an older SDK.
 */
internal sealed class FeedbackThreadRequestStage {
    internal data object InReview : FeedbackThreadRequestStage()
    internal data object Planned : FeedbackThreadRequestStage()
    internal data object InProgress : FeedbackThreadRequestStage()
    internal data object Completed : FeedbackThreadRequestStage()
    internal data class Unknown(val rawValue: String) : FeedbackThreadRequestStage()
}

/** Maps a raw feature-request status string to its public board stage. */
internal fun String.feedbackThreadRequestStage(): FeedbackThreadRequestStage = when (this) {
    "Under review", "In review" -> FeedbackThreadRequestStage.InReview
    "Planned" -> FeedbackThreadRequestStage.Planned
    "In progress", "Ready to release" -> FeedbackThreadRequestStage.InProgress
    "Released" -> FeedbackThreadRequestStage.Completed
    else -> FeedbackThreadRequestStage.Unknown(this)
}

/**
 * A user-facing label for the status: the known stage's display name, or the
 * raw status sensibly capitalized when the stage isn't recognized.
 */
internal fun String.feedbackThreadRequestLabel(): String = when (val stage = feedbackThreadRequestStage()) {
    FeedbackThreadRequestStage.InReview -> "In review"
    FeedbackThreadRequestStage.Planned -> "Planned"
    FeedbackThreadRequestStage.InProgress -> "In progress"
    FeedbackThreadRequestStage.Completed -> "Completed"
    is FeedbackThreadRequestStage.Unknown -> stage.rawValue.sensiblyCapitalized()
}

/** Capitalizes each word of a raw status string, e.g. "on_hold" -> "On Hold". */
internal fun String.sensiblyCapitalized(): String =
    trim()
        .split('_', '-', ' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word -> word.substring(0, 1).uppercase() + word.substring(1).lowercase() }
