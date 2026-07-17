# FeedbackThread Android SDK

The private-alpha Android SDK provides an async Kotlin client plus native Compose feedback and feature-request screens. It targets minSdk 26 and matches Apnea Android's current Kotlin 2.1, AGP 8.7, Gradle 8.11, and Compose toolchain.

## Add the local SDK to Apnea

In Apnea Android's `settings.gradle.kts`, add:

```kotlin
includeBuild("../Loopline/sdk/android")
```

In `app/build.gradle.kts`, add:

```kotlin
implementation("com.feedbackthread:feedbackthread:0.1.0-SNAPSHOT")
```

Gradle's included-build substitution resolves that coordinate to the local FeedbackThread SDK. A published Maven coordinate will replace this before public beta.

## Configure the client

```kotlin
val feedbackThread = FeedbackThreadClient(
    FeedbackThreadConfiguration(
        baseUrl = "https://api.feedbackthread.com",
        projectKey = BuildConfig.FEEDBACKTHREAD_PROJECT_KEY,
        source = "android",
    ),
)
```

Provide `FEEDBACKTHREAD_PROJECT_KEY` through a local Gradle property and expose it as a `BuildConfig` field. It is a public project identifier and must be assumed extractable from the APK: it can submit feedback, read the moderated Android request feed, and vote, but cannot read or change the private dashboard, use MCP, or access store credentials.

## Show the feature-request list

```kotlin
FeedbackThreadFeatureRequestScreen(
    client = feedbackThread,
    externalUserId = signedInUserId,
    onAddRequest = openFeedbackForm,
    onDismiss = onBack,
)
```

The list is moderated: Open and Rejected requests stay in the developer dashboard. App users can filter approved requests by In review, Planned, In progress, and Completed. Submitted requests appear only after approval.

Tapping a request opens its complete, untruncated description. Voting stays available on both the list and detail screens, including an accessible 64×56 dp minimum touch target.

The Android SDK requests only Android-visible features. Apple Watch requests never appear. If the app has no account ID, the screen stores a random anonymous voter ID in app preferences.

## Show the feedback screen

```kotlin
FeedbackThreadFeedbackScreen(
    client = feedbackThread,
    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    onDismiss = onBack,
)
```

Every submission receives a unique `Idempotency-Key`, preventing a retried request from creating duplicate feedback.

## Checks

The SDK requires JDK 17. On this development Mac, use the installed Homebrew runtime with `JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` when Java is not registered system-wide.

```sh
./gradlew :feedbackthread:testDebugUnitTest
./gradlew :feedbackthread:assembleDebug
```

The opt-in live integration test runs when `FEEDBACKTHREAD_LIVE_BASE_URL` and `FEEDBACKTHREAD_LIVE_PROJECT_KEY` are present in the environment. The legacy names remain accepted during the private alpha.
