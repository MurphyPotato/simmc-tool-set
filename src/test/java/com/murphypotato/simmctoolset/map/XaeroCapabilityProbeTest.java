package com.murphypotato.simmctoolset.map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.*;
import static org.junit.jupiter.api.Assertions.*;

class XaeroCapabilityProbeTest {
    private static final String GUI_MAP = "xaero/map/gui/GuiMap";
    private static final String WORLD_OPTIONS = "xaero/map/common/config/option/WorldMapProfiledConfigOptions";
    private static final String MODULE_RENDERER = "xaero/hud/minimap/module/MinimapRenderer";
    private static final String MODULE_SESSION = "xaero/hud/minimap/module/MinimapSession";
    private static final String MODULE_CONTEXT = "xaero/hud/render/module/ModuleRenderContext";
    private static final String PROCESSOR = "xaero/common/minimap/MinimapProcessor";
    private static final String MINIMAP_RENDERER = "xaero/common/minimap/render/MinimapRenderer";
    private static final String MINIMAP_OPTIONS =
            "xaero/hud/minimap/common/config/option/MinimapProfiledConfigOptions";
    private static final String RENDER =
            "(Lxaero/hud/minimap/module/MinimapSession;Lxaero/hud/render/module/ModuleRenderContext;"
                    + "Lnet/minecraft/class_332;F)V";
    private static final String OUTSIDE_PIP =
            "(Lxaero/hud/minimap/module/MinimapSession;IIIIDFIFLnet/minecraft/class_332;)V";
    private static final String RENDER_MINIMAP =
            "(Lxaero/hud/minimap/module/MinimapSession;Lxaero/common/minimap/MinimapProcessor;"
                    + "IIIIDFIFLxaero/common/graphics/CustomVertexConsumers;)V";

    @Test
    void detectsLegacyAndProfiledFamilies() {
        Fixture legacy = new Fixture()
                .world(true, false, true, false, 1)
                .minimap(false, true, true, false, false)
                .waypoint();
        assertCapabilities(legacy,
                WORLD_VIEW, WORLD_SURFACE_LEGACY, WORLD_NAVIGATION, WORLD_ZOOM_LEGACY,
                MINIMAP_RENDER_COMMON, MINIMAP_HOOK_PIP, MINIMAP_SHAPE_LEGACY, WAYPOINT_WRITE);

        Fixture profiled = new Fixture()
                .world(false, true, false, true, 1)
                .minimap(false, true, false, true, true)
                .waypoint();
        assertCapabilities(profiled,
                WORLD_VIEW, WORLD_SURFACE_PROFILED, WORLD_NAVIGATION, WORLD_ZOOM_PROFILED,
                MINIMAP_RENDER_COMMON, MINIMAP_HOOK_PIP, MINIMAP_SHAPE_PROFILE,
                MINIMAP_FABRIC_HUD, WAYPOINT_WRITE);

        Fixture depthTrace = new Fixture().minimap(true, false, true, false, false);
        assertCapabilities(depthTrace,
                MINIMAP_RENDER_COMMON, MINIMAP_HOOK_DEPTH_TRACE, MINIMAP_SHAPE_LEGACY);
    }

    @Test
    void detectsWorldDWithOtherConfigReadsAndRejectsDuplicateTargetChains() {
        Fixture executable = new Fixture().worldD(1, 1, 3);
        assertCapabilities(executable,
                WORLD_VIEW, WORLD_SURFACE_PROFILED, WORLD_NAVIGATION, WORLD_ZOOM_PROFILED);
        assertDoesNotThrow(executable::executeWorldProfiledReads);

        XaeroCapabilitySnapshot duplicate =
                XaeroCapabilityProbe.probe(new Fixture().worldD(2, 2, 2).loader());
        assertTrue(duplicate.has(WORLD_VIEW));
        assertTrue(duplicate.has(WORLD_NAVIGATION));
        assertFalse(duplicate.has(WORLD_SURFACE_PROFILED));
        assertFalse(duplicate.has(WORLD_ZOOM_PROFILED));
    }

