pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OfflineNotes"

include(":app")

include(":domain")

include(":data")
include(":data:database")
include(":data:network")
include(":data:sync")

include(":core:designsystem")
include(":core:testing")

include(":feature:notelist")
include(":feature:editor")
include(":feature:conflict")
