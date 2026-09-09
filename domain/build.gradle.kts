plugins {
    id("offlinenotes.kotlin.jvm")
}

// No Android, no Hilt, no serialization — deliberately. This module is the app's business logic,
// and every one of those would be a framework it has no reason to know about. `javax.inject` is
// the one exception: it is a 2KB annotations-only spec jar, and Dagger generates the factories
// for these @Inject constructors in the modules that actually build a component.
dependencies {
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
