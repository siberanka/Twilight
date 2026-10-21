plugins {
    java
}

group = providers.gradleProperty("maven_group").get()
val twilightVersion = providers.gradleProperty("twilight_version").get()
version = twilightVersion

base {
    archivesName = "Twilight"
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.geysermc.geyser:api:2.11.2-SNAPSHOT")
    // Item-display translation is not exposed by Geyser's public API yet.
    // Pin the adapter to a reviewed core build; never bundle Geyser classes.
    compileOnly("org.geysermc.geyser:core:2.11.3-20260925.135253-13")
    // Paper test types need their runtime dependencies for executable Mockito/Bukkit tests.
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Preserve upstream YAML and Guava dependencies, plus the local Mockito tests.
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation("com.google.guava:guava:33.3.1-jre") { isTransitive = false }
    testImplementation("org.mockito:mockito-core:5.24.0")
    // Title layout tests drive the reflective Adventure bridge with unrelocated components.
    testImplementation("net.kyori:adventure-api:4.17.0")
    // Text surface tests rewrite real protocol packets.
    testImplementation("org.geysermc.mcprotocollib:protocol:26.2-20260824.124638-17") { isTransitive = false }
    testImplementation("io.netty:netty-buffer:4.2.7.Final")
    testImplementation("org.cloudburstmc:nbt:3.0.5.Final")
    // The Bedrock network layer is tested in a real Netty pipeline with Geyser's Bedrock packets.
    for (module in listOf("bedrock-connection", "bedrock-codec", "common")) {
        testImplementation("org.cloudburstmc.protocol:$module:3.0.0.Beta13-20260917.001841-27") { isTransitive = false }
    }
    testImplementation("org.cloudburstmc.math:api:2.0") { isTransitive = false }
    testImplementation("org.cloudburstmc.math:immutable:2.0") { isTransitive = false }
    testImplementation("it.unimi.dsi:fastutil:8.5.15")
    testImplementation("io.netty:netty-transport:4.2.7.Final")

    testImplementation(platform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.11.0")
    // Run public-API-only Elytra bridge regression tests without bundling Geyser in Twilight.jar.
    testImplementation("org.geysermc.geyser:api:2.11.2-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

// The plugin-message protocol shared with twilight-proxy.
sourceSets.main {
    java.srcDir(rootProject.file("protocol/src/main/java"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    val pluginProperties = mapOf("version" to providers.gradleProperty("twilight_version").get())
    inputs.properties(pluginProperties)
    filesMatching("plugin.yml") {
        expand(pluginProperties)
    }
}

tasks.jar {
    archiveFileName = "Twilight.jar"
    manifest.attributes(
        "Implementation-Title" to "Twilight",
        "Implementation-Version" to project.version,
        "Implementation-Vendor" to "siberanka",
        "Implementation-URL" to "https://github.com/siberanka/Twilight",
        "Bundle-License" to "LicenseRef-Twilight-License; link=META-INF/LICENSE"
    )
}

// Every JAR (plugin and sources) carries the license and the required notice where tools look for them.
tasks.withType<Jar>().configureEach {
    into("META-INF") {
        from(rootProject.file("LICENSE"))
        from(rootProject.file("NOTICE"))
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("auditServerSources") {
    group = "verification"
    description = "Audits server content sources read-only and writes a machine-readable report."
    dependsOn(tasks.testClasses)
    mainClass = "com.siberanka.twilight.compiler.SourceAuditMain"
    classpath = sourceSets.test.get().runtimeClasspath
    val output = providers.gradleProperty("twilight.audit.output")
        .orElse(layout.buildDirectory.file("reports/twilight-source-audit.json").map { it.asFile.absolutePath })
    val roots = providers.gradleProperty("twilight.audit.roots")
    doFirst {
        val parsed = roots.orNull?.split(',')?.filter { it.isNotBlank() }
            ?: throw GradleException("Pass -Ptwilight.audit.roots=<server-root>,<server-root>")
        args = listOf(output.get()) + parsed
    }
}

tasks.register<JavaExec>("auditServerFonts") {
    group = "verification"
    description = "Audits only bitmap-font conversion from server sources read-only."
    dependsOn(tasks.testClasses)
    mainClass = "com.siberanka.twilight.compiler.FontAuditMain"
    classpath = sourceSets.test.get().runtimeClasspath
    val output = providers.gradleProperty("twilight.fontAudit.output")
        .orElse(layout.buildDirectory.file("reports/twilight-font-audit.json").map { it.asFile.absolutePath })
    val roots = providers.gradleProperty("twilight.audit.roots")
    providers.gradleProperty("twilight.audit.minecraft-version").orNull?.let {
        systemProperty("twilight.audit.minecraftVersion", it)
    }
    providers.gradleProperty("twilight.audit.cache").orNull?.let { systemProperty("twilight.audit.cache", it) }
    providers.gradleProperty("twilight.audit.text-layout").orNull?.let { systemProperty("twilight.audit.textLayout", it) }
    doFirst {
        val parsed = roots.orNull?.split(',')?.filter { it.isNotBlank() }
            ?: throw GradleException("Pass -Ptwilight.audit.roots=<server-root>,<server-root>")
        args = listOf(output.get()) + parsed
    }
}

tasks.register<JavaExec>("auditServerBuilds") {
    group = "verification"
    description = "Runs the complete Bedrock pack build for server sources read-only and reports every problem."
    dependsOn(tasks.testClasses)
    mainClass = "com.siberanka.twilight.compiler.ServerBuildAuditMain"
    classpath = sourceSets.test.get().runtimeClasspath
    val output = providers.gradleProperty("twilight.buildAudit.output")
        .orElse(layout.buildDirectory.file("reports/twilight-build-audit.json").map { it.asFile.absolutePath })
    val data = providers.gradleProperty("twilight.buildAudit.data")
        .orElse(layout.buildDirectory.dir("tmp/build-audit").map { it.asFile.absolutePath })
    val roots = providers.gradleProperty("twilight.audit.roots")
    providers.gradleProperty("twilight.audit.minecraft-version").orNull?.let {
        systemProperty("twilight.audit.minecraftVersion", it)
    }
    providers.gradleProperty("twilight.audit.item-displays").orNull?.let { systemProperty("twilight.audit.itemDisplays", it) }
    providers.gradleProperty("twilight.audit.max-glyph-cell").orNull?.let { systemProperty("twilight.audit.maxGlyphCell", it) }
    providers.gradleProperty("twilight.audit.displayed-items").orNull?.let { systemProperty("twilight.audit.displayedItems", it) }
    doFirst {
        val parsed = roots.orNull?.split(',')?.filter { it.isNotBlank() }
            ?: throw GradleException("Pass -Ptwilight.audit.roots=<server-root>,<server-root>")
        args = listOf(output.get(), data.get()) + parsed
    }
}

tasks.register<JavaExec>("auditServerSounds") {
    group = "verification"
    description = "Audits only custom-sound conversion from server sources read-only."
    dependsOn(tasks.testClasses)
    mainClass = "com.siberanka.twilight.compiler.SoundAuditMain"
    classpath = sourceSets.test.get().runtimeClasspath
    val output = providers.gradleProperty("twilight.soundAudit.output")
        .orElse(layout.buildDirectory.file("reports/twilight-sound-audit.json").map { it.asFile.absolutePath })
    val roots = providers.gradleProperty("twilight.audit.roots")
    providers.gradleProperty("twilight.audit.minecraft-version").orNull?.let {
        systemProperty("twilight.audit.minecraftVersion", it)
    }
    doFirst {
        val parsed = roots.orNull?.split(',')?.filter { it.isNotBlank() }
            ?: throw GradleException("Pass -Ptwilight.audit.roots=<server-root>,<server-root>")
        args = listOf(output.get()) + parsed
    }
}
