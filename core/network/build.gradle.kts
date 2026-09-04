plugins {
    id("offlinenotes.android.library")
    alias(libs.plugins.ksp)
    id("offlinenotes.android.hilt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.savvasg.offlinenotes.core.network"

    buildTypes {
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.offline-notes.example/\"")
        }
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.okhttp.mockwebserver)
}
