plugins {
    id("offlinenotes.android.library")
}

android {
    namespace = "com.savvasg.offlinenotes.core.testing"
}

dependencies {
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
}
