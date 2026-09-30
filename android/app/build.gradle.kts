plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    kotlin("kapt")
}

android {
    namespace = "in.raahi.app"
    compileSdk = 34

    defaultConfig {
        // Matches the existing Firebase project's registered Android package name
        // (see google-services.json) so we reuse the existing Firebase project —
        // phone-auth SMS setup, project config, etc. — instead of standing up a new one.
        applicationId = "com.raahi.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.0.0" // v2 = native Kotlin rewrite; Flutter app was 1.x

        // BASE_URL is per-environment, never hardcoded — set via gradle.properties / CI secrets
        buildConfigField("String", "BASE_URL", "\"${project.findProperty("BASE_URL") ?: "https://api.raahi.in/api/v1/"}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Space Grotesk + Plus Jakarta Sans for the "concept redesign" visual language —
    // downloaded at runtime via Play Services' Fonts provider rather than bundled binaries
    // (see res/values/font_certs.xml for the one-time setup step this needs).
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // MapLibre GL Native — OSS Mapbox GL fork, no API key needed for raw XYZ raster tiles.
    // Published on Maven Central since v10+, so no extra repository entry needed beyond
    // what settings.gradle.kts already declares.
    implementation("org.maplibre.gl:android-sdk:11.5.2")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    testImplementation("junit:junit:4.13.2")
}