    @Test
    void rejectsVerifierValidBrokenWorldProfileChains() {
        Fixture fixture = new Fixture().worldDWithBrokenTargetChains();
        XaeroCapabilitySnapshot broken =
                XaeroCapabilityProbe.probe(fixture.loader());
        assertTrue(broken.has(WORLD_VIEW));
        assertTrue(broken.has(WORLD_NAVIGATION));
        assertFalse(broken.has(WORLD_SURFACE_PROFILED));
        assertFalse(broken.has(WORLD_ZOOM_PROFILED));
        assertDoesNotThrow(fixture::executeWorldProfiledReads);
    }

    @Test
    void detectsMinimapBAndCAndRejectsBrokenProfileShapeReads() {
        assertCapabilities(new Fixture().minimap(false, true, true, false, false),
                MINIMAP_RENDER_COMMON, MINIMAP_HOOK_PIP, MINIMAP_SHAPE_LEGACY);
        assertCapabilities(new Fixture().minimapProfile(1, 3),
                MINIMAP_RENDER_COMMON, MINIMAP_HOOK_PIP, MINIMAP_SHAPE_PROFILE);

        assertFalse(XaeroCapabilityProbe.probe(new Fixture().minimapProfile(0, 2).loader())
                .has(MINIMAP_SHAPE_PROFILE));
        assertFalse(XaeroCapabilityProbe.probe(new Fixture().minimapProfile(2, 2).loader())
                .has(MINIMAP_SHAPE_PROFILE));
        Fixture brokenShape = new Fixture().minimapProfileWithBrokenShapeChain();
        assertFalse(XaeroCapabilityProbe.probe(brokenShape.loader()).has(MINIMAP_SHAPE_PROFILE));
        assertDoesNotThrow(() -> brokenShape.verifyClass(MINIMAP_RENDERER));
    }

    @Test
    void ignoresUnrelatedStructureAndRejectsBrokenFingerprints() {
        Fixture baseline = new Fixture().world(false, true, false, true, 1);
        Fixture unrelated = new Fixture().world(false, true, false, true, 1).unrelatedClass();
        assertEquals(XaeroCapabilityProbe.probe(baseline.loader()).capabilities(),
                XaeroCapabilityProbe.probe(unrelated.loader()).capabilities());

        XaeroCapabilitySnapshot duplicateAnchor =
                XaeroCapabilityProbe.probe(new Fixture().world(false, true, false, true, 2).loader());
        assertFalse(duplicateAnchor.has(WORLD_SURFACE_PROFILED));
        assertTrue(duplicateAnchor.has(WORLD_VIEW));
        assertTrue(duplicateAnchor.has(WORLD_NAVIGATION));
        assertTrue(duplicateAnchor.has(WORLD_ZOOM_PROFILED));

        XaeroCapabilitySnapshot missingWaypointMember =
                XaeroCapabilityProbe.probe(new Fixture().waypointWithoutSave().loader());
        assertFalse(missingWaypointMember.has(WAYPOINT_WRITE));
    }

    @Test
    void rejectsAmbiguousFamilies() {
        XaeroCapabilitySnapshot snapshot = XaeroCapabilityProbe.probe(new Fixture()
                .world(true, true, true, true, 1)
                .minimap(true, true, true, true, false)
                .loader());
        assertFalse(snapshot.has(WORLD_SURFACE_LEGACY));
        assertFalse(snapshot.has(WORLD_SURFACE_PROFILED));
        assertFalse(snapshot.has(WORLD_ZOOM_LEGACY));
        assertFalse(snapshot.has(WORLD_ZOOM_PROFILED));
        assertFalse(snapshot.has(MINIMAP_HOOK_DEPTH_TRACE));
        assertFalse(snapshot.has(MINIMAP_HOOK_PIP));
        assertFalse(snapshot.has(MINIMAP_SHAPE_LEGACY));
        assertFalse(snapshot.has(MINIMAP_SHAPE_PROFILE));
    }

