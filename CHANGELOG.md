# Changelog

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
