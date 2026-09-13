package com.murphypotato.simmctoolset.smoke;

import com.murphypotato.simmctoolset.client.ToolSetSettings;
import com.murphypotato.simmctoolset.internal.simes.SimesBrewingCookwareHud;
import com.murphypotato.simmctoolset.internal.simes.SimesFeatureController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Exercises the real scanner and transformed renderer in the isolated local world. */
final class CookwareClientChecks {
    static void run(MinecraftClient client) {
        List<ItemDisplayEntity> entities = new ArrayList<>();
        try {
            Field active = field(SimesFeatureController.class, "active");
            Field info = field(ClientCommonNetworkHandler.class, "serverInfo");
            Properties settings = (Properties) field(ToolSetSettings.class, "VALUES").get(null);
            Object handler = client.getNetworkHandler();
            Object oldInfo = info.get(handler);
            boolean oldActive = active.getBoolean(null);
            String oldSetting = settings.getProperty("cookwareEnabled");
            Method scan = SimesBrewingCookwareHud.class.getDeclaredMethod("scanCookers", MinecraftClient.class, long.class);
            scan.setAccessible(true);
            Method setStack = ItemDisplayEntity.class.getDeclaredMethod("setItemStack", ItemStack.class);
            setStack.setAccessible(true);
            try {
                info.set(handler, new ServerInfo("local fixture only", "play.simmc.cn", ServerInfo.ServerType.OTHER));
                active.setBoolean(null, true);
                settings.setProperty("cookwareEnabled", "true");
                for (String type : List.of("skillet", "steamer", "cookware")) {
                    SimesBrewingCookwareHud.reset();
                    ItemDisplayEntity vessel = display(client, entities, 1);
                    ItemDisplayEntity food = display(client, entities, 1);
                    setStack.invoke(vessel, vessel(type, true));
                    setStack.invoke(food, ItemStack.EMPTY);
                    scan.invoke(null, client, System.currentTimeMillis());
                    checkColor(client, vessel, 0);

                    setStack.invoke(food, new ItemStack(Items.PORKCHOP, 3));
                    scan.invoke(null, client, System.currentTimeMillis());
                    checkColor(client, vessel, 0xFF3FA9FF);
                    checkColor(client, food, 0);
                    Map<?, ?> states = (Map<?, ?>) field(SimesBrewingCookwareHud.class, "cookers").get(null);
                    Object cooker = states.get(vessel.getBlockPos());
                    Field contents = field(cooker.getClass(), "contents");
                    require(((List<?>) contents.get(cooker)).size() == 3, "Scanner lost ItemStack count=3");

                    setStack.invoke(vessel, vessel(type, false));
                    scan.invoke(null, client, System.currentTimeMillis());
                    checkColor(client, vessel, 0xFFFFC233);
                    require(!vessel.isGlowing(), "Client outline modified entity glowing flag");

                    ItemDisplayEntity adjacent = display(client, entities, 2);
                    setStack.invoke(adjacent, new ItemStack(Items.CHARCOAL));
                    scan.invoke(null, client, System.currentTimeMillis());
                    checkColor(client, vessel, 0xFFFFC233);
                    checkColor(client, adjacent, 0);

                    setStack.invoke(food, new ItemStack(Items.CHARCOAL));
                    scan.invoke(null, client, System.currentTimeMillis());
                    checkColor(client, vessel, 0xFFFFC233);
                    scan.invoke(null, client, System.currentTimeMillis());
                    int result = type.equals("skillet") ? 0xFFFF4040 : 0xFF45E06F;
                    checkColor(client, vessel, result);
                    scan.invoke(null, client, System.currentTimeMillis());
                    checkColor(client, vessel, result);
                    setStack.invoke(vessel, vessel(type, true));
                    scan.invoke(null, client, System.currentTimeMillis());
                    checkColor(client, vessel, result);

                    settings.setProperty("cookwareEnabled", "false");
                    checkColor(client, vessel, 0);
                    settings.setProperty("cookwareEnabled", "true");
                    checkColor(client, vessel, result);
                    double x = vessel.getX();
                    vessel.setPosition(x + 50, vessel.getY(), vessel.getZ());
                    checkColor(client, vessel, 0);
                    vessel.setPosition(x, vessel.getY(), vessel.getZ());
                    checkColor(client, vessel, result);
                    field(SimesBrewingCookwareHud.class, "outlineWorld").set(null, null);
                    checkColor(client, vessel, 0);
                    scan.invoke(null, client, System.currentTimeMillis());
                    setStack.invoke(food, ItemStack.EMPTY);
                    scan.invoke(null, client, System.currentTimeMillis());
                    checkColor(client, vessel, 0);

                    SimesBrewingCookwareHud.reset();
                    checkColor(client, vessel, 0);
                    for (ItemDisplayEntity entity : entities) client.world.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
                    entities.clear();
                }
            } finally {
                SimesBrewingCookwareHud.reset();
                for (ItemDisplayEntity entity : entities) client.world.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
                info.set(handler, oldInfo);
                active.setBoolean(null, oldActive);
                if (oldSetting == null) settings.remove("cookwareEnabled");
                else settings.setProperty("cookwareEnabled", oldSetting);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cookware client check failed", e);
        }
    }

    private static ItemDisplayEntity display(MinecraftClient client, List<ItemDisplayEntity> entities, int offset) {
        ItemDisplayEntity entity = new ItemDisplayEntity(EntityType.ITEM_DISPLAY, client.world);
        var pos = client.player.getBlockPos().add(offset, 0, 0);
        entity.setPosition(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5);
        client.world.addEntity(entity);
        entities.add(entity);
        return entity;
    }

    private static ItemStack vessel(String type, boolean open) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("smc:kitchenware_2/" + type + (open ? "_open" : "")));
        return stack;
    }

    private static void checkColor(MinecraftClient client, ItemDisplayEntity entity, int expected) throws ReflectiveOperationException {
        // scanCookers normally runs from tick(); direct invocation needs the scan timestamp too.
        field(SimesBrewingCookwareHud.class, "lastScan").setLong(null, System.currentTimeMillis());
        var state = client.getEntityRenderDispatcher().getAndUpdateRenderState(entity, 0.0F);
        require(state.outlineColor == expected,
                "Outline mismatch: expected=" + Integer.toHexString(expected) + ", actual=" + Integer.toHexString(state.outlineColor));
        require(state.hasOutline() == (expected != 0), "Native hasOutline disagrees with cookware state");
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
