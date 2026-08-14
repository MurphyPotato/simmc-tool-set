package com.murphypotato.simmctoolset.internal.map.search;

import com.murphypotato.simmctoolset.internal.map.model.SearchEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class FavoritesStore {
    public record Favorite(String stableKey, Optional<SearchEntry> entry) {
        public Favorite {
            Objects.requireNonNull(stableKey, "stableKey");
            entry = Objects.requireNonNull(entry, "entry");
        }
    }

    private final LinkedHashMap<String, Optional<SearchEntry>> entries = new LinkedHashMap<>();

    public FavoritesStore() {
        this(List.of());
    }

    public FavoritesStore(Collection<String> stableKeys) {
        if (stableKeys == null) {
            return;
        }
        for (String stableKey : stableKeys) {
            String cleaned = cleanKey(stableKey);
            if (cleaned != null) {
                entries.putIfAbsent(cleaned, Optional.empty());
            }
        }
    }

    public synchronized boolean add(SearchEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!entry.favoriteEligible()) {
            throw new IllegalArgumentException("Entry is not eligible for favorites");
        }
        boolean added = !entries.containsKey(entry.stableKey());
        entries.put(entry.stableKey(), Optional.of(entry));
        return added;
    }

    public synchronized boolean remove(String stableKey) {
        return entries.remove(stableKey) != null;
    }

    public synchronized boolean contains(String stableKey) {
        return entries.containsKey(stableKey);
    }

    public synchronized void rebind(SearchIndex index) {
        Objects.requireNonNull(index, "index");
        entries.replaceAll((stableKey, previous) -> index.resolve(stableKey));
    }

    public synchronized List<Favorite> favorites() {
        ArrayList<Favorite> result = new ArrayList<>(entries.size());
        entries.forEach((stableKey, entry) -> result.add(new Favorite(stableKey, entry)));
        return List.copyOf(result);
    }

    public synchronized List<String> stableKeys() {
        return List.copyOf(entries.keySet());
    }

    public synchronized void replaceKeys(Collection<String> stableKeys) {
        entries.clear();
        if (stableKeys == null) return;
        for (String stableKey : stableKeys) {
            String cleaned = cleanKey(stableKey);
            if (cleaned != null) entries.putIfAbsent(cleaned, Optional.empty());
        }
    }

    private static String cleanKey(String stableKey) {
        if (stableKey == null) {
            return null;
        }
        String cleaned = stableKey.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
