plugins {
    id("offlinenotes.android.library")
    alias(libs.plugins.ksp)
    id("offlinenotes.android.hilt")
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.savvasg.offlinenotes.core.data"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
