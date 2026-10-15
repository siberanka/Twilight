package com.siberanka.twilight.source;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.BaseStream;

/** Normalizes the public registry container shapes used by supported providers. */
final class ProviderRegistryEntries {
    private ProviderRegistryEntries() {}

    static Optional<List<?>> ids(Object value) {
        if (value instanceof Map<?, ?> map) return Optional.of(List.copyOf(map.keySet()));
        if (value instanceof Collection<?> collection) return Optional.of(List.copyOf(collection));
        if (value instanceof Iterable<?> iterable) return Optional.of(copy(iterable.iterator()));
        if (value instanceof Iterator<?> iterator) return Optional.of(copy(iterator));
        if (value instanceof BaseStream<?, ?> stream) {
            try (stream) {
                return Optional.of(copy(stream.iterator()));
            }
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> entries = new ArrayList<>(length);
            for (int index = 0; index < length; index++) entries.add(Array.get(value, index));
            return Optional.of(List.copyOf(entries));
        }
        return Optional.empty();
    }

    private static List<?> copy(Iterator<?> iterator) {
        List<Object> entries = new ArrayList<>();
        iterator.forEachRemaining(entries::add);
        return List.copyOf(entries);
    }
}
