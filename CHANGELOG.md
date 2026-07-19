# Changelog

All notable changes to the FeedbackThread Android SDK are documented here.

## 0.2.0

### Breaking

- **FeedbackThread-first rename.** The real implementation now lives in the `com.feedbackthread.sdk` package (`FeedbackThreadClient`, `FeedbackThreadConfiguration`, `FeedbackThreadFeedbackSubmission`, `FeedbackThreadFeatureRequest`, `FeedbackThreadCustomerTier`, `FeedbackThreadFeedbackKind`, `FeedbackThreadFeatureRequestScreen`, `FeedbackThreadFeedbackScreen`, and so on). The library's `namespace` moved from `com.loopline.sdk` to `com.feedbackthread.sdk` to match.
- The old `com.loopline.sdk` package is now a thin `@Deprecated` compatibility layer: typealiases for the data/exception/client types, and one-line delegating `@Composable` wrapper functions for the two screens (Kotlin typealiases can't target top-level functions). It is planned for removal in **0.3.0**.
- The `com.feedbackthread.sdk.FeedbackThread` wrapper file (which previously re-exported `com.loopline.sdk` types under `FeedbackThread`-prefixed names) is gone — there is no longer a second alias layer. Its former contents are now the real declarations.

### Added

- Maven publishing: the library now applies `maven-publish` with an AGP `singleVariant("release")` publication. Coordinates: `com.feedbackthread:feedbackthread-android:0.2.0`. The POM includes name, description, project URL, MIT license, developer, and SCM metadata. Verified locally via `./gradlew :feedbackthread:publishToMavenLocal`.

### Changed

- `version` dropped the `-SNAPSHOT` suffix and moved to `0.2.0`.

### Compatibility

- `FeedbackThreadFeedbackKind` already included a `REVIEW` case (serialized `"Reviews"`) prior to this release; this release does not change its cases, only its package.
- The anonymous voter ID `SharedPreferences` migration (new `feedbackthread_sdk` prefs file, falling back to the legacy `loopline_sdk` file) is unchanged by the package rename.

## 0.1.0

- Initial private-alpha Android SDK: async Kotlin client, Compose feedback and feature-request screens, customer tier support, shipped-in-version badges.
