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

plugins {
    java
    alias(libs.plugins.lombok)
    alias(libs.plugins.shadow)
    alias(libs.plugins.slimjar)
    alias(libs.plugins.runPaper)
}

// ADD YOURSELF AS A NEW LINE IF YOU WANT YOUR OWN BUILD TASK GENERATED
// ======================== WINDOWS =============================
registerCustomOutputTask("CrazyDev22", "C://Users/Julian/Desktop/server/plugins")
// ========================== UNIX ==============================
registerCustomOutputTaskUnix("CrazyDev22", "/home/julian/Desktop/server/plugins")
// ==============================================================

group = "com.volmit"
version = "1.0.0-1.17.1-1.21.8"

val pluginName = "HoloUI"
val main = "com.volmit.holoui.HoloUI"
val lib = "com.volmit.holoui.libs"
val apiVersion = 1.17

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    build {
        dependsOn(shadowJar)
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier = null
        minimize()

        relocate("io.github.slimjar", "${lib}.slimjar")
        relocate("com.github.retrooper.packetevents", "${lib}.packetevents.api")
        relocate("io.github.retrooper.packetevents", "${lib}.packetevents.impl")
        relocate("co.aikar.commands", "${lib}.acf")
        relocate("co.aikar.locales", "${lib}.locales")
        relocate("org.bstats", "${lib}.bstats")
    }

    processResources {
        inputs.properties(
            "name" to pluginName,
            "version" to version,
            "main" to main,
            "apiVersion" to apiVersion,
        )

        filesMatching("**/plugin.yml") {
            expand(inputs.properties)
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

slimJar {
    relocate("org.apace.commons", "${lib}.commons")
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
    maven("https://repo.extendedclip.com/releases/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.aikar.co/content/groups/aikar/")
}

dependencies {
    implementation(slimjarHelper("spigot"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spigot)
    compileOnly(libs.placeholderApi)

    //Shaded
    implementation(libs.packetevents) {
        exclude(group = "net.kyori")
    }
    implementation(libs.acf)
    implementation("org.bstats:bstats-bukkit:3.1.0")

    //Dynamically Loaded
    slim(libs.adventure.minimessage)
    slim(libs.adventure.nbt)
    slim(libs.undertow)
    slim(libs.semver)
    slim(libs.commons.io)
    slim(libs.commons.imaging)
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
        dependsOn("shadowJar")
        from(tasks.named<Copy>("shadowJar").map { outputs.files.singleFile })
        into(file(path))
        if (doRename) rename { "Adapt.jar" }
    }
}

val versions = listOf("1.17.1", "1.18.1", "1.18.2", "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20.1", "1.20.2", "1.20.4", "1.20.6", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8")
val jdk = listOf("1.20.6", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8")

versions.forEach {version ->
    tasks.register<RunServer>("runServer-$version") {
        group = "servers"
        minecraftVersion(version)
        minHeapSize = "2G"
        maxHeapSize = "8G"
        pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
        downloadPlugins.url("https://ci.extendedclip.com/job/PlaceholderAPI/200/artifact/build/libs/PlaceholderAPI-2.11.7-DEV-200.jar")
        if (jdk.contains(version)) {
            javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21)}
        }
    }
}

tasks.register("runServers") {
    group = "servers"
    dependsOn("build")
    doLast {
        delete("run/world")
        delete("run/world_nether")
        delete("run/world_the_end")


        versions.forEach {version ->
            tasks.named<RunServer>("runServer-$version").get().exec()
        }
    }
}