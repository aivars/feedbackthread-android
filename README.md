# FeedbackThread Android SDK

![Platform](https://img.shields.io/badge/platform-Android%208%2B%20(minSdk%2026)-3DDC84)
![Kotlin](https://img.shields.io/badge/kotlin-2.1-7F52FF)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

Native in-app feedback for Android: a drop-in Compose feature-request board with voting, a feedback form, and automatic **"Shipped in x.y.z"** badges — wired to your [FeedbackThread](https://feedbackthread.com) dashboard, roadmap, and AI-agent workflow.

- 🗳️ **Feature-request board** — moderated public requests with voting, status filters, and full detail views
- ✍️ **Feedback form** — bug reports and feature requests straight into your triage inbox
- 🚀 **Close the loop** — requests attached to a published release show a *Shipped in x.y.z* badge to the people who asked
- 💎 **Paying-customer signal** — optionally tag submissions and votes with your billing state for revenue-aware prioritization
- 🔒 **Privacy-first** — no email or name is required; you control whether to pass an external user identifier; anonymous voter IDs stay on-device
- 🪶 **Lean** — a coroutine client over `HttpURLConnection` + `kotlinx.serialization`, Compose screens, nothing else

## What your users see

`FeedbackThreadBoard` respects your Material 3 theme, light and dark:

<img src="docs/board-themes.png" alt="FeedbackThreadBoard on Android in light and dark mode - vote-sorted feature requests with status filters and Shipped badges" width="760">

## Requirements

- minSdk 26 (Android 8.0) · compileSdk 35
- Kotlin 2.1 · Jetpack Compose · JDK 17 toolchain

## Installation

The SDK is on Maven Central — no extra repository setup needed:

```kotlin
implementation("com.feedbackthread:feedbackthread-android:0.5.0")
```

Alternatively, publish locally from this repository:

```sh
./gradlew :feedbackthread:publishToMavenLocal   # then add mavenLocal() to your repositories
```

or consume it as a composite build in your app's `settings.gradle.kts`:

```kotlin
includeBuild("<path-to>/sdk/android") {
    dependencySubstitution {
        substitute(module("com.feedbackthread:feedbackthread-android"))
            .using(project(":feedbackthread"))
    }
}
```

A complete working consumer lives in [`example/`](./example).

## Quick start

Grab your project key from the dashboard (**SDK setup**). It's a public, low-privilege identifier — safe to ship in your APK. It can submit feedback, read the moderated request feed, and vote; it cannot touch your private dashboard data.

```kotlin
val feedbackThread = FeedbackThreadClient(projectKey = BuildConfig.FEEDBACKTHREAD_PROJECT_KEY)
```

Present `FeedbackThreadBoard` and you're done — it's the complete integration:

```kotlin
FeedbackThreadBoard(
    client = feedbackThread,
    externalUserId = signedInUserId,   // optional; anonymous ID used otherwise
    onAddRequest = { showFeedbackForm = true },   // present FeedbackThreadFeedbackScreen — see Advanced below
    onDismiss = onBack,
)
```

One screen gives users a vote-sorted board of requests and bugs with status filters (In review · Planned · In progress · Completed) and **Shipped in x.y.z** badges, a **Suggest a feature** button (wired to `onAddRequest`), and a **My requests** tab with an unread badge that closes the loop on their own cards — all built in. Only Android-visible requests appear.

### Tell FeedbackThread who pays

Pass the same signal you trust for your own paywall — it powers per-request "N paying customers want this" prioritization in the dashboard:

```kotlin
FeedbackThreadFeedbackSubmission(
    kind = FeedbackThreadFeedbackKind.REQUEST,
    title = "Add dark mode",
    text = "Would love a dark theme.",
    customerTier = if (billing.isPro) FeedbackThreadCustomerTier.Paying else FeedbackThreadCustomerTier.Free,
)
```

`FeedbackThreadCustomerTier` is `Free`, `Paying`, or `Custom("family")` — and omitted from the request entirely when left `null`.

## Advanced: standalone surfaces

`FeedbackThreadBoard` is a complete integration on its own, but its pieces are also available individually for contextual placements — e.g. a "Report a bug" row in your settings screen that jumps straight to the form instead of the full board. Each surface below is fully supported as a standalone screen.

### Show the feedback form

```kotlin
FeedbackThreadFeedbackScreen(
    client = feedbackThread,
    onDismiss = onBack,
)
```

Every submission carries an idempotency key, so a retried request never creates a duplicate.

### Show users their own requests

The board only ever shows moderated, public cards. `FeedbackThreadMyRequestsScreen` closes the loop for the person who submitted: it always shows their own cards, including ones still waiting for review that never appear anywhere public.

```kotlin
FeedbackThreadMyRequestsScreen(
    client = feedbackThread,
    onDismiss = onBack,
    externalUserId = signedInUserId,   // optional; falls back to the same anonymous ID as the board
    onUnreadCountChange = { unreadCount ->
        // badge your own UI, e.g. a bottom-nav item
    },
)
```

It groups cards into **Waiting for review**, **In progress**, and **Shipped**, and auto-acknowledges shipped cards as soon as they're viewed.

`onUnreadCountChange` only fires once the screen is opened — too late for a badge that should already be showing at launch. Call `myUpdates(externalUserId)` yourself on app start or foreground to get `unreadCount` ahead of time:

```kotlin
LaunchedEffect(Unit) {
    // Works for anonymous users too: feedbackThreadVoterId() returns the
    // SDK's persisted on-device ID when you don't pass your own.
    val userId = feedbackThreadVoterId(context, signedInUserId)
    runCatching { feedbackThread.myUpdates(userId) }
        .onSuccess { badgeCount = it.unreadCount }
}
```

The client exposes all three calls directly if you're building custom UI: `myRequests(externalUserId)`, `myUpdates(externalUserId)`, and `acknowledgeUpdates(ids, externalUserId)`.

### Use the client directly

The Compose screens are optional. `FeedbackThreadClient` exposes `submit(...)`, `requests(...)`, and `setVote(...)` for custom UI, with configurable connect/read timeouts and base-URL validation.

## Development

```sh
./gradlew :feedbackthread:testDebugUnitTest
./gradlew :feedbackthread:assembleDebug
./gradlew :feedbackthread:publishToMavenLocal
```

The opt-in live integration test runs when `FEEDBACKTHREAD_LIVE_BASE_URL` and `FEEDBACKTHREAD_LIVE_PROJECT_KEY` are set.

## How it fits together

The SDK is the in-app half of FeedbackThread: feedback lands in a keyboard-driven triage inbox, becomes cards on your roadmap, ships in tracked releases — and your AI agent can work the whole backlog over MCP. Learn more at [feedbackthread.com](https://feedbackthread.com).

## Contributing

This repository is where the Android SDK is developed, and pull requests are
merged here. See [CONTRIBUTING.md](CONTRIBUTING.md) for how to run the tests and
for the one invariant worth knowing before you touch status handling.

## License

MIT — see [LICENSE](LICENSE).

## Conversation service compatibility (0.4.1)

Existing integrations keep working without changes. `client.conversationSettings()`
reads the service's `FeedbackThreadConversationSettings`: private replies and
notifications are enabled; public comments follow project settings and default off.
Call this only when your application needs the policy; existing screens do not
make new requests automatically. Older/self-hosted servers without the endpoint
return the existing `FeedbackThreadException.Server` (404).

Project policy does not grant device notification permission. Private threads
require secure customer credentials; an external user ID is not authentication.

## Replies and public comments (0.5.0)

Version 0.5.0 adds secure guest sessions, private replies, public comment threads,
history pagination, read markers, follow/mute, removal and live unread state.
Requires the FeedbackThread server update released on 2026-09-15.
Physical-device push delivery has not been verified in a host app; validate your
Firebase configuration, permission flow and notification routing before rollout.

Keep one manager per host-app account in your app model:

```kotlin
val conversations = FeedbackThreadConversations(
    context = applicationContext,
    configuration = FeedbackThreadConfiguration(projectKey = "YOUR_PUBLIC_PROJECT_KEY"),
    accountScope = "local-account-id",
)
```

At the app root, use `FeedbackThreadConversationHost(conversations) { client -> … }`
and pass that supplied client to `FeedbackThreadBoard`, `FeedbackThreadMyRequestsScreen`
and `FeedbackThreadFeedbackForm`. The host prepares secure credentials, manages
foreground live updates, shows unread messages, and opens the discussion screen.
The board exposes Comments when the project enables them; My Requests exposes
Replies only for feedback submitted with the secure session. Existing cards and
votes remain accessible through the legacy identity without claiming ownership.

For custom UI, collect `conversations.state` and use `history`, `send`, `markRead`,
`follow`, `remove` and `open`. Supply the same `clientId` when retrying a send.
Only mark messages read once they have been displayed. `runLive()` should run only
while foregrounded; the Compose host handles its lifecycle.

### Android notifications

Configure FCM credentials in the project's **App discussions → Android push
configuration**. Enable Firebase Cloud Messaging and add Firebase Messaging to
the host app following Firebase's Android setup. The SDK does not own your
Firebase initialization or notification permission prompt.

- Pass new and rotated Firebase tokens to `registerDeviceToken(token)`.
- Forward notification-tap data to `handleNotification(remoteMessageData)` (or
  the matching activity intent extras). Route data never grants access by itself.
- Set up the host's notification channel and request permission when appropriate.
- Use `unregisterDeviceToken` when detaching a device without ending the session.

Notifications contain a generic alert, never the private message body. Denying
notification permission does not prevent reading replies in the app. Validate
background delivery on a physical device with the host's Firebase project before
release; unit tests use a provider stub.

### Identity and privacy

Tokens are encrypted with an Android Keystore key; ciphertext lives in the app's
no-backup directory. Do not substitute plain preferences. A custom secure store
can implement `FeedbackThreadConversationStore`.

`accountScope` is local isolation, not verified account login or cross-device
identity merging. On logout, await `logout()` and replace the manager for the next
account; the old object is permanently closed. Handle revocation errors and retry
while retaining that old object. Never reuse its client after logout.

Public comments default off. Disabling them hides existing discussion without
deleting it. Private replies remain enabled. Image uploads are deferred and have
no SDK or server implementation in this change. New Compose conversation copy is
English, matching the current Android SDK; localization remains a release check.
