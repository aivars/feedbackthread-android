plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.feedbackthread.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.feedbackthread.example"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Resolved from the local composite build (see settings.gradle.kts),
    // which substitutes this coordinate with the ../ (repository root)
    // :feedbackthread project; the version is ignored while that
    // substitution is in place. Remove the includeBuild to resolve the
    // published release from mavenCentral() instead.
    implementation("com.feedbackthread:feedbackthread-android:0.6.0")

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
