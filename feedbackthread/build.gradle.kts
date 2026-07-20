plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
}

group = "com.feedbackthread"
version = "0.2.1"

android {
    namespace = "com.feedbackthread.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.feedbackthread"
                artifactId = "feedbackthread-android"
                version = project.version.toString()

                pom {
                    name.set("FeedbackThread Android SDK")
                    description.set(
                        "Kotlin client plus native Compose feedback and feature-request " +
                            "screens for the FeedbackThread feedback and feature-request platform.",
                    )
                    url.set("https://feedbackthread.com")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("aivars")
                            name.set("Aivars Meijers")
                        }
                    }

                    scm {
                        url.set("https://github.com/aivars/feedbackthread-android")
                        connection.set("scm:git:https://github.com/aivars/feedbackthread-android.git")
                        developerConnection.set("scm:git:ssh://git@github.com/aivars/feedbackthread-android.git")
                    }
                }
            }
        }
    }
}
