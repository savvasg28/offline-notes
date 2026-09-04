plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

// Hardcoded, not catalog refs: buildSrc's own build script can't see the root project's version
// catalog (`libs`) — it's a regular script, not a precompiled one. Keep these in sync with `agp` /
// `kotlin` in gradle/libs.versions.toml. Both are required: the precompiled scripts under
// src/main/kotlin request these plugin IDs unversioned, and Gradle resolves an unversioned
// precompiled-script plugin request only against a dependency declared right here.
dependencies {
    implementation("com.android.tools.build:gradle:8.7.3") // agp
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0") // kotlin
}

// AGP transitively depends on javapoet 1.10.0, which lacks a method Dagger/Hilt's annotation
// processor calls on newer javapoet — without this, that old version wins dependency resolution
// and every module's hiltAggregateDepsDebug fails with NoSuchMethodError.
configurations.all {
    resolutionStrategy {
        force("com.squareup:javapoet:1.13.0")
    }
}

