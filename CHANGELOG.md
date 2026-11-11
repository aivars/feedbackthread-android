# Changelog

## 0.6.0 — 2026-11-11

The drop-in Compose screens are localized, and ship in English and Italian.

- Every end-user string of the board, the feedback form, My requests and the conversation screens is now a string resource of the library: `values/strings.xml` (English, the default) and a complete `values-it/strings.xml`. The screens follow the host app's locale, per-app language included; Android selects the best-matching resources for the app's configured locales and otherwise falls back to the default resources, which the SDK supplies in English. Every name is prefixed `feedbackthread_`, so it can't collide with the host app's own resources. Adding a language to the SDK is one new `values-xx/strings.xml`; a unit test fails on a string left untranslated, or on a translation whose placeholders drift from the English.
- **No public API change.** `FeedbackThreadFeedbackKind.title` still returns its English `String`; the screens use an internal localized accessor beside it. Exception messages stay English and unchanged, for the integrators who read and log them: the copy the SDK itself writes for end users (service unreachable, unreadable response, the HTTP-status fallback) is tagged internally, and the drop-in screens show it localized. `FeedbackThreadConversationState.error` keeps its English values; `FeedbackThreadConversationHost` shows them localized.
- Shown as written, on purpose: request titles and descriptions, error messages written by the API, and `FeedbackThreadException.InvalidConfiguration` messages, which address the integrator. A status the SDK doesn't recognize is not translated either: it is shown capitalized word by word (`on_hold` becomes `On Hold`), as it was before.
- A host app can reword any string, or add a language the SDK doesn't carry, with its own resources of the same names; see [Localization](README.md#localization) for how. An override must keep the placeholders of the string it replaces (a mismatch can crash at runtime), and overrides may need updating between SDK releases: resource names and formatting contracts are not guaranteed stable.
- Numbers follow the user's locale: the vote button (`▲ 12`, `△ 12`) and the My requests badge (`7`, `99+`) are format resources, so their digits are written in the user's own numerals instead of always ASCII. English and Italian read exactly as before. The "Android" label on a request's detail view is a string resource too, marked `translatable="false"` because it is a product name.
- Wire values are deliberately untouched: the request statuses matched from the API (`"Submitted"`, `"In review"`, `"Ready to release"`, …), serialized names, URL paths, headers and the persisted anonymous voter-identity key stay exactly as they were, even where a label reads the same.
- Two English changes. The board's empty filter states are one whole sentence per filter instead of a lowercased status interpolated into `"No … requests"`, which no other language can inflect: "No requests in review" and "No requests in progress" replace "No in review requests" and "No in progress requests" (the other filters read as before). And the vote button's screen-reader description is a proper plural: "Vote, 1 vote" instead of "Vote, 1 votes".
- Documentation corrected: the README no longer says the SDK uses `HttpURLConnection` and nothing else (OkHttp carries the live-update WebSocket of conversations, as of 0.5.0), and the example app's README and build file no longer point at old versions.

## 0.5.0 — 2026-09-15

- Add secure guest conversations and Compose reply/comment screens with foreground live updates, pagination, read markers, follow/mute and removal.
- Preserve legacy requests while attaching new feedback to a secure session.
- Add native FCM registration and notification-tap routing; host apps own permissions and Firebase setup.
- Use Keystore-backed encrypted credentials and close sessions permanently on logout.

## 0.4.1 — 2026-09-11

- Add typed `conversationSettings()` discovery for project reply, notification and public-comment policy.
- Existing Compose screens, client constructors, feedback, votes and shipped updates keep their behavior. No dependency or minimum Android version change.
- This is a compatibility release. Native conversation UI, secure customer sessions and FCM delivery are not included; full conversations currently ship in the Swift SDK.


## 0.4.0

**Breaking**: `FeedbackThreadFeatureRequestScreen` is now `FeedbackThreadBoard` — the name finally matches what it is: the complete drop-in surface (vote-sorted requests and bugs, submission, My Requests with unread badge). No deprecation shim; update call sites with a find-and-replace. Standalone screens are unchanged and documented under Advanced.


## 0.3.5

One entry point, and bugs join the board.

- The board is now the complete integration: My Requests lives behind a person icon in its top bar with an automatic unread-shipped badge (the board checks quietly on load). Hosts need exactly one screen; the standalone screens remain available.
- Accepted public bugs now share the whole board process: they appear alongside feature requests with a "Bug" tag, are votable, and get shipped badges. Nothing becomes public without your moderation, same as requests.


## 0.3.4

- Board redesign for reachability: "Suggest a feature" is now a full-width bottom button instead of a floating action button; the status filter chip row is unchanged and stays always visible.


## 0.3.3

- The feedback screen no longer offers "Review" as a submission type — review-kind cards come from App Store / Google Play ingestion, not in-app submission. Custom UI can use `FeedbackThreadFeedbackKind.submittableEntries`.


## 0.3.2

One-line integration.

- `FeedbackThreadClient(projectKey = ...)` — convenience constructor; the hosted API URL and "android" source are now defaults on `FeedbackThreadConfiguration`.
- The drop-in feedback screen attaches the host app's version automatically (versionName + versionCode); pass `appVersion` only to override. `feedbackThreadAppVersion(context)` is public for custom UI.


## 0.3.1

- One shared identity everywhere: the standalone feedback screen now submits with the same persisted anonymous voter ID the board and My Requests use, so anonymous submissions appear in My Requests and receive shipped updates. New public `feedbackThreadVoterId(context, externalUserId)` lets host apps badge with `myUpdates` at launch — signed-in or not.
- My Requests adds a "Closed" section for rejected items and folds unknown statuses into "In progress" — no more silently blank screens.
- Switching the feedback type resets the retry idempotency key, like editing the text already did.


## 0.3.0

Close the loop for end users, and harden the drop-in surfaces.

- **`FeedbackThreadMyRequestsScreen`** — new drop-in Compose screen showing the current user their own requests: pending ones under "Waiting for review", everything in progress, and shipped items with version badges. Auto-acknowledges shipped items and exposes an unread-count callback so the host app can badge its own menu.
- `myRequests()`, `myUpdates()`, `acknowledgeUpdates(ids)` on `FeedbackThreadClient`.
- `customerTierProvider` on the feedback screen and request board — the paying/free signal now works with the drop-in UI, read at submit/vote time.
- Retried submissions reuse their idempotency key until they succeed or the content changes, so an uncertain network failure can never create a duplicate.
- Unknown future statuses render instead of hiding requests from the board.
- Plain-HTTP base URLs are rejected unless the host is loopback.
- Boards arrive most-voted-first (server-side ordering change).

## 0.2.1

Initial public release.

- `FeedbackThreadClient` — coroutine client for submissions, the moderated request feed, and voting, with idempotent retries, configurable timeouts, and base-URL validation.
- `FeedbackThreadFeatureRequestScreen` — Compose feature-request board with voting, status filters, detail views, and **Shipped in x.y.z** badges on released requests.
- `FeedbackThreadFeedbackScreen` — Compose bug-report and feature-request form.
- `customerTier` — optional paying/free/custom signal on submissions and votes for revenue-aware prioritization.
- Anonymous voter identity stored in app preferences; no personal data collected.
- Published to Maven with sources via `maven-publish` (`com.feedbackthread:feedbackthread-android`).
