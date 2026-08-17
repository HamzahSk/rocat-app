import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Tahap 14.1: Signing & keystore (opsional). Baca `keystore.properties` dari
// root proyek rocat-app/. Jika file atau keystore tidak ada, build tetap jalan
// (release memakai signingConfig debug sebagai fallback, tidak pernah throw).
val keystoreProperties = Properties().apply {
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        keystoreFile.inputStream().use { load(it) }
    }
}
val releaseStoreFile: java.io.File? = keystoreProperties.getProperty("storeFile")?.let {
    rootProject.file(it)
}
val hasReleaseSigning: Boolean = releaseStoreFile != null && releaseStoreFile.exists()

android {
    namespace = "app.rocat"
    compileSdk = 35

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseStoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "app.rocat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Tahap 14.3: Build types
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
        }
    }

    // Tahap 14.2: ABI splits (APK per-arsitektur + universal)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
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
        viewBinding = true
    }
}

// Tahap 14.4: Firebase (opsional & aman). Plugin hanya di-apply bila
// `app/google-services.json` benar-benar ada, agar build tidak pernah gagal
// hanya karena Firebase belum disiapkan.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.viewmodel)
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.scripting.api)
    implementation(projects.scripting.rhino)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material.icons)

    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.serialization)
    implementation(libs.logcat)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Tahap 18.3: AndroidX Media3 (ExoPlayer + HLS streaming + PlayerView UI).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    // Tahap 15.3: Room database (KSP annotation processing)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    if (file("google-services.json").exists()) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.analytics)
        implementation(libs.firebase.crashlytics)
    }

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
