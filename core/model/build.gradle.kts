plugins {
    id("offlinenotes.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
