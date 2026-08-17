plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.rocat.domain"
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
    api(projects.core.common)
    api(libs.bundles.serialization)
    api(projects.scripting.api)

    testImplementation(libs.junit)
}