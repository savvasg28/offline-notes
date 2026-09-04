plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
    sourceSets.getByName("main") { kotlin.srcDir("src/main/kotlin") }
}
