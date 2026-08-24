package com.murphypotato.simmctoolset.map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

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
    private static final String RENDER =
            "(Lxaero/hud/minimap/module/MinimapSession;Lxaero/hud/render/module/ModuleRenderContext;"
                    + "Lnet/minecraft/class_332;F)V";
    private static final String OUTSIDE_PIP =
            "(Lxaero/hud/minimap/module/MinimapSession;IIIIDFIFLnet/minecraft/class_332;)V";

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
    }

    private static final class Fixture {
        private final Map<String, byte[]> resources = new HashMap<>();

        private Fixture world(boolean legacySurface, boolean profiledSurface,
                              boolean legacyZoom, boolean profiledZoom, int profiledAnchorCount) {
            clazz(GUI_MAP, visitor -> {
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
                        code.visitFieldInsn(Opcodes.GETFIELD, "xaero/map/settings/ModSettings", "renderArrow", "Z");
                    }
                    if (profiledSurface) {
                        for (int i = 0; i < profiledAnchorCount; i++) profileBoolean(code, WORLD_OPTIONS, "ARROW");
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
                        code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, GUI_MAP, "applyZoomLimits", "()V", false);
                    }
                });
                if (profiledZoom) {
                    method(visitor, "applyZoomLimits", "()V", code -> {
                        code.visitLdcInsn(0.0625d);
                        code.visitInsn(Opcodes.POP2);
                        profileBoolean(code, WORLD_OPTIONS, "UNLIMITED_ZOOM_OUT");
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
                clazz(WORLD_OPTIONS, visitor -> field(visitor, "ARROW",
                        "Lxaero/lib/common/config/option/BooleanConfigOption;"));
                clientConfigClasses();
            }
            if (profiledZoom) {
                clazz(WORLD_OPTIONS, visitor -> {
                    field(visitor, "ARROW", "Lxaero/lib/common/config/option/BooleanConfigOption;");
                    field(visitor, "UNLIMITED_ZOOM_OUT", "Lxaero/lib/common/config/option/BooleanConfigOption;");
                });
                clientConfigClasses();
            }
            return this;
        }

        private Fixture minimap(boolean depthTrace, boolean pip, boolean legacyShape,
                                boolean profiledShape, boolean fabricHud) {
            clazz(MODULE_RENDERER, visitor -> method(visitor, "render", RENDER, code -> {
                if (depthTrace) code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, PROCESSOR, "getDepthSkipper",
                        "()Lxaero/hud/render/util/GuiDepthSkipper;", false);
                if (pip) code.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                        "xaero/common/minimap/render/MinimapRenderer", "renderOutsidePip", OUTSIDE_PIP, false);
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
                if (legacyShape) {
                    field(visitor, "INSTANCE", "Lxaero/common/HudMod;");
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
                clazz("xaero/hud/minimap/common/config/option/MinimapProfiledConfigOptions",
                        visitor -> field(visitor, "SHAPE", "Lxaero/lib/common/config/option/RangeConfigOption;"));
                clientConfigClasses();
            }
            if (fabricHud) clazz("xaero/common/events/ModClientEventsFabric", visitor ->
                    method(visitor, "register", "()V", code -> {
                        code.visitLdcInsn("xaerohud");
                        code.visitLdcInsn("hud");
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
            clazz("xaero/lib/client/config/ClientConfigManager", visitor -> method(visitor, "getEffective",
                    "(Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;",
                    code -> code.visitInsn(Opcodes.ARETURN)));
        }

        private ResourceClassLoader loader() {
            return new ResourceClassLoader(resources);
        }

        private void clazz(String name, Consumer<ClassVisitor> body) {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
            body.accept(writer);
            writer.visitEnd();
            resources.put(name + ".class", writer.toByteArray());
        }

        private static void field(ClassVisitor visitor, String name, String descriptor) {
            visitor.visitField(Opcodes.ACC_PUBLIC, name, descriptor, null, null).visitEnd();
        }

        private static void method(ClassVisitor visitor, String name, String descriptor,
                                   Consumer<MethodVisitor> instructions) {
            MethodVisitor method = visitor.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
            method.visitCode();
            instructions.accept(method);
            if (descriptor.endsWith("V")) method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(16, 16);
            method.visitEnd();
        }

        private static void profileBoolean(MethodVisitor method, String owner, String name) {
            method.visitFieldInsn(Opcodes.GETSTATIC, owner, name,
                    "Lxaero/lib/common/config/option/BooleanConfigOption;");
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "xaero/lib/client/config/ClientConfigManager",
                    "getEffective", "(Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;", false);
            method.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            method.visitInsn(Opcodes.POP);
        }
    }
}
