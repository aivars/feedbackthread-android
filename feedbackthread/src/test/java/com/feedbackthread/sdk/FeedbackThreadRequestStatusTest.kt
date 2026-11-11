package com.feedbackthread.sdk

import com.feedbackthread.sdk.FeedbackThreadStringResources.english
import com.feedbackthread.sdk.FeedbackThreadStringResources.italian
import org.junit.Assert.assertEquals
import org.junit.Test

public class FeedbackThreadRequestStatusTest {
    @Test
    public fun mapsKnownStatusesToTheirPublicBoardStage() {
        assertEquals(FeedbackThreadRequestStage.PendingReview, "Submitted".feedbackThreadRequestStage())
        assertEquals(FeedbackThreadRequestStage.InReview, "Under review".feedbackThreadRequestStage())
        assertEquals(FeedbackThreadRequestStage.InReview, "In review".feedbackThreadRequestStage())
        assertEquals(FeedbackThreadRequestStage.Planned, "Planned".feedbackThreadRequestStage())
        assertEquals(FeedbackThreadRequestStage.InProgress, "In progress".feedbackThreadRequestStage())
        assertEquals(FeedbackThreadRequestStage.InProgress, "Ready to release".feedbackThreadRequestStage())
        assertEquals(FeedbackThreadRequestStage.Completed, "Released".feedbackThreadRequestStage())
        assertEquals(FeedbackThreadRequestStage.Rejected, "Rejected".feedbackThreadRequestStage())
    }

    @Test
    public fun labelsEveryKnownStatusWithItsResourceAndUnchangedEnglish() {
        // Wire status -> the label resource it maps to, and the English label
        // the SDK showed before it was localized: English must not move.
        val labels = listOf(
            Triple("Submitted", R.string.feedbackthread_status_waiting_for_review, "Waiting for review"),
            Triple("Under review", R.string.feedbackthread_status_in_review, "In review"),
            Triple("In review", R.string.feedbackthread_status_in_review, "In review"),
            Triple("Planned", R.string.feedbackthread_status_planned, "Planned"),
            Triple("In progress", R.string.feedbackthread_status_in_progress, "In progress"),
            Triple("Ready to release", R.string.feedbackthread_status_in_progress, "In progress"),
            Triple("Released", R.string.feedbackthread_status_completed, "Completed"),
            Triple("Rejected", R.string.feedbackthread_status_rejected, "Rejected"),
        )

        for ((status, resource, englishLabel) in labels) {
            val label = status.feedbackThreadRequestLabel()
            assertEquals(status, FeedbackThreadText.Resource(resource), label)
            assertEquals(status, englishLabel, english.render(label))
        }
    }

    @Test
    public fun labelsPendingReviewHonestlyWithoutImplyingModerationHappened() {
        val label = "Submitted".feedbackThreadRequestLabel()

        assertEquals("Waiting for review", english.render(label))
        assertEquals("In attesa di revisione", italian.render(label))
    }

    @Test
    public fun labelsRejectedPlainly() {
        val label = "Rejected".feedbackThreadRequestLabel()

        assertEquals("Rejected", english.render(label))
        assertEquals("Rifiutata", italian.render(label))
    }

    @Test
    public fun preservesAFabricatedUnrecognizedStatusRatherThanDroppingIt() {
        val status = "archived"

        assertEquals(FeedbackThreadRequestStage.Unknown("archived"), status.feedbackThreadRequestStage())
        assertEquals(FeedbackThreadText.Verbatim("Archived"), status.feedbackThreadRequestLabel())
    }

    @Test
    public fun sensiblyCapitalizesAnUnrecognizedStatusWithSeparators() {
        assertEquals(FeedbackThreadText.Verbatim("On Hold"), "on_hold".feedbackThreadRequestLabel())
        assertEquals(FeedbackThreadText.Verbatim("Needs Triage"), "NEEDS-TRIAGE".feedbackThreadRequestLabel())
    }

    @Test
    public fun bucketsEveryStageIntoExactlyOneMyRequestsSection() {
        assertEquals(
            FeedbackThreadMyRequestsSection.WAITING_FOR_REVIEW,
            FeedbackThreadRequestStage.PendingReview.myRequestsSection(),
        )
        assertEquals(FeedbackThreadMyRequestsSection.IN_PROGRESS, FeedbackThreadRequestStage.InReview.myRequestsSection())
        assertEquals(FeedbackThreadMyRequestsSection.IN_PROGRESS, FeedbackThreadRequestStage.Planned.myRequestsSection())
        assertEquals(FeedbackThreadMyRequestsSection.IN_PROGRESS, FeedbackThreadRequestStage.InProgress.myRequestsSection())
        assertEquals(FeedbackThreadMyRequestsSection.SHIPPED, FeedbackThreadRequestStage.Completed.myRequestsSection())
        assertEquals(FeedbackThreadMyRequestsSection.CLOSED, FeedbackThreadRequestStage.Rejected.myRequestsSection())
        assertEquals(
            FeedbackThreadMyRequestsSection.IN_PROGRESS,
            FeedbackThreadRequestStage.Unknown("archived").myRequestsSection(),
        )
    }
}
