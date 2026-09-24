plugins {
    id("offlinenotes.android.library")
    alias(libs.plugins.kotlin.compose)
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
}

android {
    namespace = "com.savvasg.offlinenotes.core.designsystem"

    buildFeatures {
        compose = true
    }

    lint {
        disable += "ComposeCompositionLocalUsage"
    }
}

dependencies {
    lintChecks(libs.compose.lint.checks)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
