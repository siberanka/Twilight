/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.integration.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.siberanka.twilight.integration.display.DisplayIndex;
import com.siberanka.twilight.integration.text.GeyserTextLayoutBridge;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.option.ResourcePackOption;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;

/**
 * Sends each Bedrock player the Twilight pack deployed when it joins, without Geyser reloading (which disconnects
 * every Bedrock player) or the server restarting.
 *
 * <p>Geyser reads its packs folder when it starts; a pack replaced afterwards would be sent with the size and hash
 * of the old file. Every deployment is therefore copied into an immutable snapshot, and a session whose Geyser
 * record is older gets the snapshot instead. The display variants and the text layout a session uses are those of
 * the pack it loaded, so new furniture and model variants show for players who join after the build. Item, block
 * and skull registrations still need Geyser's start (the deployment says when).
 */
public final class LivePacks implements AutoCloseable {
    /** Snapshots kept on disk: a session may still download one it was offered just before the next build. */
    private static final int KEEP = 3;
    private static final int MAX_MANIFEST_CHARS = 64 * 1024;

    /** One pack file: what a session loads, its display variants and its text layout. */
    public static final class Snapshot {
        private final Path file;
        private final UUID uuid;
        private final int[] version;
        private final ResourcePack pack;
        private final DisplayIndex index;
        private volatile GeyserTextLayoutBridge.Layout layout;
        private volatile boolean layoutRead;

        private Snapshot(Path file, UUID uuid, int[] version, ResourcePack pack, DisplayIndex index) {
            this.file = file;
            this.uuid = uuid;
            this.version = version;
            this.pack = pack;
            this.index = index;
        }

        public DisplayIndex index() {
            return index;
        }

        GeyserTextLayoutBridge.Layout layout() {
            if (!layoutRead) {
                synchronized (this) {
                    if (!layoutRead) {
                        try {
                            layout = GeyserTextLayoutBridge.readLayout(file);
                        } catch (IOException | RuntimeException unreadable) {
                            layout = null;
                        }
                        layoutRead = true;
                    }
                }
            }
            return layout;
        }

        boolean sameVersion(ResourcePack served) {
            var header = served.manifest().header();
            var other = header.version();
            return header.uuid().equals(uuid) && other.major() == version[0] && other.minor() == version[1]
                    && other.patch() == version[2];
        }
    }

    private final Path deployed;
    private final Path store;
    private final Logger logger;
    private final EventRegistrar registrar;
    private final Snapshot startup;
    private final Map<GeyserConnection, Snapshot> sessions = Collections.synchronizedMap(new WeakHashMap<>());
    private volatile Snapshot current;

    private LivePacks(Object owner, Path deployed, Path store, Snapshot startup, Logger logger) {
        this.deployed = deployed;
        this.store = store;
        this.startup = startup;
        this.current = startup;
        this.logger = logger;
        this.registrar = EventRegistrar.of(owner);
    }

    /**
     * Starts with the pack Geyser loaded when it started ({@code deployed}, which may not exist yet).
     *
     * @param store a folder of Twilight's own for the snapshots
     */
    public static LivePacks start(Object owner, Path deployed, Path store, Logger logger) throws IOException {
        Snapshot startup = Files.isRegularFile(deployed) ? read(deployed, null) : null;
        LivePacks packs = new LivePacks(owner, deployed, store, startup, logger);
        com.siberanka.twilight.geyser.GeyserEvents.subscribe(packs.registrar, SessionLoadResourcePacksEvent.class, packs::load);
        return packs;
    }

