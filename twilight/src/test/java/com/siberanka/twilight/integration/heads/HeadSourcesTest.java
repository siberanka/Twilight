/* Copyright (c) 2026 siberanka. Twilight License (PolyForm Noncommercial 1.0.0 + terms), see LICENSE. */
package com.siberanka.twilight.integration.heads;

import com.siberanka.twilight.geyser.CustomSkulls;
import com.siberanka.twilight.geyser.HeadTextures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class HeadSourcesTest {
    private static final String SKIN = "4b0b1f7b8a6fa27e5f8fdb4c1d1e44f6a2c1e0d9c3b2a19f8e7d6c5b4a392817";
    private static final String OTHER = "1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809";
    @TempDir Path root;

    private static String profile(String hash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test void readsSkinsFromProfilesUrlsAndHashes() {
        assertEquals(SKIN, HeadTextures.fromValue(profile(SKIN)));
        assertEquals(SKIN, HeadTextures.fromUrl("https://textures.minecraft.net/texture/" + SKIN.toUpperCase()));
        assertNull(HeadTextures.fromValue("not base64!"));
        assertTrue(HeadTextures.name("Notch"));
        assertFalse(HeadTextures.name("%player_name%"));
    }

    /** The encodings menu plugins write a head in, whatever the plugin; placeholders are not players. */
    @Test void scansPluginConfigurationsForHeads() throws Exception {
        Path plugins = root.resolve("plugins");
        Files.createDirectories(plugins.resolve("DeluxeMenus/gui_menus"));
        Files.writeString(plugins.resolve("DeluxeMenus/gui_menus/main.yml"), """
                items:
                  profile:
                    material: basehead-%s
                  shop:
                    material: texture-%s
                  self:
                    material: head-%%player_name%%
                  notch:
                    material: head-Notch
                  hdb:
                    material: hdb-1234
                """.formatted(profile(SKIN), OTHER));
        Files.createDirectories(plugins.resolve("Twilight"));
        Files.writeString(plugins.resolve("Twilight/ignored.yml"), "texture: " + profile("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
        Files.createDirectories(plugins.resolve("zMenu"));
        Files.writeString(plugins.resolve("zMenu/buttons.yml"), "url: http://textures.minecraft.net/texture/" + OTHER + "\n");
        Files.createDirectories(plugins.resolve("TrMenu/menus"));
        Files.writeString(plugins.resolve("TrMenu/menus/main.yml"), """
                Icons:
                  jeb:
                    display:
                      mat: 'head:jeb_'
                  me:
                    display:
                      mat: 'head:%%player_name%%'
                  skin:
                    skin: %s
                """.formatted(OTHER));
        var found = HeadSources.scan(plugins, id -> id.equals("1234") ? profile("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd") : null);
        assertEquals(Set.of(SKIN, OTHER, "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd"), found.skins());
        assertEquals(Set.of("Notch", "jeb_"), found.names());
        assertEquals(Set.of("1234"), found.headDatabase());
        assertTrue(HeadSources.heads(found).contains("name:Notch"));
    }

    /** A pack's head list is read back with every value validated. */
    @Test void customSkullsReadThePacksHeads() throws Exception {
        Path pack = root.resolve("pack.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack))) {
            zip.putNextEntry(new ZipEntry(CustomSkulls.PATH));
            zip.write(("{\"format\":1,\"skins\":[\"" + SKIN + "\",\"bad hash\"],\"names\":[\"Notch\",\"%x%\"]}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var heads = CustomSkulls.read(List.of(pack, root.resolve("missing.zip")));
        assertEquals(Set.of(SKIN), heads.skins());
        assertEquals(Set.of("Notch"), heads.names());
        assertEquals(Set.of(), heads.players());
    }

    /**
     * The players' skins fill what the shown heads leave free, most recent first and taken in turn from each pack;
     * a change in them alone never counts as other heads shown (it asks for no restart).
     */
    @Test void customSkullsAddThePlayersSkins() throws Exception {
        Path first = root.resolve("first.zip"), second = root.resolve("second.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(first))) {
            zip.putNextEntry(new ZipEntry(CustomSkulls.PATH));
            zip.write(("{\"format\":1,\"skins\":[\"" + SKIN + "\"],\"names\":[]}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(CustomSkulls.PLAYERS_PATH));
            zip.write(("{\"format\":1,\"skins\":[\"" + SKIN + "\",\"aaaaaaaaaaaaaaaa\",\"cccccccccccccccc\"]}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(second))) {
            zip.putNextEntry(new ZipEntry(CustomSkulls.PLAYERS_PATH));
            zip.write("{\"format\":1,\"skins\":[\"bbbbbbbbbbbbbbbb\",\"no\"]}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var heads = CustomSkulls.read(List.of(first, second));
        assertEquals(Set.of(SKIN), heads.skins());
        assertEquals(List.of("bbbbbbbbbbbbbbbb", "aaaaaaaaaaaaaaaa", "cccccccccccccccc"), List.copyOf(heads.players()));
        assertEquals(4, heads.size());
        var shownOnly = CustomSkulls.read(List.of(first));
        assertTrue(heads.sameShown(shownOnly));
        assertNotEquals(heads, shownOnly);
    }
}
