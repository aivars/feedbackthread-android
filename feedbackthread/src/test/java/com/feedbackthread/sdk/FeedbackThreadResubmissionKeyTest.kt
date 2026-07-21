package com.feedbackthread.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

public class FeedbackThreadResubmissionKeyTest {
    @Test
    public fun reusesTheSameKeyAcrossRetriesOfAFailedAttempt() {
        var generatedCount = 0
        val key = FeedbackThreadResubmissionKey(generator = { "key-${++generatedCount}" })

        val firstAttempt = key.beginAttempt()
        // Simulate a network failure: no success/edit call happens before retrying.
        val retryAttempt = key.beginAttempt()

        assertEquals(firstAttempt, retryAttempt)
        assertEquals(1, generatedCount)
    }

    @Test
    public fun generatesANewKeyAfterASuccessfulSubmission() {
        var generatedCount = 0
        val key = FeedbackThreadResubmissionKey(generator = { "key-${++generatedCount}" })

        val firstAttempt = key.beginAttempt()
        key.submissionSucceeded()
        val nextAttempt = key.beginAttempt()

        assertNotEquals(firstAttempt, nextAttempt)
        assertEquals(2, generatedCount)
    }

    @Test
    public fun generatesANewKeyAfterTheUserEditsTheContent() {
        var generatedCount = 0
        val key = FeedbackThreadResubmissionKey(generator = { "key-${++generatedCount}" })

        val firstAttempt = key.beginAttempt()
        key.contentChanged()
        val nextAttempt = key.beginAttempt()

        assertNotEquals(firstAttempt, nextAttempt)
        assertEquals(2, generatedCount)
    }

    @Test
    public fun generatesANewKeyWhenTheFeedbackKindChanges() {
        // FeedbackThreadFeedbackScreen wires its type FilterChip's onClick to
        // contentChanged() - documented here so a switch from, say, Request
        // to Bug after a failed submit can't reuse a key minted for a
        // materially different payload.
        var generatedCount = 0
        val key = FeedbackThreadResubmissionKey(generator = { "key-${++generatedCount}" })

        val firstAttempt = key.beginAttempt()
        // Simulates the user tapping a different feedback-kind FilterChip after a failed attempt.
        key.contentChanged()
        val nextAttempt = key.beginAttempt()

        assertNotEquals(firstAttempt, nextAttempt)
        assertEquals(2, generatedCount)
    }
}