    /**
     * Copies the deployed pack into a snapshot that sessions joining from now on load. Call after every deployment
     * (off the server thread: the copy and the hash read the whole pack).
     */
    public synchronized void prepare() throws IOException {
        if (!Files.isRegularFile(deployed)) return;
        Files.createDirectories(store);
        Path temporary = store.resolve("incoming.tmp");
        Files.copy(deployed, temporary, StandardCopyOption.REPLACE_EXISTING);
        Snapshot probe = read(temporary, null);
        Path target = store.resolve("twilight-" + probe.version[0] + '.' + probe.version[1] + '.' + probe.version[2]
                + '-' + System.currentTimeMillis() + ".zip");
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target);
        }
        ResourcePack pack = ResourcePack.create(PackCodec.path(target));
        // Hash and size now, not while the first player waits for the pack list.
        pack.codec().sha256();
        pack.codec().size();
        current = new Snapshot(target, probe.uuid, probe.version, pack, probe.index);
        retire(target);
    }

    /** The pack a session loaded; the one Geyser started with for sessions that joined before this listened. */
    public Snapshot of(GeyserConnection session) {
        Snapshot found = session == null ? null : sessions.get(session);
        return found != null ? found : startup != null ? startup : current;
    }

    /** The display variants of a session's pack (empty when there is none). */
    public DisplayIndex index(GeyserConnection session) {
        Snapshot snapshot = of(session);
        return snapshot == null ? DisplayIndex.EMPTY : snapshot.index();
    }

    /** The text layout of the pack each session loaded. */
    public GeyserTextLayoutBridge.LayoutSource layouts() {
        return new GeyserTextLayoutBridge.LayoutSource() {
            @Override public void refresh() {
                // Every snapshot reads its own layout once; nothing changes in place.
            }

            @Override public GeyserTextLayoutBridge.Layout layout(org.geysermc.geyser.session.GeyserSession session) {
                Snapshot snapshot = of(session);
                return snapshot == null ? null : snapshot.layout();
            }

            @Override public String describe() {
                Snapshot snapshot = current;
                GeyserTextLayoutBridge.Layout layout = snapshot == null ? null : snapshot.layout();
                return layout == null ? "idle: the served pack has no layout table" : layout.table().entryCount() + " font entries";
            }
        };
    }

    private void load(SessionLoadResourcePacksEvent event) {
        try {
            Snapshot latest = current;
            if (latest == null) return;
            ResourcePack served = null;
            for (ResourcePack pack : event.resourcePacks()) {
                if (pack.uuid().equals(latest.uuid)) served = pack;
            }
            // Not offered by Geyser (another plugin sends the pack): nothing to replace.
            if (served == null) return;
            if (latest.pack != null && !latest.sameVersion(served)) {
                ResourcePackOption<?>[] options = event.options(served.uuid()).toArray(ResourcePackOption<?>[]::new);
                event.unregister(served.uuid());
                event.register(latest.pack, options);
            }
            sessions.put(event.connection(), latest);
        } catch (RuntimeException | LinkageError failure) {
            // Geyser then sends what it loaded at start, as without this listener.
            logger.log(Level.WARNING, "Could not offer the current Twilight pack to a Bedrock player", failure);
        }
    }

    private void retire(Path keep) {
        try (var files = Files.list(store)) {
            List<Path> snapshots = files.filter(path -> path.getFileName().toString().startsWith("twilight-")
                            && path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(LivePacks::modified).reversed()).toList();
            for (int i = KEEP; i < snapshots.size(); i++) {
                if (snapshots.get(i).equals(keep)) continue;
                try {
                    Files.deleteIfExists(snapshots.get(i));
                } catch (IOException inUse) {
                    // A download still reads it; the next build removes it.
                }
            }
        } catch (IOException ignored) {
            // Old snapshots only take space.
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException gone) {
            return 0;
        }
    }

    /** The manifest identity and display variants of a pack Twilight built. */
    private static Snapshot read(Path file, ResourcePack pack) throws IOException {
        UUID uuid;
        int[] version = new int[3];
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var entry = zip.getEntry("manifest.json");
            if (entry == null || entry.getSize() > MAX_MANIFEST_CHARS) throw new IOException("The Twilight pack has no readable manifest");
            JsonObject header;
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                header = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("header");
            }
            uuid = UUID.fromString(header.get("uuid").getAsString());
            var numbers = header.getAsJsonArray("version");
            for (int i = 0; i < 3; i++) version[i] = numbers.get(i).getAsInt();
        } catch (RuntimeException malformed) {
            throw new IOException("The Twilight pack manifest is malformed", malformed);
        }
        return new Snapshot(file, uuid, version, pack, DisplayIndex.read(file));
    }

    @Override
    public void close() {
        com.siberanka.twilight.geyser.GeyserEvents.unregisterAll(registrar);
        sessions.clear();
    }
}
