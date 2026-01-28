plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.1"
    id("app.ultradev.hytalegradle") version "2.0.1"
}

group = "tokebak"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

hytale {
    serverJar.set(file("libs/HytaleServer.jar"))
    assetsZip.set(file("libs/Assets.zip"))
    // Add `--allow-op` to server args (allows you to run `/op self` in-game)
    allowOp.set(true)

    // Set the patchline to use, currently there are "release" and "pre-release"
    patchline.set("pre-release")

    // Load mods from the local Hytale installation
    includeLocalMods.set(true)

    // Replace the version in the manifest with the project version
    manifest {
        version.set(project.version.toString())
    }
}
