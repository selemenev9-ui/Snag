plugins {
    alias(libs.plugins.android.application)
    // AGP 9 has built-in Kotlin support — no kotlin-android plugin.
    alias(libs.plugins.kotlin.compose)
}

// Opt-in design studies keep the shipping debug package and its data separate.
val snagDesign = providers.gradleProperty("snagDesign").orElse("classic").get()
require(snagDesign in setOf("classic", "paper", "cinema", "signal"))
val studyName = mapOf("paper" to "Snag Paper", "cinema" to "Snag Cinema", "signal" to "Snag Signal")

android {
    namespace = "app.snag"
    compileSdk = 37

    defaultConfig {
        applicationId = if (snagDesign == "classic") "app.snag" else "app.snag.$snagDesign"
        buildConfigField("String", "SNAG_DESIGN", "\"$snagDesign\"")
        manifestPlaceholders["snagLabel"] = studyName[snagDesign] ?: "Snag Classic"
        manifestPlaceholders["snagIcon"] = if (snagDesign == "classic") "@drawable/ic_launcher" else "@drawable/ic_study_$snagDesign"
        minSdk = 29
        targetSdk = 37
        versionCode = 5
        versionName = "0.1.4-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk { abiFilters += "arm64-v8a" }
    }

    // Preserve the installed beta certificate; never commit signing material.
    val explicitSigningStore = providers.environmentVariable("SNAG_SIGNING_STORE").orNull
    val preservedBetaStore = file(System.getProperty("user.home") + "/.codex/signing/snag-beta.keystore")
    if (explicitSigningStore != null || preservedBetaStore.isFile) {
        signingConfigs.getByName("debug").storeFile = explicitSigningStore?.let { file(it) } ?: preservedBetaStore
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module",
        )
        jniLibs.useLegacyPackaging = true // yt-dlp/python natives must be extractable
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.muxer)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)

    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coil.gif)
    implementation("com.google.errorprone:error_prone_annotations:2.30.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
