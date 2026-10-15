package com.siberanka.twilight.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public final class WorldLayout {
    private WorldLayout() {}

    public static Set<Path> discover(Path serverRoot, Set<Path> runtimeWorlds) throws IOException {
        Path root = serverRoot.toAbsolutePath().normalize();
        Properties properties = new Properties();
        Path serverProperties = root.resolve("server.properties");
        if (Files.isRegularFile(serverProperties)) {
            try (InputStream input = Files.newInputStream(serverProperties)) {
                properties.load(input);
            }
        }
        String levelName = properties.getProperty("level-name", "world").trim();
        if (levelName.isEmpty()) levelName = "world";

        Set<Path> worlds = new LinkedHashSet<>();
        addWithinRoot(worlds, root, root.resolve(levelName));
        addWithinRoot(worlds, root, root.resolve(levelName + "_nether"));
        addWithinRoot(worlds, root, root.resolve(levelName + "_the_end"));
        for (Path runtimeWorld : runtimeWorlds) addWithinRoot(worlds, root, runtimeWorld);
        return worlds;
    }

    private static void addWithinRoot(Set<Path> worlds, Path root, Path candidate) throws IOException {
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new IOException("World path escapes server root: " + candidate);
        if (Files.isDirectory(normalized) && !Files.isSymbolicLink(normalized)) worlds.add(normalized);
    }
}
