/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.network;

import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.collision.SolidCollision;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Which blocks hide what is behind them, read through Geyser's view of the player's world (its chunk cache on a
 * proxy, the server's world on a backend: the same lookups Geyser makes for block interactions). Only full,
 * solid, opaque cubes count; glass, leaves, ice, barriers, grates and every partial block let the view through,
 * and so does every block that cannot be read.
 */
final class GeyserBlocks {
    /** Known answers per Java block state: 0 unknown, 1 opaque, 2 see-through. */
    private static final AtomicReferenceArray<byte[]> STATES = new AtomicReferenceArray<>(1);
    private static final String[] SEE_THROUGH = {"glass", "leaves", "barrier", "spawner", "beacon", "slime_block",
            "honey_block", "mangrove_roots", "grate", "scaffolding", "light", "structure_void", "chorus", "vault",
            "conduit", "powder_snow", "moving_piston", "frosted_ice", "copper_bulb"};

    private GeyserBlocks() {}

    static Occlusion.Blocks of(GeyserSession session) {
        var world = session.getGeyser().getWorldManager();
        return (x, y, z) -> {
            if (session.isClosed()) return false;
            return opaque(world.getBlockAt(session, x, y, z));
        };
    }

    static boolean opaque(int javaId) {
        if (javaId <= 0 || javaId > 1 << 20) return false;
        byte[] known = STATES.get(0);
        if (known == null || known.length <= javaId) {
            byte[] grown = new byte[Math.max(javaId + 1, known == null ? 32_768 : known.length * 2)];
            if (known != null) System.arraycopy(known, 0, grown, 0, known.length);
            STATES.compareAndSet(0, known, grown);
            known = STATES.get(0);
            if (known.length <= javaId) return false;
        }
        byte answer = known[javaId];
        if (answer == 0) {
            answer = compute(javaId) ? (byte) 1 : (byte) 2;
            known[javaId] = answer;
        }
        return answer == 1;
    }

    private static boolean compute(int javaId) {
        try {
            if (!(BlockRegistries.COLLISIONS.get(javaId) instanceof SolidCollision)) return false;
            BlockState state = BlockState.of(javaId);
            if (state == null) return false;
            String name = String.valueOf(state.block()).toLowerCase(Locale.ROOT);
            // Block{javaIdentifier='minecraft:ice', javaId=...}: plain ice is see-through, packed and blue ice are not.
            if (name.contains(":ice'") || name.endsWith(":ice")) return false;
            for (String part : SEE_THROUGH) if (name.contains(part)) return false;
            return true;
        } catch (RuntimeException | LinkageError unknown) {
            return false;
        }
    }
}
