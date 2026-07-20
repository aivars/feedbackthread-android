# Changelog

## 0.2.1

Initial public release.

- `FeedbackThreadClient` — coroutine client for submissions, the moderated request feed, and voting, with idempotent retries, configurable timeouts, and base-URL validation.
- `FeedbackThreadFeatureRequestScreen` — Compose feature-request board with voting, status filters, detail views, and **Shipped in x.y.z** badges on released requests.
- `FeedbackThreadFeedbackScreen` — Compose bug-report and feature-request form.
- `customerTier` — optional paying/free/custom signal on submissions and votes for revenue-aware prioritization.
- Anonymous voter identity stored in app preferences; no personal data collected.
- Published to Maven with sources via `maven-publish` (`com.feedbackthread:feedbackthread-android`).
