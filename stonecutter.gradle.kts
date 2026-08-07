plugins {
	id("dev.kikugie.stonecutter")
}

stonecutter active "26.2" /* [SC] DO NOT EDIT */

// See https://stonecutter.kikugie.dev/wiki/config/params
// No replacements{} block - no ResourceLocation-> Identifier-style rename spans
// this mod's 1.21.11..26.2 range (verified during planning).
stonecutter parameters {
	swaps["mod_version"] = "\"${property("mod.version")}\";"
	swaps["minecraft"] = "\"${node.metadata.version}\";"
	swaps["fabric_api"] = "\"${node.project.property("deps.fabric_api") as String}\";"
	swaps["modmenu"] = "\"${node.project.property("deps.modmenu") as String}\";"
}
