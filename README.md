# Loopline Android SDK

The private-alpha Android SDK provides an async Kotlin ingestion client and a native Compose feedback screen. It targets minSdk 26 and matches Apnea Android's current Kotlin 2.1, AGP 8.7, Gradle 8.11, and Compose toolchain.

## Add the local SDK to Apnea

In Apnea Android's `settings.gradle.kts`, add:

```kotlin
includeBuild("../Loopline/sdk/android")
```

In `app/build.gradle.kts`, add:

```kotlin
implementation("com.loopline:loopline:0.1.0-SNAPSHOT")
```

Gradle's included-build substitution resolves that coordinate to the local Loopline SDK. A published Maven coordinate will replace this before public beta.

## Configure the client

```kotlin
val loopline = LooplineClient(
    LooplineConfiguration(
        baseUrl = "https://loopline-staging.aivars-meijers.workers.dev",
        projectKey = BuildConfig.LOOPLINE_PROJECT_KEY,
        source = "android",
    ),
)
```

Provide `LOOPLINE_PROJECT_KEY` through a local Gradle property and expose it as a `BuildConfig` field. It is an ingestion-only app credential: it can create feedback but cannot read or change the dashboard.

## Show the feedback screen

```kotlin
LooplineFeedbackScreen(
    client = loopline,
    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    onDismiss = onBack,
)
```

Every submission receives a unique `Idempotency-Key`, preventing a retried request from creating duplicate feedback.

## Checks

The SDK requires JDK 17. On this development Mac, use the installed Homebrew runtime with `JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` when Java is not registered system-wide.

```sh
./gradlew :loopline:testDebugUnitTest
./gradlew :loopline:assembleDebug
```

The opt-in live integration test runs when `LOOPLINE_LIVE_BASE_URL` and `LOOPLINE_LIVE_PROJECT_KEY` are present in the environment.
