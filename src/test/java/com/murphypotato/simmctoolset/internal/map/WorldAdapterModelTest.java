package com.murphypotato.simmctoolset.internal.map;

import com.murphypotato.simmctoolset.internal.map.integration.WorldAdapterSelection;
import com.murphypotato.simmctoolset.internal.map.integration.WorldMapHealth;
import com.murphypotato.simmctoolset.internal.map.integration.WorldSurfaceAdapter;
import com.murphypotato.simmctoolset.internal.map.integration.WorldViewAdapter;
import com.murphypotato.simmctoolset.internal.map.integration.WorldZoomAdapter;
import com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldAdapterModelTest {
    @Test
    void selectsWorldFamiliesWithoutCopyingGuiAdapters() {
        assertEquals(WorldAdapterSelection.Family.A, select(WORLD_SURFACE_LEGACY, WORLD_ZOOM_LEGACY).family());
        assertEquals(WorldAdapterSelection.Family.BC, select(WORLD_SURFACE_PROFILED, WORLD_ZOOM_LEGACY).family());
        assertEquals(WorldAdapterSelection.Family.D, select(WORLD_SURFACE_PROFILED, WORLD_ZOOM_PROFILED).family());
        assertFalse(select(false, true).view());
        assertFalse(select(true, false).navigation());
        WorldAdapterSelection unknown = new WorldAdapterSelection(false, false,
                WorldAdapterSelection.Surface.NONE, WorldAdapterSelection.Zoom.NONE);
        assertEquals(WorldAdapterSelection.Family.UNKNOWN, unknown.family());
        assertFalse(unknown.hasSurface());
    }

    @Test
    void zoomFallbackLeavesNormalMapUsable() {
        WorldZoomAdapter adapter = WorldZoomAdapter.resolve(select(WORLD_SURFACE_PROFILED, WORLD_ZOOM_PROFILED));
        assertNotNull(adapter);
        assertEquals(WorldZoomAdapter.PROFILED_FLOOR, adapter.floor(true, 0.0625d));
        assertEquals(0.5d, adapter.floor(false, 0.5d));
        assertEquals(0.5d, adapter.fallback(0.5d));
        assertEquals(WorldZoomAdapter.LEGACY_FLOOR, adapter.fallback(Double.NaN));
    }

    @Test
    void nullAndNonFiniteViewsNeverEnterHitTest() {
        assertTrue(WorldViewAdapter.view(10, 20, 2, 1, 800, 600).isPresent());
        assertTrue(WorldViewAdapter.view(10, 20, 2, 1, 0, 600).isEmpty());
        assertTrue(WorldViewAdapter.view(Double.NaN, 20, 2, 1, 800, 600).isEmpty());
        assertTrue(WorldViewAdapter.view(10, Double.POSITIVE_INFINITY, 2, 1, 800, 600).isEmpty());
        assertTrue(WorldViewAdapter.view(10, 20, Double.NEGATIVE_INFINITY, 1, 800, 600).isEmpty());
        assertTrue(WorldViewAdapter.view(10, 20, 2, Double.NaN, 800, 600).isEmpty());
        assertFalse(WorldViewAdapter.shouldHitTest(null));
    }

    @Test
    void surfaceResolutionUsesOnlyTheSelectedFlushAbi() {
        ClassLoader loader = fixtureLoader();
        WorldSurfaceAdapter legacy = WorldSurfaceAdapter.resolve(
                select(WORLD_SURFACE_LEGACY), loader);
        WorldSurfaceAdapter profiled = WorldSurfaceAdapter.resolve(
                select(WORLD_SURFACE_PROFILED), loader);
        assertNotNull(legacy);
        assertNotNull(profiled);
        assertEquals(WorldAdapterSelection.Surface.LEGACY, legacy.kind());
        assertEquals(WorldAdapterSelection.Surface.PROFILED, profiled.kind());
        legacy.flushGui();
        profiled.flushGui();
        assertNull(WorldSurfaceAdapter.resolve(select(WORLD_SURFACE_LEGACY),
                missingFlushLoader()));
    }

    @Test
    void eachWorldFailureLeavesOtherCapabilitiesHealthy() {
        WorldMapHealth surfaceFail = new WorldMapHealth();
        surfaceFail.fail(WorldMapHealth.Capability.SURFACE);
        assertFalse(surfaceFail.worldSurfaceEnabled());
        assertTrue(surfaceFail.navigationEnabled());
        assertTrue(surfaceFail.extendedZoomEnabled());

        WorldMapHealth navigationFail = new WorldMapHealth();
        navigationFail.fail(WorldMapHealth.Capability.NAVIGATION);
        assertTrue(navigationFail.worldSurfaceEnabled());
        assertFalse(navigationFail.navigationEnabled());
        assertTrue(navigationFail.extendedZoomEnabled());

        WorldMapHealth zoomFail = new WorldMapHealth();
        zoomFail.fail(WorldMapHealth.Capability.EXTENDED_ZOOM);
        assertTrue(zoomFail.worldSurfaceEnabled());
        assertTrue(zoomFail.navigationEnabled());
        assertFalse(zoomFail.extendedZoomEnabled());
    }

    private static WorldAdapterSelection select(XaeroCapabilitySnapshot.Capability... capabilities) {
        return select(true, true, capabilities);
    }

    private static WorldAdapterSelection select(boolean view, boolean navigation,
                                                XaeroCapabilitySnapshot.Capability... capabilities) {
        EnumSet<XaeroCapabilitySnapshot.Capability> set = EnumSet.noneOf(XaeroCapabilitySnapshot.Capability.class);
        if (view) set.add(WORLD_VIEW);
        if (navigation) set.add(WORLD_NAVIGATION);
        for (XaeroCapabilitySnapshot.Capability capability : capabilities) set.add(capability);
        return WorldAdapterSelection.select(new XaeroCapabilitySnapshot(set));
    }

    private static ClassLoader missingFlushLoader() {
        return new ClassLoader(WorldAdapterModelTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("xaero.map.render.util.GuiRenderUtil")
                        || name.equals("xaero.lib.client.render.util.GuiRenderUtil")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
    }

    private static ClassLoader fixtureLoader() {
        return new ClassLoader(WorldAdapterModelTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("xaero.map.render.util.GuiRenderUtil")
                        || name.equals("xaero.lib.client.render.util.GuiRenderUtil")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded != null) return loaded;
                    String resource = name.replace('.', '/') + ".class";
                    try (InputStream input = getParent().getResourceAsStream(resource)) {
                        if (input == null) throw new ClassNotFoundException(name);
                        byte[] bytes = input.readAllBytes();
                        return defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException failure) {
                        throw new ClassNotFoundException(name, failure);
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
    }
}