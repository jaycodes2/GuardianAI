pluginManagement {
    repositories {
        // Correctly includes all plugins from Google's repository
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Correctly includes all dependencies from Google's repository
        google()
        mavenCentral()
    }
}

rootProject.name = "GuardianAI"
include(":app")
include(":image-redaction")
include(":audio-redaction")
include(":text-redaction")