/*
 * Spatial grid design is derived in part from JR1258/EarthMC-Map-Addon
 * src/main/java/net/townymap/render/WorldMapRenderer.java, upstream commit
 * c85c5003855eb47868868b931624b951cffba74e. Changes: generalized the grid
 * buckets, negative-coordinate floor behavior, and query deduplication for
 * immutable SIMMC marker bounds while removing all Minecraft dependencies.
 * Licensed under Apache-2.0; see THIRD_PARTY_NOTICES.md.
 */
package com.murphypotato.simmctoolset.internal.map.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable spatial index. Duplicate IDs are rejected; value identity is not required to be unique. */
public final class SpatialIndex<T> {
    private static final int MAX_ENTRIES = 1_000_000;
    private static final long MAX_CELLS_PER_ENTRY = 4_096;
    private static final long MAX_QUERY_CELLS = 65_536;

    public record Entry<T>(String id, T value, Bounds bounds) {
        public Entry {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Entry ID must not be blank");
            }
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    record QueryProbe<T>(List<Entry<T>> entries, int exactChecks) {
        QueryProbe {
            entries = List.copyOf(entries);
            if (exactChecks < 0) {
                throw new IllegalArgumentException("Exact check count must not be negative");
            }
        }
    }

    private record Cell(long x, long z) { }

    private final double cellSize;
    private final List<Entry<T>> entries;
    private final Map<Cell, List<Integer>> cells;
    private final List<Integer> globalEntries;

    private SpatialIndex(
            double cellSize, List<Entry<T>> entries, Map<Cell, List<Integer>> cells, List<Integer> globalEntries
    ) {
        this.cellSize = cellSize;
        this.entries = entries;
        this.cells = cells;
        this.globalEntries = globalEntries;
    }

    public static <T> SpatialIndex<T> build(double cellSize, List<Entry<T>> source) {
        if (!Double.isFinite(cellSize) || cellSize <= 0) {
            throw new IllegalArgumentException("Cell size must be positive and finite");
        }
        Objects.requireNonNull(source, "source");
        if (source.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Spatial index exceeds entry limit");
        }
        List<Entry<T>> entries = List.copyOf(source);
        Set<String> ids = new HashSet<>(Math.max(16, entries.size() * 2));
        Map<Cell, List<Integer>> mutableCells = new LinkedHashMap<>();
        List<Integer> global = new ArrayList<>();
        for (int ordinal = 0; ordinal < entries.size(); ordinal++) {
            Entry<T> entry = Objects.requireNonNull(entries.get(ordinal), "entry");
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate spatial entry ID: " + entry.id());
            }
            CellRange range = CellRange.of(entry.bounds(), cellSize);
            if (range == null || range.cellCountExceeds(MAX_CELLS_PER_ENTRY)) {
                global.add(ordinal);
                continue;
            }
            int entryOrdinal = ordinal;
            range.forEach((x, z) -> mutableCells.computeIfAbsent(new Cell(x, z), ignored -> new ArrayList<>())
                    .add(entryOrdinal));
        }
        Map<Cell, List<Integer>> frozenCells = new HashMap<>(mutableCells.size());
        mutableCells.forEach((key, value) -> frozenCells.put(key, List.copyOf(value)));
        return new SpatialIndex<>(cellSize, entries, Map.copyOf(frozenCells), List.copyOf(global));
    }

    public int size() {
        return entries.size();
    }

    public List<Entry<T>> entries() {
        return entries;
    }

    public List<Entry<T>> query(Bounds viewport) {
        return queryWithStats(viewport).entries();
    }

    public List<Entry<T>> queryPoint(double x, double z) {
        return queryPointWithStats(x, z).entries();
    }

