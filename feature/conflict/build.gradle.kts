plugins {
    id("offlinenotes.android.library")
    alias(libs.plugins.ksp)
    id("offlinenotes.android.hilt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
}

android {
    namespace = "com.savvasg.offlinenotes.feature.conflict"

    buildFeatures {
        compose = true
    }
}

dependencies {
    lintChecks(libs.compose.lint.checks)

    implementation(project(":domain"))
    implementation(project(":core:designsystem"))

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
