# Contributing

Thanks for being here. This repository is where the FeedbackThread Android SDK
is developed — issues and pull requests are read, and merged pull requests land
here directly.

> **Changed August 2026.** This repository used to be a read-only mirror,
> published by force-pushing from a private monorepo, which quietly meant a
> pull request could never be merged. That is no longer the case. The SDK lives
> here now.

## Requirements

- JDK 17
- Android compile SDK 35, `minSdk` 26
- Kotlin with Compose and kotlinx.serialization

The module is `:feedbackthread`, published as `com.feedbackthread`. Runtime
dependencies are deliberately minimal — coroutines and kotlinx.serialization —
and additions need a good reason.

## Running the tests

```sh
./gradlew :feedbackthread:testDebugUnitTest :feedbackthread:assembleDebug
```

Both run on every pull request in CI, from a clean checkout. The assemble step
matters as much as the tests: it is what catches Compose code that type-checks
in isolation but fails to build.

The `example/` app is a separate Gradle build and a good place to try a change
against a real host app.

## One invariant worth knowing

**Server status values are not display strings, even where they read
identically.** The SDK matches the wire values the API sends — `"Submitted"`,
`"In review"`, `"In progress"`, `"Ready to release"` — and the labels a user
sees are separate. Several collide textually, so these must never be changed by
find-and-replace. Changing a wire string breaks status mapping silently, at
runtime only.

The same goes for serialized names, URL paths, headers, and the persisted
anonymous voter-identity key.

Errors addressed to the integrating developer, rather than to end users, stay in
English on purpose.

## Pull requests

- Branch from `main`, keep the change focused.
- Explain the reasoning in the description, not just the what. If you made a
  judgement call, say which way and why.
- New behaviour comes with a test.
- Public API changes need a note in `CHANGELOG.md` under `## Unreleased`.

Releases, version bumps and Maven Central publication are handled by the
maintainer; you do not need to bump a version in your pull request.

## Reporting a bug

Open an issue with the SDK version, Android version, and the smallest
reproduction you can manage. If it involves a request board, whether the card
was public or still in triage is usually the detail that matters.

## Licence

MIT. By contributing you agree your work ships under it.
