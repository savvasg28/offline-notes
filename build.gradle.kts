import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt)
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        parallel = true
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml").takeIf { it.exists() }
    }

    dependencies {
        add("detektPlugins", rootProject.libs.detekt.formatting)
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        autoCorrect = providers.gradleProperty("detektAutoCorrect").isPresent
        reports {
            html.required.set(true)
            sarif.required.set(false)
            txt.required.set(false)
            md.required.set(false)
        }
    }
}

tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt with type resolution on every module's production sources."
    dependsOn(subprojects.map { sp -> sp.tasks.matching { it.name == "detektMain" || it.name == "detektDebug" } })
}
