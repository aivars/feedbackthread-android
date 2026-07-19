# FeedbackThread Android SDK

The private-alpha Android SDK provides an async Kotlin client plus native Compose feedback and feature-request screens. It targets minSdk 26 and matches Apnea Android's current Kotlin 2.1, AGP 8.7, Gradle 8.11, and Compose toolchain.

## Add the SDK to Apnea

### Maven coordinates (Central publication pending)

The published coordinate is:

```kotlin
implementation("com.feedbackthread:feedbackthread-android:0.2.0")
```

This has not been published to Maven Central yet. Until then, use one of the two options below.

### Option A: `mavenLocal()`

Publish the SDK to your local Maven repository from this checkout:

```sh
./gradlew :feedbackthread:publishToMavenLocal
```

Then add `mavenLocal()` to Apnea Android's repository list and depend on the same coordinate:

```kotlin
implementation("com.feedbackthread:feedbackthread-android:0.2.0")
```

### Option B: composite build (local development)

In Apnea Android's `settings.gradle.kts`, add:

```kotlin
includeBuild("../Loopline/sdk/android") {
    dependencySubstitution {
        // Required: the Gradle project is named :feedbackthread while the
        // published artifactId is feedbackthread-android, so automatic
        // substitution does not match the coordinate on its own.
        substitute(module("com.feedbackthread:feedbackthread-android"))
            .using(project(":feedbackthread"))
    }
}
```

In `app/build.gradle.kts`, add:

```kotlin
implementation("com.feedbackthread:feedbackthread-android:0.2.0")
```

The explicit substitution resolves that coordinate to the local FeedbackThread SDK regardless of the exact version string. A working consumer lives in `examples/android`.

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

The list is moderated: Submitted and Rejected requests stay in the developer dashboard. App users can filter approved requests by In review, Planned, In progress, and Completed. New requests appear only after approval.

Tapping a request opens its complete, untruncated description. Voting stays available on both the list and detail screens, including an accessible 64×56 dp minimum touch target.

The Android SDK requests only Android-visible features. Apple Watch requests never appear. If the app has no account ID, the screen stores a random anonymous voter ID in app preferences.

Requests whose status is Completed and whose release has been published show a **Shipped in `<version>`** badge next to the status pill, using the `shippedInVersion` value from the request feed.

### Statuses

The dashboard and API renamed two request statuses: `Open` is now `Submitted`, and `Under review` is now `In review`. The SDK's models and public request feed already use the new labels; the feature-request screen still tolerates the old `Under review` label when filtering, in case cached data has not refreshed yet.

### Customer tier

`FeedbackThreadFeedbackSubmission` and `FeedbackThreadClient.setVote(requestId, voted, externalUserId, customerTier)` both accept an optional `customerTier`:

```kotlin
FeedbackThreadFeedbackSubmission(
    kind = FeedbackThreadFeedbackKind.REQUEST,
    title = "Add dark mode",
    text = "Would love a dark theme.",
    customerTier = FeedbackThreadCustomerTier.Paying,
)
```

`FeedbackThreadCustomerTier` is `FeedbackThreadCustomerTier.Free`, `FeedbackThreadCustomerTier.Paying`, or `FeedbackThreadCustomerTier.Custom("<label>")` for plans that don't fit that binary. The convention: pass the same signal you trust for your own paywall. FeedbackThread uses it to help prioritize feedback and votes from paying customers. The field is omitted from the request body entirely when left `null`, so existing integrations are unaffected.

## Show the feedback screen

```kotlin
FeedbackThreadFeedbackScreen(
    client = feedbackThread,
    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    onDismiss = onBack,
)
```

Every submission receives a unique `Idempotency-Key`, preventing a retried request from creating duplicate feedback.

## Migrating from the `com.loopline.sdk` package (0.1.x → 0.2.0)

As of 0.2.0, the real implementation lives in `com.feedbackthread.sdk`. The previous `com.loopline.sdk` package (`LooplineClient`, `LooplineConfiguration`, `LooplineFeedbackSubmission`, `LooplineFeatureRequestScreen`, `LooplineFeedbackScreen`, and so on) still compiles — it is now a thin `@Deprecated(..., ReplaceWith(...))` compatibility layer that delegates to `com.feedbackthread.sdk` — but Android Studio will flag every use with a deprecation warning, and **it is removed in 0.3.0**. Existing integrators (Apnea, FocusLock) should update their imports from `com.loopline.sdk` to `com.feedbackthread.sdk` before then; most IDEs can apply the `ReplaceWith` quick fix automatically for each deprecated symbol.

## Checks

The SDK requires JDK 17. On this development Mac, use the installed Homebrew runtime with `JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` when Java is not registered system-wide.

```sh
./gradlew :feedbackthread:testDebugUnitTest
./gradlew :feedbackthread:assembleDebug
./gradlew :feedbackthread:publishToMavenLocal
```

The opt-in live integration test runs when `FEEDBACKTHREAD_LIVE_BASE_URL` and `FEEDBACKTHREAD_LIVE_PROJECT_KEY` are present in the environment. The legacy names remain accepted during the private alpha.
