plugins {
    id("offlinenotes.android.library")
    alias(libs.plugins.ksp)
    id("offlinenotes.android.hilt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Belt and braces: the androidx.room plugin's schemaDirectory stopped reaching KSP when this
// module moved onto the shared convention plugin, silently disabling schema export. Passing the
// processor argument directly is the documented fallback and is what actually writes schemas/.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.savvasg.offlinenotes.data.database"
    defaultConfig {
        testInstrumentationRunner = "com.savvasg.offlinenotes.data.database.HiltTestRunner"
    }
}

dependencies {
    implementation(project(":domain"))

    // api, not implementation: OfflineNotesDatabase extends RoomDatabase and is built directly by
    // :data's integration test, which needs RoomDatabase on its own compile classpath.
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite.bundled)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
