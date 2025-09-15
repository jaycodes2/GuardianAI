pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://alphacephei.com/maven/") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GuardianAI"
include(":app")
include(":core")
include(":text-redaction")
include(":audio-redaction")
include(":image-redaction")
include(":ner")
