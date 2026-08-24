package com.murphypotato.simmctoolset.map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.*;

public final class XaeroCapabilityProbe {
    private static final String GUI_MAP = "xaero/map/gui/GuiMap";
    private static final String WORLD_OPTIONS = "xaero/map/common/config/option/WorldMapProfiledConfigOptions";
    private static final String MODULE_RENDERER = "xaero/hud/minimap/module/MinimapRenderer";
    private static final String MODULE_SESSION = "xaero/hud/minimap/module/MinimapSession";
    private static final String MODULE_CONTEXT = "xaero/hud/render/module/ModuleRenderContext";
    private static final String PROCESSOR = "xaero/common/minimap/MinimapProcessor";
    private static final String MINIMAP_RENDERER = "xaero/common/minimap/render/MinimapRenderer";
    private static final String MINIMAP_OPTIONS =
            "xaero/hud/minimap/common/config/option/MinimapProfiledConfigOptions";
    private static final String CONFIG_CHANNEL = "xaero/lib/common/config/channel/ConfigChannel";
    private static final String CONFIG_MANAGER = "xaero/lib/client/config/ClientConfigManager";
    private static final String BOOL_OPTION = "Lxaero/lib/common/config/option/BooleanConfigOption;";
    private static final String GET_EFFECTIVE =
            "(Lxaero/lib/common/config/option/ConfigOption;)Ljava/lang/Object;";
    private static final String RENDER =
            "(Lxaero/hud/minimap/module/MinimapSession;Lxaero/hud/render/module/ModuleRenderContext;"
                    + "Lnet/minecraft/class_332;F)V";
    private static final String OUTSIDE_PIP =
            "(Lxaero/hud/minimap/module/MinimapSession;IIIIDFIFLnet/minecraft/class_332;)V";
    private static final String RENDER_MINIMAP =
            "(Lxaero/hud/minimap/module/MinimapSession;Lxaero/common/minimap/MinimapProcessor;"
                    + "IIIIDFIFLxaero/common/graphics/CustomVertexConsumers;)V";

    private XaeroCapabilityProbe() { }

