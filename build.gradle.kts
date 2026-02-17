/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import io.github.slimjar.func.slimjarHelper
import xyz.jpenilla.runpaper.task.RunServer
import kotlin.system.exitProcess

plugins {
    `java-library`
    alias(libs.plugins.lombok)
    alias(libs.plugins.shadow)
    alias(libs.plugins.slimjar)
    alias(libs.plugins.runPaper)
}

group = "art.arcane"
version = "1.0.0-1.17.1-1.21.10"
val apiVersion = "1.17"
val main = "art.arcane.holoui.HoloUI"
val lib = "art.arcane.holoui.libs"
val volmLibCoordinate: String = providers.gradleProperty("volmLibCoordinate")
    .orElse("com.github.VolmitSoftware:VolmLib:master-SNAPSHOT")
    .get()

// ADD YOURSELF AS A NEW LINE IF YOU WANT YOUR OWN BUILD TASK GENERATED
// ======================== WINDOWS =============================
registerCustomOutputTask("Cyberpwn", "C://Users/cyberpwn/Documents/development/server/plugins")
registerCustomOutputTask("Psycho", "C://Dan/MinecraftDevelopment/Server/plugins")
registerCustomOutputTask("ArcaneArts", "C://Users/arcane/Documents/development/server/plugins")
registerCustomOutputTask("Vatuu", "D://Minecraft/Servers/1.20/plugins")
registerCustomOutputTask("Nowhere", "E://Desktop/server/plugins")
registerCustomOutputTask("CrazyDev22", "C://Users/Julian/Desktop/server/plugins")
registerCustomOutputTask("Pixel", "D://Iris Dimension Engine//1.20.4 - Development//plugins")
// ========================== UNIX ==============================
registerCustomOutputTaskUnix("CyberpwnLT", "/Users/danielmills/development/server/plugins")
registerCustomOutputTaskUnix("PsychoLT", "/Users/brianfopiano/Developer/RemoteGit/[Minecraft Server]/plugin-jars")
registerCustomOutputTaskUnix("the456gamer", "/home/the456gamer/projects/minecraft/adapt-testserver/plugins/update/", false)
// ==============================================================

tasks {
    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    shadowJar {
        archiveClassifier = null
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        minimize()

        relocate("io.github.slimjar", "${lib}.slimjar")
        relocate("com.github.retrooper.packetevents", "${lib}.packetevents.api")
        relocate("io.github.retrooper.packetevents", "${lib}.packetevents.impl")
        relocate("org.bstats", "${lib}.bstats")
    }

    processResources {
        inputs.properties(
            "name" to rootProject.name,
            "version" to version,
            "main" to main,
            "apiVersion" to apiVersion,
        )

        filesMatching("**/plugin.yml") {
            expand(inputs.properties)
        }
    }
}

slimJar {
    relocate("org.apache.commons", "${lib}.commons")
    relocate("com.github.zafarkhaja.semver", "${lib}.semver")
    relocate("io.undertow", "${lib}.undertow")
    relocate("org.jboss", "${lib}.jboss")
    relocate("org.xnio", "${lib}.xnio")
    relocate("org.wildfly", "${lib}.wildfly")
    relocate("net.kyori", "${lib}.kyori")
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://jitpack.io")
}

dependencies {
    implementation(slimjarHelper("spigot"))
    implementation(volmLibCoordinate) {
        isChanging = true
        isTransitive = false
    }

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spigot)
    compileOnly(libs.placeholderApi)

    // Shaded
    implementation(libs.packetevents) {
        exclude(group = "net.kyori")
    }
    implementation("org.bstats:bstats-bukkit:3.1.0")

    // Dynamically loaded
    slim(libs.adventure.minimessage)
    slim(libs.adventure.nbt)
    slim(libs.undertow)
    slim(libs.semver)
    slim(libs.commons.io)
    slim(libs.commons.imaging)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    System.err.println()
    System.err.println("=========================================================================================================")
    System.err.println("You must run gradle on Java 21 or newer. You are using " + JavaVersion.current())
    System.err.println()
    System.err.println("=== For IDEs ===")
    System.err.println("1. Configure the project for Java 21")
    System.err.println("2. Configure the bundled gradle to use Java 21 in settings")
    System.err.println()
    System.err.println("=== For Command Line (gradlew) ===")
    System.err.println("1. Install JDK 21 from https://www.oracle.com/java/technologies/downloads/#java21")
    System.err.println("2. Set JAVA_HOME environment variable to the new jdk installation folder")
    System.err.println("3. Open a new command prompt window to get the new environment variables if need be.")
    System.err.println("=========================================================================================================")
    System.err.println()
    exitProcess(69)
}

// IDE Server stuff
fun registerCustomOutputTask(name: String, path: String, doRename: Boolean = true) {
    if (!System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }

    createOutputTask(name, path, doRename)
}

fun registerCustomOutputTaskUnix(name: String, path: String, doRename: Boolean = true) {
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }

    createOutputTask(name, path, doRename)
}

fun createOutputTask(name: String, path: String, doRename: Boolean = true) {
    tasks.register<Copy>("build$name") {
        group = "development"
        outputs.upToDateWhen { false }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(tasks.shadowJar)
        from(tasks.shadowJar.flatMap { it.archiveFile })
        into(file(path))
        if (doRename) rename { "HoloUi.jar" }
    }
}

val versions = listOf("1.17.1", "1.18.1", "1.18.2", "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20.1", "1.20.2", "1.20.4", "1.20.6", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8", "1.21.10")

versions.forEach { version ->
    tasks.register<RunServer>("runServer-$version") {
        group = "servers"
        minecraftVersion(version)
        minHeapSize = "2G"
        maxHeapSize = "8G"
        pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
        downloadPlugins.url("https://ci.extendedclip.com/job/PlaceholderAPI/221/artifact/build/libs/PlaceholderAPI-2.11.8-DEV-221.jar")
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    }
}

tasks.register("runServers") {
    group = "servers"
    dependsOn("build")
    doLast {
        delete("run/world")
        delete("run/world_nether")
        delete("run/world_the_end")

        versions.forEach { version ->
            tasks.named<RunServer>("runServer-$version").get().exec()
        }
    }
}
