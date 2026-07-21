package com.feedbackthread.sdk

/**
 * The identity-precedence rule shared by every SDK surface that needs a
 * stable per-person ID: a developer-supplied external user ID wins when
 * present and non-blank; otherwise fall back to the on-device anonymous
 * voter ID (see [anonymousVoterId]).
 *
 * Extracted as a pure function - independent of [android.content.Context] -
 * so the precedence itself is unit-testable without Robolectric, and so
 * there is exactly one place this precedence is implemented. The feature
 * request board, the standalone feedback screen, and My Requests all
 * resolve through this same function.
 */
internal fun resolveVoterId(externalUserId: String?, anonymousVoterId: () -> String): String =
    externalUserId?.trim()?.takeIf { it.isNotEmpty() } ?: anonymousVoterId()

/**
 * Public entry point for host apps that need the same stable identity the
 * drop-in screens use - e.g. calling [FeedbackThreadClient.myUpdates] at app
 * launch to badge a menu item before the user ever opens My Requests. Pass
 * your own user ID when you have one; with null this returns (creating on
 * first use) the SDK's persisted on-device anonymous voter ID.
 */
public fun feedbackThreadVoterId(context: android.content.Context, externalUserId: String? = null): String =
    resolveVoterId(externalUserId) { anonymousVoterId(context) }

/**
 * The host app's version name and code, e.g. `"2.4.1 (317)"` - the value the
 * drop-in feedback screen attaches to submissions by default so integrators
 * never have to plumb it through themselves.
 */
public fun feedbackThreadAppVersion(context: android.content.Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val name = info.versionName ?: "unknown"
    @Suppress("DEPRECATION")
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    "$name ($code)"
} catch (_: Exception) {
    "unknown"
}
