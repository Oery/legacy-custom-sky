pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
	}
}

plugins {
	// Check the latest version at https://plugins.gradle.org/plugin/dev.kikugie.stonecutter
	id("dev.kikugie.stonecutter") version "0.9.7"

	// Selects the correct Loom variant per node - legacy/obfuscated Loom for
	// 1.21.11, modern/unobfuscated Loom for 26.1+ - from one shared build script.
	// https://codeberg.org/KikuGie/loom-back-compat
	id("dev.kikugie.loom-back-compat") version "0.4.2"

	// Auto-provisions JDK 21 for the 1.21.11 node's toolchain even when the host
	// JDK is 25 (used to build 26.1/26.2). https://github.com/gradle/foojay-toolchains
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
	create(rootProject) {
		versions("1.21.11", "26.1", "26.2")
		vcsVersion = "26.2"
	}
}

// Should match your modid
rootProject.name = "legacy-custom-sky"
