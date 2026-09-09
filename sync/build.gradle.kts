plugins {
    id("offlinenotes.android.library")
    alias(libs.plugins.ksp)
    id("offlinenotes.android.hilt")
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.savvasg.offlinenotes.sync"
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.work.testing)
}
