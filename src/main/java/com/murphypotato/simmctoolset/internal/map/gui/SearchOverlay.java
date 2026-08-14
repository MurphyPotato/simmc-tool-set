package com.murphypotato.simmctoolset.internal.map.gui;

import com.murphypotato.simmctoolset.internal.map.model.SearchEntry;
import com.murphypotato.simmctoolset.internal.map.search.SearchIndex;
import java.util.List;
import java.util.Optional;

public final class SearchOverlay {
    private SearchIndex index = SearchIndex.build(new com.murphypotato.simmctoolset.internal.map.model.MapSnapshot(List.of()), List.of());
    private final StringBuilder query = new StringBuilder();
    private int selected;
    public void update(SearchIndex index) { this.index = index; selected = 0; }
    public String query() { return query.toString(); }
    public List<SearchEntry> results() { return index.search(query.toString()).stream().limit(10).toList(); }
    public void type(char value) { if (!Character.isISOControl(value) && query.length() < 64) { query.append(value); selected = 0; } }
    public boolean backspace() { if (query.isEmpty()) return false; query.deleteCharAt(query.length() - 1); selected = 0; return true; }
    public void move(int delta) { int size = results().size(); if (size > 0) selected = Math.floorMod(selected + delta, size); }
    public Optional<SearchEntry> selected() { List<SearchEntry> values = results(); return values.isEmpty() ? Optional.empty() : Optional.of(values.get(Math.min(selected, values.size() - 1))); }
    public void clear() { query.setLength(0); selected = 0; }
}
