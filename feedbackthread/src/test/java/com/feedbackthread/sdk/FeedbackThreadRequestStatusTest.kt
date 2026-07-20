package com.feedbackthread.sdk

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
    }

    @Test
    public fun labelsPendingReviewHonestlyWithoutImplyingModerationHappened() {
        assertEquals("Waiting for review", "Submitted".feedbackThreadRequestLabel())
    }

    @Test
    public fun preservesAFabricatedUnrecognizedStatusRatherThanDroppingIt() {
        val status = "archived"

        assertEquals(FeedbackThreadRequestStage.Unknown("archived"), status.feedbackThreadRequestStage())
        assertEquals("Archived", status.feedbackThreadRequestLabel())
    }

    @Test
    public fun sensiblyCapitalizesAnUnrecognizedStatusWithSeparators() {
        assertEquals("On Hold", "on_hold".feedbackThreadRequestLabel())
        assertEquals("Needs Triage", "NEEDS-TRIAGE".feedbackThreadRequestLabel())
    }
}
