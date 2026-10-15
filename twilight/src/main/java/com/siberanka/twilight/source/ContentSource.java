package com.siberanka.twilight.source;

import java.nio.file.Path;

public record ContentSource(String provider, Kind kind, Path path, int priority) {
    public enum Kind { RESOURCE_PACK, DATAPACK, MODEL_SOURCE, PROVIDER_DATA, SEASONAL_PACK }
}
