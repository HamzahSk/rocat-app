plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.rocat.core.common"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    buildTypes {
        create("preview") {
            initWith(getByName("release"))
        }
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
    api(libs.bundles.okhttp)
    api(libs.bundles.serialization)
    api(libs.bundles.coroutines)
    implementation(libs.jsoup)
    implementation(libs.logcat)
    implementation(libs.injekt)
}