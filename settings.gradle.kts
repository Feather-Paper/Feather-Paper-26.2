import java.util.Locale

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

if (!file(".git").exists()) {
    // Leaf start - project setup
    val errorText = """
        
        =====================[ ERROR ]=====================
         The Feather project directory is not a properly cloned Git repository.
         
         In order to build Feather from source you must clone
         the Feather repository using Git, not download a code
         zip from GitHub.
         
         See https://github.com/PaperMC/Paper/blob/main/CONTRIBUTING.md
         for further information on building and modifying Paper forks.
        ===================================================
    """.trimIndent()
    // Leaf end - project setup
    error(errorText)
}

rootProject.name = "feather"

for (name in listOf("feather-api", "feather-server")) {
    val projName = name.lowercase(Locale.ENGLISH)
    include(projName)
}

gradle.lifecycle.beforeProject {
    val mcVersion = providers.gradleProperty("mcVersion").get().trim()
    val paperVersionChannel = providers.gradleProperty("channel").get().trim()
    val paperBuildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.trim()?.toInt()
    val versionString = if (paperBuildNumber == null) {
        "$mcVersion.local-SNAPSHOT"
    } else {
        "$mcVersion.build.$paperBuildNumber-${paperVersionChannel.lowercase()}"
    }
    version = versionString
}
