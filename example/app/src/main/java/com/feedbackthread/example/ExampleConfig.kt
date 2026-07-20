package com.feedbackthread.example

import com.feedbackthread.sdk.FeedbackThreadConfiguration
import com.feedbackthread.sdk.FeedbackThreadCustomerTier

/**
 * Single place a real integrator would edit: the project key and the
 * customer-tier signal. Everything else in this app reads from here.
 */
object ExampleConfig {
    val configuration = FeedbackThreadConfiguration(
        baseUrl = "https://api.feedbackthread.com",
        projectKey = "YOUR_FEEDBACKTHREAD_PROJECT_KEY",
        source = "android",
    )

    // Replace with whatever your app already uses to tell paying customers
    // from free ones — the same signal you trust for your own paywall.
    // Hardcoded here since this example has no real billing state.
    private const val IS_PAYING_CUSTOMER = false

    val customerTier: FeedbackThreadCustomerTier =
        if (IS_PAYING_CUSTOMER) FeedbackThreadCustomerTier.Paying else FeedbackThreadCustomerTier.Free
}
