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

android {
    namespace = "com.savvasg.offlinenotes.core.database"
    defaultConfig {
        testInstrumentationRunner = "com.savvasg.offlinenotes.core.database.HiltTestRunner"
    }
}

dependencies {
    implementation(project(":core:model"))

    // api, not implementation: OfflineNotesDatabase extends RoomDatabase, and it's part of this
    // module's public surface (SyncEngineTest builds one directly) — consumers need RoomDatabase
    // resolvable on their own compile classpath, which only `api` provides.
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
