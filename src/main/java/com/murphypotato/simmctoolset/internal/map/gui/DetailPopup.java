package com.murphypotato.simmctoolset.internal.map.gui;

import com.murphypotato.simmctoolset.internal.map.model.LandDetails;
import com.murphypotato.simmctoolset.internal.map.model.MapMarker;
import com.murphypotato.simmctoolset.internal.map.model.SearchEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DetailPopup {
    public enum Page {
        OVERVIEW("概览"), MEMBERS("成员"), NATION_AND_TERRITORIES("国家与领土"), RAW_DESCRIPTION("原始说明");
        private final String title;
        Page(String title) { this.title = title; }
        public String title() { return title; }
    }
    private SearchEntry entry;
    private Page page = Page.OVERVIEW;
    private int scrollOffset;
    private int visibleLineCapacity = 12;

    public void open(SearchEntry entry) { this.entry = java.util.Objects.requireNonNull(entry); page = Page.OVERVIEW; scrollOffset = 0; }
    public void close() { entry = null; scrollOffset = 0; }
    public boolean isOpen() { return entry != null; }
    public Optional<SearchEntry> entry() { return Optional.ofNullable(entry); }
    public Page page() { return page; }
    public void selectPage(Page page) { this.page = java.util.Objects.requireNonNull(page); scrollOffset = 0; }
    public void setVisibleLineCapacity(int capacity) { visibleLineCapacity = Math.max(1, capacity); clamp(); }
    public int scrollOffset() { return scrollOffset; }
    public void scroll(int delta) { scrollOffset += delta; clamp(); }
    public Optional<String> coordinateText() { return entry == null ? Optional.empty() : entry.center()
            .map(point -> Math.round(point.x()) + ", 64, " + Math.round(point.z())); }
    public List<String> contentLines() {
        if (entry == null) return List.of();
        Optional<LandDetails> details = entry.marker().flatMap(DetailPopup::landDetails);
        List<String> lines = new ArrayList<>();
        if (page == Page.OVERVIEW) {
            lines.add(entry.displayName());
            coordinateText().ifPresent(value -> lines.add("坐标：" + value));
            details.ifPresent(d -> { d.description().ifPresent(v -> lines.add("描述：" + v)); d.level().ifPresent(v -> lines.add("等级：" + v)); d.owner().ifPresent(v -> lines.add("所有者：" + v)); d.balanceText().ifPresent(v -> lines.add("余额：" + v)); });
        } else if (page == Page.MEMBERS) {
            details.ifPresent(d -> lines.addAll(d.members()));
        } else if (page == Page.NATION_AND_TERRITORIES) {
            details.ifPresent(d -> { d.nationName().ifPresent(v -> lines.add("国家：" + v)); d.nationLevel().ifPresent(v -> lines.add("国家等级：" + v)); d.nationCapital().ifPresent(v -> lines.add("首都：" + v)); lines.addAll(d.nationTerritories()); });
        } else {
            details.map(LandDetails::rawText).filter(v -> !v.isBlank()).ifPresent(lines::add);
            if (lines.isEmpty()) entry.marker().map(MapMarker::popup).filter(v -> !v.isBlank()).ifPresent(lines::add);
        }
        return List.copyOf(lines);
    }
    public List<String> visibleLines() { List<String> all = contentLines(); int from = Math.min(scrollOffset, all.size()); return all.subList(from, Math.min(all.size(), from + visibleLineCapacity)); }
    private void clamp() { scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, contentLines().size() - visibleLineCapacity))); }
    private static Optional<LandDetails> landDetails(MapMarker marker) { return marker instanceof com.murphypotato.simmctoolset.internal.map.model.PolygonMarker polygon ? polygon.landDetails() : Optional.empty(); }
}
