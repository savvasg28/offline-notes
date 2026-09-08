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

include(":core:model")
include(":core:database")
include(":core:network")
include(":core:data")
include(":core:domain")
include(":core:designsystem")
include(":core:testing")

include(":sync:engine")
include(":sync:work")

include(":feature:notelist")
include(":feature:editor")
include(":feature:conflict")
