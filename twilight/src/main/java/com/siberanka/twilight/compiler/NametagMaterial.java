/*
 * Copyright (c) 2026 siberanka.
 * Licensed under the Twilight License (PolyForm Noncommercial 1.0.0 with additional terms); see LICENSE.
 */
package com.siberanka.twilight.compiler;

import java.nio.charset.StandardCharsets;

/**
 * Bedrock draws a dark box behind every name tag; Java text displays choose their own background,
 * and nameplate plugins (CustomNameplates) make it transparent and draw images instead. The
 * box is a translucent black quad drawn with the {@code name_tag} material: blending it
 * additively adds black, which leaves the scene unchanged, while the tag's text keeps its own
 * material. Bedrock merges pack materials into its own by name.
 */
final class NametagMaterial {
    static final String PATH = "materials/ui3D.material";

    private NametagMaterial() {}

    static byte[] hiddenBackground() {
        return """
                {
                  "materials": {
                    "version": "1.0.0",
                    "name_tag": {
                      "blendSrc": "One",
                      "blendDst": "One"
                    }
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
    }
}
