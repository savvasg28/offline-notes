plugins {
    id("offlinenotes.android.application")
    alias(libs.plugins.ksp)
    id("offlinenotes.android.hilt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
}

android {
    namespace = "com.savvasg.offlinenotes"

    defaultConfig {
        applicationId = "com.savvasg.offlinenotes"
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    lintChecks(libs.compose.lint.checks)

    implementation(project(":domain"))
    implementation(project(":data:database"))
    implementation(project(":data:network"))
    implementation(project(":data"))
    implementation(project(":core:designsystem"))
    implementation(project(":sync"))
    implementation(project(":feature:notelist"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:conflict"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.work)

    testImplementation(libs.junit)
    testImplementation(libs.konsist)
}
