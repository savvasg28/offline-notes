plugins {
    id("offlinenotes.kotlin.jvm")
}

dependencies {
    api(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
}
