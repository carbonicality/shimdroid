plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "dev.openanalytics.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.openanalytics.wear"
        minSdk = 30 // Wear OS 3+
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        // A fixed key checked into the repo, so every build (local or CI) has the
        // same SHA-1 to register with your Google OAuth client. Fine for a
        // sideloaded personal app; see README if you want your own key.
        create("shared") {
            storeFile = file("openanalytics-debug.keystore")
            storePassword = "android"
            keyAlias = "openanalytics"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

roborazzi {
    outputDir.set(file("screenshots"))
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material") // pull-to-refresh only
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // Google account sign-in + OAuth tokens via Play services
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Tile
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    // Full Guava: tiles-renderer brings it in, which empties the standalone listenablefuture artifact
    implementation("com.google.guava:guava:33.3.1-android")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real org.json for JVM unit tests
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")

    // Screenshot tests (Robolectric + Roborazzi)
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.43.0")
    // debug (not test) so its resources are merged and visible to Robolectric
    debugImplementation("androidx.wear.tiles:tiles-renderer:1.4.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}
