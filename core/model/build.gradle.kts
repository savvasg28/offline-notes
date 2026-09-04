plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDir("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
