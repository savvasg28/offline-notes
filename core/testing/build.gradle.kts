plugins {
    id("offlinenotes.android.library")
}

android {
    namespace = "com.savvasg.offlinenotes.core.testing"
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlinx.coroutines.core)
}
