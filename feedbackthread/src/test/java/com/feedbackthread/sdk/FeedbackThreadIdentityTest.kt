package com.feedbackthread.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

public class FeedbackThreadIdentityTest {
    @Test
    public fun returnsTheProvidedExternalIdTrimmedWithoutFallingBack() {
        var anonymousCalls = 0
        val resolved = resolveVoterId("  user-42  ") {
            anonymousCalls++
            "anon-id"
        }

        assertEquals("user-42", resolved)
        assertEquals(0, anonymousCalls)
    }

    @Test
    public fun fallsBackToTheAnonymousIdWhenExternalIdIsNull() {
        assertEquals("anon-id", resolveVoterId(null) { "anon-id" })
    }

    @Test
    public fun fallsBackToTheAnonymousIdWhenExternalIdIsBlank() {
        assertEquals("anon-id", resolveVoterId("   ") { "anon-id" })
    }
}

class FeedbackThreadConfigurationDefaultTest {
    @org.junit.Test
    fun `defaults point at the hosted API with the android source`() {
        val configuration = FeedbackThreadConfiguration(projectKey = "ft_pk_test")
        org.junit.Assert.assertEquals("https://api.feedbackthread.com", configuration.baseUrl)
        org.junit.Assert.assertEquals("android", configuration.source)
        org.junit.Assert.assertEquals("ft_pk_test", configuration.projectKey)
    }

    @org.junit.Test
    fun `one-line client constructor builds a working client`() {
        FeedbackThreadClient(projectKey = "ft_pk_test")
    }
}
