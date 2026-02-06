import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.diffplug.spotless")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

// SYNC: Automatically copy the root LICENSE into the app assets during build
val copyLicenseTask = tasks.register<Copy>("copyLicense") {
    from(rootProject.file("LICENSE"))
    into(layout.buildDirectory.dir("generated/assets/license"))
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktfmt().googleStyle() // Use Google's standard Kotlin format
        trimTrailingWhitespace()
        endWithNewline()
    }
}
tasks.named("preBuild") {
    dependsOn("spotlessApply")
}

android {
    namespace = "com.syncrow"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.syncrow"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // SECURE: Pulled from local.properties (local dev) or Environment variables (CI)
        val stravaId = localProperties.getProperty("strava_client_id") 
            ?: System.getenv("STRAVA_CLIENT_ID") 
            ?: ""
        val stravaSecret = localProperties.getProperty("strava_client_secret") 
            ?: System.getenv("STRAVA_CLIENT_SECRET") 
            ?: ""

        buildConfigField("String", "STRAVA_CLIENT_ID", "\"$stravaId\"")
        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"$stravaSecret\"")
    }

    androidResources {
        // SYNC: Restrict to officially supported languages from AGENTS.md
        localeFilters += listOf("en", "fr", "de", "es", "it")
    }

    sourceSets {
        getByName("main") {
            // Register the generated directory as an assets source
            assets.srcDirs(copyLicenseTask)
        }
    }

    signingConfigs {
        create("release") {
            // Retrieve signing credentials from env vars (CI) or local properties (Local)
            val keystorePath = System.getenv("SIGNING_KEY_PATH") 
                ?: localProperties.getProperty("signing_key_path")
            
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEY_STORE_PASSWORD") 
                    ?: localProperties.getProperty("key_store_password")
                keyAlias = System.getenv("ALIAS") 
                    ?: localProperties.getProperty("alias")
                keyPassword = System.getenv("KEY_PASSWORD") 
                    ?: localProperties.getProperty("key_password")
            }
        }
    }

    buildTypes {
        release {
            // Apply the signing config if it's set up
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // Core & UI
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Bluetooth
    implementation("com.polidea.rxandroidble3:rxandroidble:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.7.3")

    // Database (Room)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking (Retrofit & OkHttp)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
