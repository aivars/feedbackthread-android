package com.feedbackthread.sdk

import com.feedbackthread.sdk.FeedbackThreadStringResources.english
import com.feedbackthread.sdk.FeedbackThreadStringResources.italian
import com.feedbackthread.sdk.FeedbackThreadStringResources.stringName
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

public class FeedbackThreadTextTest {
    @Test
    public fun showsAnyOtherErrorsOwnMessageVerbatim() {
        assertEquals(
            FeedbackThreadText.Verbatim("Unable to resolve host"),
            IOException("Unable to resolve host").feedbackThreadDisplayText(R.string.feedbackthread_board_load_failed_fallback),
        )
    }

    @Test
    public fun fallsBackToTheScreensOwnCopyForAnErrorWithoutAMessage() {
        val text = IllegalStateException().feedbackThreadDisplayText(R.string.feedbackthread_my_requests_load_failed_fallback)

        assertEquals(FeedbackThreadText.Resource(R.string.feedbackthread_my_requests_load_failed_fallback), text)
        assertEquals("Your requests could not be loaded.", english.render(text))
        assertEquals("Non è stato possibile caricare le tue richieste.", italian.render(text))
    }

    @Test
    public fun localizesExactlyTheTwoErrorsTheConversationManagerWrites() {
        val refresh = conversationErrorText(CONVERSATIONS_REFRESH_FAILED)
        val connect = conversationErrorText(CONVERSATIONS_CONNECT_FAILED)

        assertEquals(FeedbackThreadText.Resource(R.string.feedbackthread_conversation_refresh_failed), refresh)
        assertEquals(FeedbackThreadText.Resource(R.string.feedbackthread_conversation_connect_failed), connect)
        // The state's public, English value is unchanged.
        assertEquals("Could not refresh conversations.", CONVERSATIONS_REFRESH_FAILED)
        assertEquals("Could not connect to conversations.", CONVERSATIONS_CONNECT_FAILED)
        assertEquals(CONVERSATIONS_REFRESH_FAILED, english.render(refresh))
        assertEquals(CONVERSATIONS_CONNECT_FAILED, english.render(connect))
        assertEquals("Impossibile aggiornare le conversazioni.", italian.render(refresh))
        assertEquals("Impossibile connettersi alle conversazioni.", italian.render(connect))
    }

    @Test
    public fun showsAnyOtherConversationErrorVerbatim() {
        for (error in listOf("Conversation request failed (500).", "could not refresh conversations.", "")) {
            assertEquals(FeedbackThreadText.Verbatim(error), conversationErrorText(error))
        }
    }

    @Test
    public fun titlesEveryBoardFilterAndItsEmptyState() {
        val copy = RequestFilter.entries.associateWith { filter ->
            english.strings.getValue(stringName(filter.title)) to english.strings.getValue(stringName(filter.emptyTitle))
        }

        assertEquals(
            mapOf(
                RequestFilter.ALL to ("All" to "No requests"),
                RequestFilter.IN_REVIEW to ("In review" to "No requests in review"),
                RequestFilter.PLANNED to ("Planned" to "No planned requests"),
                RequestFilter.IN_PROGRESS to ("In progress" to "No requests in progress"),
                RequestFilter.COMPLETED to ("Completed" to "No completed requests"),
            ),
            copy,
        )
        assertEquals("Tutte", italian.strings.getValue(stringName(RequestFilter.ALL.title)))
        assertEquals("Nessuna richiesta in revisione", italian.strings.getValue(stringName(RequestFilter.IN_REVIEW.emptyTitle)))
    }

    @Test
    public fun stageFiltersShareTheStatusLabels() {
        val stageFilters = mapOf(
            RequestFilter.IN_REVIEW to "In review",
            RequestFilter.PLANNED to "Planned",
            RequestFilter.IN_PROGRESS to "In progress",
            RequestFilter.COMPLETED to "Released",
        )

        for ((filter, status) in stageFilters) {
            assertEquals(filter.name, status.feedbackThreadRequestLabel(), FeedbackThreadText.Resource(filter.title))
        }
    }

    @Test
    public fun localizesEveryFeedbackKindWhileItsPublicTitleStaysEnglish() {
        for (kind in FeedbackThreadFeedbackKind.entries) {
            assertEquals(kind.name, kind.title, english.strings.getValue(stringName(kind.localizedTitle)))
        }
        assertEquals("Bug", FeedbackThreadFeedbackKind.BUG.title)
        assertEquals("Request", FeedbackThreadFeedbackKind.REQUEST.title)
        assertEquals("Review", FeedbackThreadFeedbackKind.REVIEW.title)
        assertEquals("Richiesta", italian.strings.getValue(stringName(FeedbackThreadFeedbackKind.REQUEST.localizedTitle)))
        assertEquals("Recensione", italian.strings.getValue(stringName(FeedbackThreadFeedbackKind.REVIEW.localizedTitle)))
    }
}
