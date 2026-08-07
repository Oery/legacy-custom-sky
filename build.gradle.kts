import org.gradle.language.jvm.tasks.ProcessResources

plugins {
	// Applies the correct Loom variant (obfuscated vs unobfuscated) per node
	// instead of net.fabricmc.fabric-loom directly.
	id("dev.kikugie.loom-back-compat")
	id("maven-publish")
}

// DO NOT set group here - Stonecutter/loom-back-compat manage per-node project
// identity themselves (see the upstream stonecutter-template-fabric, which
// carries the same warning). mod.group in stonecutter.properties.toml is kept
// for documentation/consistency, not consumed here.
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName.set(property("mod.id") as String)


// sc.current.parsed's operator-overloaded version comparison is why this
// project uses the Kotlin DSL instead of Groovy.
val requiredJava: JavaVersion = when {
	sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
	else -> JavaVersion.VERSION_21
}

// Resolved eagerly (Project receiver) and captured by the lazy task
// configuration blocks below - a bare property(...)/sc.properties[...] call
// from *inside* a tasks.named { } lambda resolves against the Task receiver
// (Task also has its own unrelated property() method), not the Project, and
// silently looks up the wrong thing.
val modId = property("mod.id") as String
val modName = property("mod.name") as String
val modVersionProp = property("mod.version") as String
val mcCompat = sc.properties.get<String>("mod.mc_compat")

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
	maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" }
}

loom {
	splitEnvironmentSourceSets()

	mods {
		create("legacy-custom-sky") {
			sourceSet(sourceSets["main"])
			sourceSet(sourceSets["client"])
		}
	}
}

dependencies {
	// To change the versions see stonecutter.properties.toml
	minecraft("com.mojang:minecraft:${sc.current.version}")
	// Applies Mojang mappings on the obfuscated 1.21.11 node; a no-op on the
	// unobfuscated 26.1/26.2 nodes.
	loomx.applyMojangMappings()

	modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")

	// Fabric API. Kept as a single artifact (not the per-module
	// fabricApi.module(...) slicing the upstream template uses) - matches this
	// mod's existing, already-working single-artifact dependency.
	modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

	// Mod Menu integration is soft/optional (registered via the "modmenu" custom
	// entrypoint in fabric.mod.json, only ever loaded by Mod Menu itself if it's
	// present). clientCompileOnly (not mod-prefixed): this Loom version has no
	// modImplementation/modCompileOnly sugar for the client source set (mod-jar
	// remapping is auto-detected per dependency now, not per configuration), so
	// this compiles against Mod Menu's API without requiring or bundling it at
	// runtime. Confirmed to still resolve under loom-back-compat for all three
	// nodes (see Risk V2 in the plan) - a mod-prefixed "modClientCompileOnly"
	// configuration doesn't even exist here. clientCompileOnly is created
	// dynamically by splitEnvironmentSourceSets(), so it needs the string-invoke
	// form rather than a generated Kotlin accessor.
	"clientCompileOnly"("com.terraformersmc:modmenu:${sc.properties.get<String>("deps.modmenu")}")
}

tasks.named<ProcessResources>("processResources") {
	val expandProps = mapOf(
		"id" to modId,
		"name" to modName,
		"version" to project.version,
		"minecraft" to mcCompat,
		"javaVersion" to requiredJava.majorVersion
	)
	inputs.properties(expandProps)

	filesMatching("fabric.mod.json") {
		expand(expandProps)
	}
}

// legacy-custom-sky.client.mixins.json lives in the *client* source set
// (src/client/resources), which splitEnvironmentSourceSets() processes via its
// own processClientResources task, not processResources - the mixin
// compatibilityLevel placeholder silently stayed as the literal "${java}" in
// the built jar until this was split out.
tasks.named<ProcessResources>("processClientResources") {
	val mixinJava = "JAVA_${requiredJava.majorVersion}"
	inputs.property("mixinJava", mixinJava)
	filesMatching("*.mixins.json") {
		expand("java" to mixinJava)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set(requiredJava.majorVersion.toInt())
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = requiredJava
	targetCompatibility = requiredJava

	toolchain {
		languageVersion.set(JavaLanguageVersion.of(requiredJava.majorVersion))
	}
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// configure the maven publication
publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}

// Builds this node's mod jar (+ sources) and copies it into a shared
// build/libs/<mod.version>/ folder. Since this script is the build file for
// every Stonecutter version subproject, running the bare `buildAndCollect`
// task name from the root runs it in all of them, producing all three jars in
// one command.
tasks.register<Copy>("buildAndCollect") {
	group = "build"
	description = "Builds mod jars and copies results to build/libs/<mod.version>/"

	inputs.property("version", modVersionProp)
	from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
	into(rootProject.layout.buildDirectory.dir("libs/$modVersionProp"))
}
