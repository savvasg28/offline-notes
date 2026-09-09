plugins {
    id("offlinenotes.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.savvasg.offlinenotes.core.designsystem"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
