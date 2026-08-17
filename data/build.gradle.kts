plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.rocat.data"
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
    implementation(projects.domain)
    implementation(projects.scripting.api)
    implementation(projects.scripting.rhino)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.serialization)
    implementation(libs.logcat)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
}