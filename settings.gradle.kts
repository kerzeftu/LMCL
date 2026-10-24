rootProject.name = "HMCL3"
include(
    "HMCL",
    "HMCLCore",
    "HMCLBoot",
    "m3fx"
)

val minecraftLibraries = listOf("HMCLTransformerDiscoveryService", "HMCLMultiMCBootstrap")
include(minecraftLibraries)

for (library in minecraftLibraries) {
    project(":$library").projectDir = file("minecraft/libraries/$library")
}
