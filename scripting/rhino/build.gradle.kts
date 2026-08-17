plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.rocat.scripting.rhino"
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
    implementation(projects.scripting.api)
    implementation(projects.core.common)
    implementation(libs.rhino)
    implementation(libs.jsoup)
    implementation(libs.bundles.coroutines)
    implementation(libs.bundles.serialization)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}