    public static XaeroCapabilitySnapshot probe(ClassLoader loader) {
        Scanner scanner = new Scanner(Objects.requireNonNull(loader, "loader"));
        EnumSet<XaeroCapabilitySnapshot.Capability> found =
                EnumSet.noneOf(XaeroCapabilitySnapshot.Capability.class);

        ClassInfo world = scanner.read(GUI_MAP);
        if (world.fields("cameraX:D", "cameraZ:D", "scale:D", "screenScale:D",
                "lastViewedDimensionId:Lnet/minecraft/class_5321;")) {
            found.add(WORLD_VIEW);
        }
        if (world.fields("userScale:D", "destScale:D", "zoomAnim:Lxaero/map/animation/Animation;")
                && world.hasMethod("getScaleMultiplier", "(I)D")) {
            found.add(WORLD_NAVIGATION);
        }

        MethodInfo worldRender = world.method("method_25394", "(Lnet/minecraft/class_332;IIF)V");
        boolean legacySurface = worldRender.fieldCount(Opcodes.GETFIELD,
                "xaero/map/settings/ModSettings", "renderArrow", "Z") == 1
                && scanner.read("xaero/map/render/util/GuiRenderUtil").hasMethod("flushGUI", "()V");
        boolean profiledSurface = worldRender.fieldCount(Opcodes.GETSTATIC, WORLD_OPTIONS,
                "ARROW", BOOL_OPTION) == 1
                && worldRender.sequenceCount(
                new Instruction(Opcodes.GETSTATIC, WORLD_OPTIONS, "ARROW", BOOL_OPTION),
                new Instruction(Opcodes.INVOKEVIRTUAL, CONFIG_MANAGER, "getEffective", GET_EFFECTIVE),
                new Instruction(Opcodes.CHECKCAST, "java/lang/Boolean", "", ""),
                new Instruction(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z")) == 1
                && scanner.read(WORLD_OPTIONS).hasField("ARROW", BOOL_OPTION)
                && scanner.read(CONFIG_MANAGER).hasMethod("getEffective", GET_EFFECTIVE)
                && scanner.read("xaero/lib/client/render/util/GuiRenderUtil")
                .hasMethod("flushGUI", "()V");
        addExclusive(found, legacySurface, WORLD_SURFACE_LEGACY,
                profiledSurface, WORLD_SURFACE_PROFILED);

        MethodInfo changeZoom = world.method("changeZoom", "(DI)V");
        boolean legacyZoom = changeZoom.constantCount(0.0625d) == 2;
        MethodInfo limits = world.method("applyZoomLimits", "()V");
        boolean profiledZoom = changeZoom.callCount(Opcodes.INVOKEVIRTUAL, GUI_MAP,
                "applyZoomLimits", "()V") == 1
                && limits.constantCount(0.0625d) == 1
                && limits.constantCount(0.001953125d) == 1
                && limits.fieldCount(Opcodes.GETSTATIC, WORLD_OPTIONS,
                "UNLIMITED_ZOOM_OUT", BOOL_OPTION) == 1
                && limits.sequenceCount(
                new Instruction(Opcodes.GETSTATIC, WORLD_OPTIONS, "UNLIMITED_ZOOM_OUT", BOOL_OPTION),
                new Instruction(Opcodes.INVOKEVIRTUAL, CONFIG_MANAGER, "getEffective", GET_EFFECTIVE),
                new Instruction(Opcodes.CHECKCAST, "java/lang/Boolean", "", ""),
                new Instruction(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z")) == 1
                && scanner.read(WORLD_OPTIONS).hasField("UNLIMITED_ZOOM_OUT", BOOL_OPTION)
                && scanner.read(CONFIG_MANAGER).hasMethod("getEffective", GET_EFFECTIVE);
        addExclusive(found, legacyZoom, WORLD_ZOOM_LEGACY, profiledZoom, WORLD_ZOOM_PROFILED);

        ClassInfo minimapRenderer = scanner.read(MODULE_RENDERER);
        MethodInfo render = minimapRenderer.method("render", RENDER);
        if (minimapRenderer.hasMethod("render", RENDER)
                && scanner.read(MODULE_CONTEXT)
                .fields("x:I", "y:I", "w:I", "screenWidth:I", "screenHeight:I")
                && scanner.read(MODULE_SESSION).hasMethod("getProcessor",
                "()Lxaero/common/minimap/MinimapProcessor;")
                && scanner.read(PROCESSOR).hasMethod("getMinimapZoom", "()D")) {
            found.add(MINIMAP_RENDER_COMMON);
        }
        boolean depthTrace = render.callCount(Opcodes.INVOKEVIRTUAL, PROCESSOR, "getDepthSkipper",
                "()Lxaero/hud/render/util/GuiDepthSkipper;") == 1;
        boolean pip = render.callCount(Opcodes.INVOKEVIRTUAL,
                "xaero/common/minimap/render/MinimapRenderer", "renderOutsidePip", OUTSIDE_PIP) == 1;
        addExclusive(found, depthTrace, MINIMAP_HOOK_DEPTH_TRACE, pip, MINIMAP_HOOK_PIP);

        ClassInfo hudMod = scanner.read("xaero/common/HudMod");
        boolean legacyShape = hudMod.hasField("INSTANCE", "Lxaero/common/HudMod;")
                && hudMod.hasMethod("getSettings", "()Lxaero/common/settings/ModSettings;")
                && scanner.read("xaero/common/settings/ModSettings").hasField("minimapShape", "I");
        ClassInfo shapeRenderer = scanner.read(MINIMAP_RENDERER);
        MethodInfo shapeRead = shapeRenderer.method("renderMinimap", RENDER_MINIMAP);
        boolean profiledShape = hudMod.hasField("INSTANCE", "Lxaero/common/HudMod;")
                && hudMod.hasMethod("getHudConfigs",
                "()Lxaero/lib/common/config/channel/ConfigChannel;")
                && shapeRenderer.hasField("modMain", "Lxaero/common/HudMod;")
                && shapeRead.sequenceCount(
                new Instruction(Opcodes.GETFIELD, MINIMAP_RENDERER,
                        "modMain", "Lxaero/common/HudMod;"),
                new Instruction(Opcodes.INVOKEVIRTUAL, "xaero/common/HudMod",
                        "getHudConfigs", "()Lxaero/lib/common/config/channel/ConfigChannel;"),
                new Instruction(Opcodes.INVOKEVIRTUAL, CONFIG_CHANNEL,
                        "getClientConfigManager", "()Lxaero/lib/client/config/ClientConfigManager;")) == 1
                && shapeRead.sequenceCount(
                new Instruction(Opcodes.GETSTATIC, MINIMAP_OPTIONS,
                        "SHAPE", "Lxaero/lib/common/config/option/RangeConfigOption;"),
                new Instruction(Opcodes.INVOKEVIRTUAL, CONFIG_MANAGER,
                        "getEffective", GET_EFFECTIVE),
                new Instruction(Opcodes.CHECKCAST, "java/lang/Integer", "", ""),
                new Instruction(Opcodes.INVOKEVIRTUAL, "java/lang/Integer",
                        "intValue", "()I")) == 1
                && scanner.read(MINIMAP_OPTIONS)
                .hasField("SHAPE", "Lxaero/lib/common/config/option/RangeConfigOption;")
                && scanner.read(CONFIG_CHANNEL).hasMethod("getClientConfigManager",
                "()Lxaero/lib/client/config/ClientConfigManager;")
                && scanner.read(CONFIG_MANAGER).hasMethod("getEffective", GET_EFFECTIVE);
        addExclusive(found, legacyShape, MINIMAP_SHAPE_LEGACY,
                profiledShape, MINIMAP_SHAPE_PROFILE);

        MethodInfo fabricHud = scanner.read("xaero/common/events/ModClientEventsFabric")
                .method("register", "()V");
        if (fabricHud.constantCount("xaerohud") == 1 && fabricHud.constantCount("hud") == 1
                && fabricHud.callCount(Opcodes.INVOKESTATIC,
                "net/fabricmc/fabric/api/client/rendering/v1/hud/HudElementRegistry",
                "attachElementAfter", "(Lnet/minecraft/class_2960;Lnet/minecraft/class_2960;"
                        + "Lnet/fabricmc/fabric/api/client/rendering/v1/hud/HudElement;)V") == 1) {
            found.add(MINIMAP_FABRIC_HUD);
        }

        if (hasWaypointWrite(scanner)) found.add(WAYPOINT_WRITE);
        return new XaeroCapabilitySnapshot(found);
    }

    private static boolean hasWaypointWrite(Scanner scanner) {
        return scanner.read("xaero/common/XaeroMinimapSession").methods(
                "getCurrentSession:()Lxaero/common/XaeroMinimapSession;",
                "getMinimapProcessor:()Lxaero/common/minimap/MinimapProcessor;")
                && scanner.read(PROCESSOR).hasMethod("getSession",
                "()Lxaero/hud/minimap/module/MinimapSession;")
                && scanner.read(MODULE_SESSION).methods(
                "getWorldManager:()Lxaero/hud/minimap/world/MinimapWorldManager;",
                "getWorldManagerIO:()Lxaero/hud/minimap/world/io/MinimapWorldManagerIO;")
                && scanner.read("xaero/hud/minimap/world/MinimapWorldManager").hasMethod(
                "getCurrentWorld", "()Lxaero/hud/minimap/world/MinimapWorld;")
                && scanner.read("xaero/hud/minimap/world/MinimapWorld").methods(
                "getCurrentWaypointSet:()Lxaero/hud/minimap/waypoint/set/WaypointSet;",
                "addWaypointSet:(Ljava/lang/String;)V",
                "setCurrentWaypointSetId:(Ljava/lang/String;)V")
                && scanner.read("xaero/common/minimap/waypoints/Waypoint").hasMethod("<init>",
                "(IIILjava/lang/String;Ljava/lang/String;Lxaero/hud/minimap/waypoint/WaypointColor;"
                        + "Lxaero/hud/minimap/waypoint/WaypointPurpose;ZZ)V")
                && scanner.read("xaero/hud/minimap/waypoint/set/WaypointSet").methods(
                "add:(Lxaero/common/minimap/waypoints/Waypoint;)V",
                "remove:(Lxaero/common/minimap/waypoints/Waypoint;)V")
                && scanner.read("xaero/hud/minimap/waypoint/WaypointColor").hasField("AQUA",
                "Lxaero/hud/minimap/waypoint/WaypointColor;")
                && scanner.read("xaero/hud/minimap/waypoint/WaypointPurpose").hasField("NORMAL",
                "Lxaero/hud/minimap/waypoint/WaypointPurpose;")
                && scanner.read("xaero/hud/minimap/world/io/MinimapWorldManagerIO").hasMethod(
                "saveWorld", "(Lxaero/hud/minimap/world/MinimapWorld;)V");
    }

    private static void addExclusive(EnumSet<XaeroCapabilitySnapshot.Capability> found,
                                     boolean first, XaeroCapabilitySnapshot.Capability firstCapability,
                                     boolean second, XaeroCapabilitySnapshot.Capability secondCapability) {
        if (first == second) return;
        found.add(first ? firstCapability : secondCapability);
    }

    private static final class Scanner {
        private final ClassLoader loader;
        private final Map<String, ClassInfo> classes = new HashMap<>();

        private Scanner(ClassLoader loader) {
            this.loader = loader;
        }

        private ClassInfo read(String name) {
            return classes.computeIfAbsent(name, this::readResource);
        }

        private ClassInfo readResource(String name) {
            try (InputStream input = loader.getResourceAsStream(name + ".class")) {
                if (input == null) return ClassInfo.EMPTY;
                ClassInfo info = new ClassInfo();
                new ClassReader(input).accept(info.visitor(),
                        ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return info;
            } catch (IOException | RuntimeException unreadable) {
                return ClassInfo.EMPTY;
            }
        }
    }

    private static final class ClassInfo {
        private static final ClassInfo EMPTY = new ClassInfo();
        private final Set<Member> fields = new HashSet<>();
        private final Map<Member, MethodInfo> methods = new HashMap<>();

        private ClassVisitor visitor() {
            return new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    fields.add(new Member(name, descriptor));
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    MethodInfo info = new MethodInfo();
                    methods.put(new Member(name, descriptor), info);
                    return info.visitor();
                }
            };
        }

        private boolean hasField(String name, String descriptor) {
            return fields.contains(new Member(name, descriptor));
        }

        private boolean hasMethod(String name, String descriptor) {
            return methods.containsKey(new Member(name, descriptor));
        }

        private MethodInfo method(String name, String descriptor) {
            return methods.getOrDefault(new Member(name, descriptor), MethodInfo.EMPTY);
        }

        private boolean fields(String... members) {
            for (String member : members) {
                int split = member.indexOf(':');
                if (!hasField(member.substring(0, split), member.substring(split + 1))) return false;
            }
            return true;
        }

        private boolean methods(String... members) {
            for (String member : members) {
                int split = member.indexOf(':');
                if (!hasMethod(member.substring(0, split), member.substring(split + 1))) return false;
            }
            return true;
        }
    }

    private static final class MethodInfo {
        private static final MethodInfo EMPTY = new MethodInfo();
        private final Map<Instruction, Integer> fields = new HashMap<>();
        private final Map<Instruction, Integer> calls = new HashMap<>();
        private final Map<Object, Integer> constants = new HashMap<>();
        private final List<Instruction> instructions = new ArrayList<>();

        private MethodVisitor visitor() {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitInsn(int opcode) {
                    instructions.add(opcodeOnly(opcode));
                }

                @Override
                public void visitIntInsn(int opcode, int operand) {
                    instructions.add(opcodeOnly(opcode));
                }

                @Override
                public void visitVarInsn(int opcode, int varIndex) {
                    instructions.add(opcodeOnly(opcode));
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                    Instruction instruction = new Instruction(opcode, owner, name, descriptor);
                    fields.merge(instruction, 1, Integer::sum);
                    instructions.add(instruction);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                                            String descriptor, boolean isInterface) {
                    Instruction instruction = new Instruction(opcode, owner, name, descriptor);
                    calls.merge(instruction, 1, Integer::sum);
                    instructions.add(instruction);
                }

                @Override
                public void visitInvokeDynamicInsn(String name, String descriptor,
                                                   Handle bootstrapMethodHandle,
                                                   Object... bootstrapMethodArguments) {
                    instructions.add(opcodeOnly(Opcodes.INVOKEDYNAMIC));
                }

                @Override
                public void visitJumpInsn(int opcode, Label label) {
                    instructions.add(opcodeOnly(opcode));
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    instructions.add(new Instruction(opcode, type, "", ""));
                }

                @Override
                public void visitLdcInsn(Object value) {
                    constants.merge(value, 1, Integer::sum);
                    instructions.add(opcodeOnly(Opcodes.LDC));
                }

                @Override
                public void visitIincInsn(int varIndex, int increment) {
                    instructions.add(opcodeOnly(Opcodes.IINC));
                }

                @Override
                public void visitTableSwitchInsn(int min, int max, Label defaultLabel,
                                                 Label... labels) {
                    instructions.add(opcodeOnly(Opcodes.TABLESWITCH));
                }

                @Override
                public void visitLookupSwitchInsn(Label defaultLabel, int[] keys, Label[] labels) {
                    instructions.add(opcodeOnly(Opcodes.LOOKUPSWITCH));
                }

                @Override
                public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                    instructions.add(opcodeOnly(Opcodes.MULTIANEWARRAY));
                }
            };
        }

        private static Instruction opcodeOnly(int opcode) {
            return new Instruction(opcode, "", "", "");
        }

        private int fieldCount(int opcode, String owner, String name, String descriptor) {
            return fields.getOrDefault(new Instruction(opcode, owner, name, descriptor), 0);
        }

        private int callCount(int opcode, String owner, String name, String descriptor) {
            return calls.getOrDefault(new Instruction(opcode, owner, name, descriptor), 0);
        }

        private int constantCount(Object value) {
            return constants.getOrDefault(value, 0);
        }

        private int sequenceCount(Instruction... sequence) {
            int count = 0;
            for (int i = 0; i <= instructions.size() - sequence.length; i++) {
                if (instructions.subList(i, i + sequence.length).equals(List.of(sequence))) count++;
            }
            return count;
        }
    }

    private record Member(String name, String descriptor) { }
    private record Instruction(int opcode, String owner, String name, String descriptor) { }
}
