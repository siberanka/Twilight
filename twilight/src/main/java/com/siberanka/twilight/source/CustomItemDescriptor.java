package com.siberanka.twilight.source;

import java.util.Optional;
import java.util.OptionalInt;

public record CustomItemDescriptor(
        String provider,
        String baseItem,
        Optional<String> itemModel,
        OptionalInt customModelData,
        String displayName
) {}
