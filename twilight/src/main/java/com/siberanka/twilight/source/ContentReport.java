package com.siberanka.twilight.source;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ContentReport(
        Instant scannedAt,
        int sources,
        long sourceBytes,
        int entries,
        int textures,
        int itemDefinitions,
        int legacyItemModels,
        int modelJson,
        int blockStates,
        int fontDefinitions,
        int sounds,
        int bbmodels,
        int biomeDefinitions,
        List<String> sourcePaths,
        Map<String, Integer> providers
) {}
