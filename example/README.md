# FeedbackThread Android example

This small app consumes the local `feedbackthread` Gradle module exactly as an
integrating Android app would. It does not duplicate SDK networking or UI —
it just calls the two public screens.

## Open in Android Studio

```sh
cd sdk/android/example
```

Open this directory in Android Studio (or run `./gradlew :app:assembleDebug`
from the command line). `settings.gradle.kts` pulls in the SDK with
`includeBuild("..")`, so there is nothing to publish first —
Android Studio resolves `com.feedbackthread:feedbackthread-android:0.2.0`
straight from `../` (the `sdk/android` module).

## Set a real project key

Edit `app/src/main/java/com/feedbackthread/example/ExampleConfig.kt` and
replace the `projectKey` placeholder with a real FeedbackThread project key.
`baseUrl` already points at `https://api.feedbackthread.com`; point it at a
local dev server instead if you're testing against one.

## What the two screens do

The app is a single activity with two tabs, each a direct call to an SDK
composable:

- **Request a feature** — `FeedbackThreadFeedbackScreen`. A short form
  (kind, title, details) that posts to the feedback endpoint.
- **Feature requests** — `FeedbackThreadFeatureRequestScreen`. The moderated,
  votable request list; its "add" action switches back to the first tab.

Both share one `FeedbackThreadClient` built from `ExampleConfig.configuration`.

## The customer-tier convention

`ExampleConfig.customerTier` shows the pattern documented in the SDK README:
pass whatever signal you already trust for your own paywall.
`IS_PAYING_CUSTOMER` is hardcoded to `false` here because this example has no
real billing state — a real app would read its own entitlement check instead.
The two screens above don't take a `customerTier` parameter directly; use it
when calling `FeedbackThreadClient.submit` or `.setVote` yourself.

## Toolchain

Matches the SDK: Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, JDK 17
(`jvmToolchain(17)`), `compileSdk`/`minSdk` 35/26. The Gradle wrapper is
copied from `sdk/android` so both projects always build with the same Gradle
version.
