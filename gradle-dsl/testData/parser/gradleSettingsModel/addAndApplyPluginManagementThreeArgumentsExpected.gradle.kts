pluginManagement {
  plugins {
    id("com.android.application") version "7.0.0" apply false
  }
  repositories {
    google()
  }
}
dependencyResolutionManagement {
  repositories {
    jcenter()
  }
}
rootProject.name = "My Application"
include(":app")
