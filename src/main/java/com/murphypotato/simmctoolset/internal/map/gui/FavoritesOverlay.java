package com.murphypotato.simmctoolset.internal.map.gui;

import com.murphypotato.simmctoolset.internal.map.model.SearchEntry;
import com.murphypotato.simmctoolset.internal.map.search.FavoritesStore;
import com.murphypotato.simmctoolset.internal.map.search.SearchIndex;
import java.util.List;

public final class FavoritesOverlay {
    private final FavoritesStore store;
    public FavoritesOverlay() { this(new FavoritesStore()); }
    public FavoritesOverlay(FavoritesStore store) { this.store = store; }
    public void rebind(SearchIndex index) { store.rebind(index); }
    public boolean toggle(SearchEntry entry) { if (store.contains(entry.stableKey())) { store.remove(entry.stableKey()); return false; } store.add(entry); return true; }
    public boolean contains(String key) { return store.contains(key); }
    public List<FavoritesStore.Favorite> favorites() { return store.favorites(); }
    public List<String> stableKeys() { return store.stableKeys(); }
    public void restoreStableKeys(java.util.Collection<String> keys) { store.replaceKeys(keys); }
}
