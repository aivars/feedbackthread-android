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
}
