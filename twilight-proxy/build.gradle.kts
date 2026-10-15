plugins {
    java
}

group = providers.gradleProperty("maven_group").get()
version = providers.gradleProperty("twilight_version").get()

base {
    archivesName = "TwilightProxy"
}

dependencies {
    // Compiled against current APIs; only long-standing events and methods are used.
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    compileOnly("net.md-5:bungeecord-api:1.21-R0.1")
    compileOnly("org.geysermc.geyser:api:2.11.2-SNAPSHOT")
    // Twilight's text layout and loading protection run on the proxy's Geyser through its core classes,
    // like on a backend's own Geyser. Pinned to the reviewed core build; never bundled.
    compileOnly("org.geysermc.geyser:core:2.11.3-20260925.135253-13") {
        // The proxies bring their own Adventure; the bridges reach Geyser's copy by name.
        exclude(group = "net.kyori")
    }

    testImplementation(platform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // The proxies provide Gson at runtime (item mappings).
    testImplementation("com.google.code.gson:gson:2.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

// The plugin-message protocol shared with Twilight on the backends.
sourceSets.main {
    java.srcDir(rootProject.file("protocol/src/main/java"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Geyser, which twilight-proxy serves, requires Java 21.
    options.release = 21
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    val properties = mapOf("version" to project.version.toString())
    inputs.properties(properties)
    filesMatching(listOf("velocity-plugin.json", "bungee.yml")) {
        expand(properties)
    }
}

tasks.jar {
    archiveFileName = "TwilightProxy.jar"
    from(rootProject.file("LICENSE")) { rename { "LICENSE_Twilight" } }
    from(rootProject.file("NOTICE")) { rename { "NOTICE_Twilight" } }
    manifest.attributes(
        "Implementation-Title" to "twilight-proxy",
        "Implementation-Version" to project.version,
        "Implementation-Vendor" to "siberanka"
    )
}

tasks.test {
    useJUnitPlatform()
}