    @Test
    void readsClassResourcesWithoutLoadingXaeroClasses() {
        Fixture fixture = new Fixture().world(true, false, true, false, 1);
        ResourceClassLoader loader = fixture.loader();
        XaeroCapabilitySnapshot snapshot = XaeroCapabilityProbe.probe(loader);
        assertTrue(snapshot.has(WORLD_SURFACE_LEGACY));
        assertFalse(loader.xaeroClassLoadRequested);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.capabilities().clear());
    }

    private static void assertCapabilities(Fixture fixture, XaeroCapabilitySnapshot.Capability... expected) {
        Set<XaeroCapabilitySnapshot.Capability> actual = XaeroCapabilityProbe.probe(fixture.loader()).capabilities();
        assertEquals(EnumSet.copyOf(Set.of(expected)), actual);
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final Map<String, byte[]> resources;
        private boolean xaeroClassLoadRequested;

        private ResourceClassLoader(Map<String, byte[]> resources) {
            super(null);
            this.resources = Map.copyOf(resources);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            byte[] bytes = resources.get(name);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("xaero.")) xaeroClassLoadRequested = true;
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = resources.get(name.replace('.', '/') + ".class");
            if (bytes == null) throw new ClassNotFoundException(name);
            return defineClass(name, bytes, 0, bytes.length);
        }

        private Class<?> loadVerifiedClass(String name) throws ClassNotFoundException {
            return loadClass(name, true);
        }
    }

    private static final class Fixture {
        private final Map<String, byte[]> resources = new HashMap<>();

        private Fixture world(boolean legacySurface, boolean profiledSurface,
                              boolean legacyZoom, boolean profiledZoom, int profiledAnchorCount) {
            return world(legacySurface, profiledSurface, legacyZoom, profiledZoom,
                    profiledAnchorCount, profiledZoom ? 1 : 0, 0, false, false);
        }

        private Fixture worldD(int surfaceAnchorCount, int zoomAnchorCount, int otherConfigReads) {
            return world(false, true, false, true,
                    surfaceAnchorCount, zoomAnchorCount, otherConfigReads, false, false);
        }

        private Fixture worldDWithBrokenTargetChains() {
            return world(false, true, false, true, 1, 1, 2, true, true);
        }

        private Fixture world(boolean legacySurface, boolean profiledSurface,
                              boolean legacyZoom, boolean profiledZoom, int profiledAnchorCount,
                              int profiledZoomAnchorCount, int otherConfigReads,
                              boolean brokenSurfaceChain, boolean brokenZoomChain) {
            clazz(GUI_MAP, visitor -> {
                method(visitor, "<init>", "()V", code -> { });
                field(visitor, "cameraX", "D");
                field(visitor, "cameraZ", "D");
                field(visitor, "scale", "D");
                field(visitor, "screenScale", "D");
                field(visitor, "lastViewedDimensionId", "Lnet/minecraft/class_5321;");
                field(visitor, "userScale", "D");
                field(visitor, "destScale", "D");
                field(visitor, "zoomAnim", "Lxaero/map/animation/Animation;");
                method(visitor, "getScaleMultiplier", "(I)D", code -> code.visitInsn(Opcodes.DRETURN));
                method(visitor, "method_25394", "(Lnet/minecraft/class_332;IIF)V", code -> {
                    if (legacySurface) {
                        code.visitInsn(Opcodes.ACONST_NULL);
                        code.visitFieldInsn(Opcodes.GETFIELD, "xaero/map/settings/ModSettings", "renderArrow", "Z");
                        code.visitInsn(Opcodes.POP);
                    }
                    if (profiledSurface) {
                        for (int i = 0; i < otherConfigReads; i++) {
                            profileBoolean(code, WORLD_OPTIONS, "OTHER_" + i, false);
                        }
                        for (int i = 0; i < profiledAnchorCount; i++) {
                            profileBoolean(code, WORLD_OPTIONS, "ARROW", brokenSurfaceChain);
                        }
                    }
                });
                method(visitor, "changeZoom", "(DI)V", code -> {
                    if (legacyZoom) {
                        code.visitLdcInsn(0.0625d);
                        code.visitInsn(Opcodes.POP2);
                        code.visitLdcInsn(0.0625d);
                        code.visitInsn(Opcodes.POP2);
                    }
                    if (profiledZoom) {
                        code.visitVarInsn(Opcodes.ALOAD, 0);
                        code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GUI_MAP, "applyZoomLimits", "()V", false);
                    }
                });
                if (profiledZoom) {
                    method(visitor, "applyZoomLimits", "()V", code -> {
                        code.visitLdcInsn(0.0625d);
                        code.visitInsn(Opcodes.POP2);
                        for (int i = 0; i < otherConfigReads; i++) {
                            profileBoolean(code, WORLD_OPTIONS, "OTHER_" + i, false);
                        }
                        for (int i = 0; i < profiledZoomAnchorCount; i++) {
                            profileBoolean(code, WORLD_OPTIONS,
                                    "UNLIMITED_ZOOM_OUT", brokenZoomChain);
                        }
                        code.visitLdcInsn(0.001953125d);
                        code.visitInsn(Opcodes.POP2);
                    });
                }
            });
            if (legacySurface) clazz("xaero/map/render/util/GuiRenderUtil",
                    visitor -> method(visitor, "flushGUI", "()V", code -> { }));
            if (profiledSurface) {
                clazz("xaero/lib/client/render/util/GuiRenderUtil",
                        visitor -> method(visitor, "flushGUI", "()V", code -> { }));
            }
            if (profiledSurface || profiledZoom) {
                clazz(WORLD_OPTIONS, visitor -> {
                    if (profiledSurface) {
                        staticField(visitor, "ARROW",
                                "Lxaero/lib/common/config/option/BooleanConfigOption;");
                    }
                    if (profiledZoom) {
                        staticField(visitor, "UNLIMITED_ZOOM_OUT",
                                "Lxaero/lib/common/config/option/BooleanConfigOption;");
                    }
                    for (int i = 0; i < otherConfigReads; i++) {
                        staticField(visitor, "OTHER_" + i,
                                "Lxaero/lib/common/config/option/BooleanConfigOption;");
                    }
                });
                clientConfigClasses();
            }
            clazz("net/minecraft/class_332", visitor -> { });
            clazz("net/minecraft/class_5321", visitor -> { });
            clazz("xaero/map/animation/Animation", visitor -> { });
            return this;
        }

        private Fixture minimap(boolean depthTrace, boolean pip, boolean legacyShape,
                                boolean profiledShape, boolean fabricHud) {
            return minimap(depthTrace, pip, legacyShape, profiledShape, fabricHud,
                    profiledShape ? 1 : 0, 0, false);
        }

        private Fixture minimapProfile(int shapeReadCount, int otherConfigReads) {
            return minimap(false, true, false, true, false,
                    shapeReadCount, otherConfigReads, false);
        }

        private Fixture minimapProfileWithBrokenShapeChain() {
            return minimap(false, true, false, true, false, 1, 2, true);
        }

        private Fixture minimap(boolean depthTrace, boolean pip, boolean legacyShape,
                                boolean profiledShape, boolean fabricHud,
                                int shapeReadCount, int otherConfigReads,
                                boolean brokenShapeChain) {
            clazz(MODULE_RENDERER, visitor -> method(visitor, "render", RENDER, code -> {
                if (depthTrace) {
                    code.visitInsn(Opcodes.ACONST_NULL);
                    code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROCESSOR, "getDepthSkipper",
                            "()Lxaero/hud/render/util/GuiDepthSkipper;", false);
                    code.visitInsn(Opcodes.POP);
                }
                if (pip) {
                    code.visitInsn(Opcodes.ACONST_NULL);
                    code.visitInsn(Opcodes.ACONST_NULL);
                    code.visitInsn(Opcodes.ICONST_0);
                    code.visitInsn(Opcodes.ICONST_0);
                    code.visitInsn(Opcodes.ICONST_0);
                    code.visitInsn(Opcodes.ICONST_0);
                    code.visitInsn(Opcodes.DCONST_0);
                    code.visitInsn(Opcodes.FCONST_0);
                    code.visitInsn(Opcodes.ICONST_0);
                    code.visitInsn(Opcodes.FCONST_0);
                    code.visitInsn(Opcodes.ACONST_NULL);
                    code.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                            MINIMAP_RENDERER, "renderOutsidePip", OUTSIDE_PIP, false);
                }
            }));
            clazz(MODULE_CONTEXT, visitor -> {
                field(visitor, "x", "I");
                field(visitor, "y", "I");
                field(visitor, "w", "I");
                field(visitor, "screenWidth", "I");
                field(visitor, "screenHeight", "I");
            });
            clazz(MODULE_SESSION, visitor -> method(visitor, "getProcessor",
                    "()Lxaero/common/minimap/MinimapProcessor;", code -> code.visitInsn(Opcodes.ARETURN)));
            clazz(PROCESSOR, visitor -> method(visitor, "getMinimapZoom", "()D",
                    code -> code.visitInsn(Opcodes.DRETURN)));
            clazz("xaero/common/HudMod", visitor -> {
                if (legacyShape || profiledShape) {
                    staticField(visitor, "INSTANCE", "Lxaero/common/HudMod;");
                }
                if (legacyShape) {
                    method(visitor, "getSettings", "()Lxaero/common/settings/ModSettings;",
                            code -> code.visitInsn(Opcodes.ARETURN));
                }
                if (profiledShape) method(visitor, "getHudConfigs",
                        "()Lxaero/lib/common/config/channel/ConfigChannel;",
                        code -> code.visitInsn(Opcodes.ARETURN));
            });
            if (legacyShape) clazz("xaero/common/settings/ModSettings",
                    visitor -> field(visitor, "minimapShape", "I"));
            if (profiledShape) {
                clazz(MINIMAP_OPTIONS, visitor -> {
                    staticField(visitor, "SHAPE", "Lxaero/lib/common/config/option/RangeConfigOption;");
                    for (int i = 0; i < otherConfigReads; i++) {
                        staticField(visitor, "OTHER_" + i,
                                "Lxaero/lib/common/config/option/RangeConfigOption;");
                    }
                });
                clientConfigClasses();
                clazz(MINIMAP_RENDERER, visitor -> {
                    field(visitor, "modMain", "Lxaero/common/HudMod;");
                    method(visitor, "renderMinimap", RENDER_MINIMAP, code -> {
                        code.visitVarInsn(Opcodes.ALOAD, 0);
                        code.visitFieldInsn(Opcodes.GETFIELD, MINIMAP_RENDERER,
                                "modMain", "Lxaero/common/HudMod;");
                        code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "xaero/common/HudMod",
                                "getHudConfigs", "()Lxaero/lib/common/config/channel/ConfigChannel;", false);
                        code.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                "xaero/lib/common/config/channel/ConfigChannel",
                                "getClientConfigManager",
                                "()Lxaero/lib/client/config/ClientConfigManager;", false);
                        code.visitVarInsn(Opcodes.ASTORE, 13);
                        for (int i = 0; i < otherConfigReads; i++) {
                            profileInteger(code, 13, "OTHER_" + i, false);
                        }
                        for (int i = 0; i < shapeReadCount; i++) {
                            profileInteger(code, 13, "SHAPE", brokenShapeChain);
                        }
                    });
                });
                clazz("xaero/common/graphics/CustomVertexConsumers", visitor -> { });
            }
            if (fabricHud) clazz("xaero/common/events/ModClientEventsFabric", visitor ->
                    method(visitor, "register", "()V", code -> {
                        code.visitLdcInsn("xaerohud");
                        code.visitInsn(Opcodes.POP);
                        code.visitLdcInsn("hud");
                        code.visitInsn(Opcodes.POP);
                        code.visitInsn(Opcodes.ACONST_NULL);
                        code.visitInsn(Opcodes.ACONST_NULL);
                        code.visitInsn(Opcodes.ACONST_NULL);
                        code.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "net/fabricmc/fabric/api/client/rendering/v1/hud/HudElementRegistry",
                                "attachElementAfter",
                                "(Lnet/minecraft/class_2960;Lnet/minecraft/class_2960;"
                                        + "Lnet/fabricmc/fabric/api/client/rendering/v1/hud/HudElement;)V", true);
                    }));
            return this;
        }

        private Fixture waypoint() {
            return waypoint(true);
        }

        private Fixture waypointWithoutSave() {
            return waypoint(false);
        }

        private Fixture waypoint(boolean saveMethod) {
            clazz("xaero/common/XaeroMinimapSession", visitor -> {
                method(visitor, "getCurrentSession", "()Lxaero/common/XaeroMinimapSession;",
                        code -> code.visitInsn(Opcodes.ARETURN));
                method(visitor, "getMinimapProcessor", "()Lxaero/common/minimap/MinimapProcessor;",
                        code -> code.visitInsn(Opcodes.ARETURN));
            });
            clazz(PROCESSOR, visitor -> {
                method(visitor, "getMinimapZoom", "()D", code -> code.visitInsn(Opcodes.DRETURN));
                method(visitor, "getSession", "()Lxaero/hud/minimap/module/MinimapSession;",
                        code -> code.visitInsn(Opcodes.ARETURN));
            });
            clazz(MODULE_SESSION, visitor -> {
                method(visitor, "getProcessor", "()Lxaero/common/minimap/MinimapProcessor;",
                        code -> code.visitInsn(Opcodes.ARETURN));
                method(visitor, "getWorldManager", "()Lxaero/hud/minimap/world/MinimapWorldManager;",
                        code -> code.visitInsn(Opcodes.ARETURN));
                method(visitor, "getWorldManagerIO", "()Lxaero/hud/minimap/world/io/MinimapWorldManagerIO;",
                        code -> code.visitInsn(Opcodes.ARETURN));
            });
            clazz("xaero/hud/minimap/world/MinimapWorldManager", visitor -> method(visitor, "getCurrentWorld",
                    "()Lxaero/hud/minimap/world/MinimapWorld;", code -> code.visitInsn(Opcodes.ARETURN)));
            clazz("xaero/hud/minimap/world/MinimapWorld", visitor -> {
                method(visitor, "getCurrentWaypointSet", "()Lxaero/hud/minimap/waypoint/set/WaypointSet;",
                        code -> code.visitInsn(Opcodes.ARETURN));
                method(visitor, "addWaypointSet", "(Ljava/lang/String;)V", code -> { });
                method(visitor, "setCurrentWaypointSetId", "(Ljava/lang/String;)V", code -> { });
            });
            clazz("xaero/common/minimap/waypoints/Waypoint", visitor -> method(visitor, "<init>",
                    "(IIILjava/lang/String;Ljava/lang/String;Lxaero/hud/minimap/waypoint/WaypointColor;"
                            + "Lxaero/hud/minimap/waypoint/WaypointPurpose;ZZ)V", code -> { }));
            clazz("xaero/hud/minimap/waypoint/set/WaypointSet", visitor -> {
                method(visitor, "add", "(Lxaero/common/minimap/waypoints/Waypoint;)V", code -> { });
                method(visitor, "remove", "(Lxaero/common/minimap/waypoints/Waypoint;)V", code -> { });
            });
            clazz("xaero/hud/minimap/waypoint/WaypointColor", visitor ->
                    field(visitor, "AQUA", "Lxaero/hud/minimap/waypoint/WaypointColor;"));
            clazz("xaero/hud/minimap/waypoint/WaypointPurpose", visitor ->
                    field(visitor, "NORMAL", "Lxaero/hud/minimap/waypoint/WaypointPurpose;"));
            clazz("xaero/hud/minimap/world/io/MinimapWorldManagerIO", visitor -> {
                if (saveMethod) method(visitor, "saveWorld", "(Lxaero/hud/minimap/world/MinimapWorld;)V", code -> { });
            });
            return this;
        }

        private Fixture unrelatedClass() {
            clazz("example/Unrelated", visitor -> {
                field(visitor, "changed", "Ljava/lang/String;");
                method(visitor, "anything", "(J)I", code -> code.visitInsn(Opcodes.IRETURN));
            });
            return this;
        }

        private void clientConfigClasses() {
            clazz("xaero/lib/common/config/channel/ConfigChannel", visitor -> method(visitor,
                    "getClientConfigManager", "()Lxaero/lib/client/config/ClientConfigManager;",
                    code -> code.visitInsn(Opcodes.ARETURN)));
            clazz("xaero/lib/common/config/option/ConfigOption", visitor -> { });
            clazz("xaero/lib/common/config/option/BooleanConfigOption",
                    "xaero/lib/common/config/option/ConfigOption", visitor -> { });
            clazz("xaero/lib/common/config/option/RangeConfigOption",
                    "xaero/lib/common/config/option/ConfigOption", visitor -> { });
            clazz("xaero/lib/client/config/ClientConfigManager", visitor -> {
                method(visitor, "<init>", "()V", code -> { });
                method(visitor, "getEffective",
                        "(Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;",
                        code -> {
                            code.visitInsn(Opcodes.POP);
                            code.visitFieldInsn(Opcodes.GETSTATIC,
                                    "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
                            code.visitInsn(Opcodes.ARETURN);
                        });
            });
        }

        private ResourceClassLoader loader() {
            return new ResourceClassLoader(resources);
        }

        private void executeWorldProfiledReads() throws ReflectiveOperationException {
            ResourceClassLoader loader = loader();
            Class<?> guiMap = loader.loadClass(GUI_MAP.replace('/', '.'));
            Object instance = guiMap.getConstructor().newInstance();
            Class<?> drawContext = loader.loadClass("net.minecraft.class_332");
            guiMap.getMethod("method_25394", drawContext, int.class, int.class, float.class)
                    .invoke(instance, null, 0, 0, 0.0f);
            guiMap.getMethod("changeZoom", double.class, int.class)
                    .invoke(instance, 1.0d, 0);
        }

        private void verifyClass(String internalName) throws ClassNotFoundException {
            loader().loadVerifiedClass(internalName.replace('/', '.'));
        }

        private void clazz(String name, Consumer<ClassVisitor> body) {
            clazz(name, "java/lang/Object", body);
        }

        private void clazz(String name, String superName, Consumer<ClassVisitor> body) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, superName, null);
            body.accept(writer);
            writer.visitEnd();
            resources.put(name + ".class", writer.toByteArray());
        }

        private static void field(ClassVisitor visitor, String name, String descriptor) {
            visitor.visitField(Opcodes.ACC_PUBLIC, name, descriptor, null, null).visitEnd();
        }

        private static void staticField(ClassVisitor visitor, String name, String descriptor) {
            visitor.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    name, descriptor, null, null).visitEnd();
        }

        private static void method(ClassVisitor visitor, String name, String descriptor,
                                   Consumer<MethodVisitor> instructions) {
            MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
            method.visitCode();
            if ("<init>".equals(name)) {
                method.visitVarInsn(Opcodes.ALOAD, 0);
                method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        "java/lang/Object", "<init>", "()V", false);
            } else {
                pushDefault(method, descriptor);
            }
            instructions.accept(method);
            if (descriptor.endsWith("V")) method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }

        private static void profileBoolean(MethodVisitor method, String owner, String name,
                                           boolean brokenChain) {
            method.visitTypeInsn(Opcodes.NEW, "xaero/lib/client/config/ClientConfigManager");
            method.visitInsn(Opcodes.DUP);
            method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "xaero/lib/client/config/ClientConfigManager", "<init>", "()V", false);
            method.visitFieldInsn(Opcodes.GETSTATIC, owner, name,
                    "Lxaero/lib/common/config/option/BooleanConfigOption;");
            if (brokenChain) {
                method.visitInsn(Opcodes.POP);
                method.visitInsn(Opcodes.ACONST_NULL);
            }
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "xaero/lib/client/config/ClientConfigManager",
                    "getEffective", "(Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;", false);
            method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            method.visitInsn(Opcodes.POP);
        }

        private static void profileInteger(MethodVisitor method, int managerLocal, String name,
                                           boolean brokenChain) {
            method.visitVarInsn(Opcodes.ALOAD, managerLocal);
            method.visitFieldInsn(Opcodes.GETSTATIC, MINIMAP_OPTIONS, name,
                    "Lxaero/lib/common/config/option/RangeConfigOption;");
            if (brokenChain) {
                method.visitInsn(Opcodes.POP);
                method.visitInsn(Opcodes.ACONST_NULL);
            }
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "xaero/lib/client/config/ClientConfigManager",
                    "getEffective", "(Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;", false);
            method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "java/lang/Integer", "intValue", "()I", false);
            method.visitInsn(Opcodes.POP);
        }

        private static void pushDefault(MethodVisitor method, String descriptor) {
            switch (Type.getReturnType(descriptor).getSort()) {
                case Type.VOID -> { }
                case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT ->
                        method.visitInsn(Opcodes.ICONST_0);
                case Type.FLOAT -> method.visitInsn(Opcodes.FCONST_0);
                case Type.LONG -> method.visitInsn(Opcodes.LCONST_0);
                case Type.DOUBLE -> method.visitInsn(Opcodes.DCONST_0);
                default -> method.visitInsn(Opcodes.ACONST_NULL);
            }
        }
    }
}
