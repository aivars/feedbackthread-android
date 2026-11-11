package com.feedbackthread.sdk

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text a drop-in screen shows, carried as a value until composition resolves
 * it in the host app's current language. Needed wherever text is produced
 * outside composition - a catch block in a launched coroutine, a status
 * label - and may be either one of the SDK's own strings or text that must
 * be shown exactly as it came.
 *
 * Text that is always one of the SDK's own strings travels as its
 * `@StringRes` id instead.
 */
internal sealed interface FeedbackThreadText {
    /** One of the SDK's string resources, with its format arguments. */
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : FeedbackThreadText

    /**
     * Shown as given, never looked up: a message the API wrote, a status
     * this SDK doesn't recognize, an error addressed to the integrator.
     */
    data class Verbatim(val value: String) : FeedbackThreadText
}

@Composable
internal fun FeedbackThreadText.resolve(): String = when (this) {
    // Without arguments the string is not run through a formatter at all.
    is FeedbackThreadText.Resource -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
    is FeedbackThreadText.Verbatim -> value
}

/**
 * What a drop-in screen shows for an error it caught: the end-user copy the
 * SDK authored for it ([FeedbackThreadException.userMessage]), in the user's
 * language; otherwise the error's own message, verbatim - an
 * [FeedbackThreadException.InvalidConfiguration] addresses the integrator
 * and stays English on purpose, and an untagged
 * [FeedbackThreadException.Server] message is the API's own - or [fallback]
 * when the error has no message at all.
 */
internal fun Throwable.feedbackThreadDisplayText(@StringRes fallback: Int): FeedbackThreadText =
    (this as? FeedbackThreadException)?.userMessage
        ?: message?.let { FeedbackThreadText.Verbatim(it) }
        ?: FeedbackThreadText.Resource(fallback)
