package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.model.SquaremapWorldSettings;

import java.util.List;
import java.util.Optional;

/** Narrow client-thread tile composition boundary implemented by {@link SquaremapTileRenderer}. */
public interface WorldMapTileSource extends AutoCloseable {
    List<TileKey> requestViewport(WorldViewport viewport, double worldScale, RequestContext context);
    int drainUploads(int maxUploads);
    Optional<GpuTileRegion> gpuHandleOrParent(TileKey key);
    void updateNamespace(String profileId, SquaremapWorldSettings settings);
    default void revalidateCurrentViewport() { }
    void invalidateGpuResources();
    @Override void close();
}