    QueryProbe<T> queryWithStats(Bounds viewport) {
        Objects.requireNonNull(viewport, "viewport");
        int[] candidates = candidates(viewport);
        if (candidates == null) {
            candidates = allOrdinals();
        }
        ArrayList<Entry<T>> result = new ArrayList<>();
        for (int ordinal : candidates) {
            Entry<T> entry = entries.get(ordinal);
            if (entry.bounds().intersects(viewport)) {
                result.add(entry);
            }
        }
        return new QueryProbe<>(result, candidates.length);
    }

    QueryProbe<T> queryPointWithStats(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Query point must be finite");
        }
        Long cellX = floorCell(x, cellSize);
        Long cellZ = floorCell(z, cellSize);
        HashSet<Integer> candidates = new HashSet<>(Math.max(16, globalEntries.size() * 2));
        candidates.addAll(globalEntries);
        if (cellX == null || cellZ == null) {
            return filterPoint(sortedOrdinals(candidates), x, z);
        }
        addCell(candidates, cellX, cellZ);
        return filterPoint(sortedOrdinals(candidates), x, z);
    }

    int indexedCellCount() {
        return cells.size();
    }

    int globalEntryCount() {
        return globalEntries.size();
    }

    private QueryProbe<T> filterPoint(int[] candidates, double x, double z) {
        ArrayList<Entry<T>> result = new ArrayList<>();
        for (int ordinal : candidates) {
            Entry<T> entry = entries.get(ordinal);
            if (entry.bounds().contains(x, z)) {
                result.add(entry);
            }
        }
        return new QueryProbe<>(result, candidates.length);
    }

    /** Returns null only for an unindexable or oversized viewport requiring the documented full-scan fallback. */
    private int[] candidates(Bounds viewport) {
        CellRange range = CellRange.of(viewport, cellSize);
        if (range == null || range.cellCountExceeds(MAX_QUERY_CELLS)) {
            return null;
        }
        HashSet<Integer> candidates = new HashSet<>(Math.max(16, globalEntries.size() * 2));
        candidates.addAll(globalEntries);
        range.forEach((x, z) -> addCell(candidates, x, z));
        return sortedOrdinals(candidates);
    }

    private int[] allOrdinals() {
        int[] ordinals = new int[entries.size()];
        Arrays.setAll(ordinals, index -> index);
        return ordinals;
    }

    private static int[] sortedOrdinals(Set<Integer> candidates) {
        int[] ordinals = candidates.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(ordinals);
        return ordinals;
    }

    private void addCell(Set<Integer> candidates, long x, long z) {
        List<Integer> ordinals = cells.get(new Cell(x, z));
        if (ordinals != null) {
            candidates.addAll(ordinals);
        }
    }

    private interface CellConsumer {
        void accept(long x, long z);
    }

    private record CellRange(long minX, long minZ, long maxX, long maxZ) {
        private static CellRange of(Bounds bounds, double cellSize) {
            Long minX = floorCell(bounds.minX(), cellSize);
            Long minZ = floorCell(bounds.minZ(), cellSize);
            Long maxX = floorCell(bounds.maxX(), cellSize);
            Long maxZ = floorCell(bounds.maxZ(), cellSize);
            return minX == null || minZ == null || maxX == null || maxZ == null
                    ? null : new CellRange(minX, minZ, maxX, maxZ);
        }

        private boolean cellCountExceeds(long limit) {
            double width = (double) maxX - minX + 1.0;
            double height = (double) maxZ - minZ + 1.0;
            return width > limit || height > limit || width * height > limit;
        }

        private void forEach(CellConsumer consumer) {
            for (long z = minZ; ; z++) {
                for (long x = minX; ; x++) {
                    consumer.accept(x, z);
                    if (x == maxX) {
                        break;
                    }
                }
                if (z == maxZ) {
                    break;
                }
            }
        }
    }

    private static Long floorCell(double coordinate, double cellSize) {
        double cell = Math.floor(coordinate / cellSize);
        if (!Double.isFinite(cell) || cell < -0x1.0p63 || cell >= 0x1.0p63) {
            return null;
        }
        return (long) cell;
    }
}
