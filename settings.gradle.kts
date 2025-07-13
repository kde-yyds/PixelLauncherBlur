pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url =  uri("https://api.xposed.info/") }
    maven { url =  uri("https://jitpack.io") }
  }
}

rootProject.name = "Free Share"

include(":app")