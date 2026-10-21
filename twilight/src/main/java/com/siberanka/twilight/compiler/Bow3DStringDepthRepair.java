package com.siberanka.twilight.compiler;

import java.util.Map;

/**
 * Prevents a second (incorrect) geometric translation of 3D bowstrings.
 *
 * <p>The Java resource pack authors the bowstring in a single stationary YZ
 * ribbon across the idle and draw states. The drawable string already moves
 * inside that ribbon because the bow / pulling texture changes per state.
 * Translating the ribbon as well doubles the drawback and moves the visible
 * string too far behind the bow.
 *
 * <p>Leave the Java-to-Bedrock-converted geometry and the projectile completely
 * untouched. The existing BowArrowAndStringDrawRepair and
 * BowIdleStringUvRepair retain responsibility for their independent UV and
 * arrow-direction fixes. In particular, do not attempt to mirror the string's
 * origin or infer an absolute Z offset from another bow model.
 *
 * <p>This class stays present because BowIdleStringUvRepair calls it. Return
 * zero changes intentionally. Source Java models that author geometric motion
 * continue to retain that motion via the normal model converter.
 */
public final class Bow3DStringDepthRepair {
    private Bow3DStringDepthRepair() {}

    public static int apply(Map<String, byte[]> packFiles) {
        return 0;
    }
}
