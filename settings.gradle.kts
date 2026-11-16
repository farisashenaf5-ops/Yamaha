pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Faris PSR-S975"

include(":app")
include(":audio-engine")
include(":soundfont-engine")
include(":voice-engine")
include(":style-engine")
include(":midi-engine")
include(":chord-engine")
include(":effects-engine")
include(":mixer-engine")
include(":recording-engine")
include(":sample-manager")
include(":expansion-manager")
include(":ui-components")
include(":data-storage")
