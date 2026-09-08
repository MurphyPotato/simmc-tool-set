package com.murphypotato.simmctoolset.smoke;

import com.murphypotato.simmctoolset.client.ToolSetSettings;
import com.murphypotato.simmctoolset.internal.simes.ArcaneHudConfig;
import com.murphypotato.simmctoolset.internal.simes.ManaHud;
import com.murphypotato.simmctoolset.internal.simes.SimesArcaneHud;
import com.murphypotato.simmctoolset.internal.simes.SimesFeatureController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Client-only Mana lifecycle checks run inside the isolated local world. */
public final class ManaHudClientChecks {
    private ManaHudClientChecks() {
    }

    public static void run(MinecraftClient client) {
        require(client != null && client.player != null, "Mana checks require a client player");
        try {
            Field activeField = field(SimesFeatureController.class, "active");
            Field configField = field(SimesArcaneHud.class, "config");
            Field settingsField = field(ToolSetSettings.class, "VALUES");
            Field serverInfoField = field(ClientCommonNetworkHandler.class, "serverInfo");
            Field manaField = field(ManaHud.class, "mana");
            Field readyField = field(ManaHud.class, "manaReady");
            Field heldField = field(ManaHud.class, "wandHeld");
            Field cooldownsField = field(SimesArcaneHud.class, "COOLDOWNS");
            Method observeWand = method(ManaHud.class, "observeWand", ItemStack.class);
            Method tick = method(ManaHud.class, "tick", MinecraftClient.class);
            Method reset = method(ManaHud.class, "reset");

            boolean oldActive = activeField.getBoolean(null);
            ArcaneHudConfig oldConfig = (ArcaneHudConfig) configField.get(null);
            Properties settings = (Properties) settingsField.get(null);
            String oldArcaneSetting = settings.getProperty("arcaneHudEnabled");
            Object handler = client.getNetworkHandler();
            require(handler != null, "Mana checks require a client network handler");
            ServerInfo oldServerInfo = (ServerInfo) serverInfoField.get(handler);
            @SuppressWarnings("unchecked")
            Map<String, Object> cooldowns = (Map<String, Object>) cooldownsField.get(null);
            Map<String, Object> oldCooldowns = new LinkedHashMap<>(cooldowns);

            PlayerInventory inventory = client.player.getInventory();
            int oldSlot = inventory.getSelectedSlot();
            ItemStack oldStack = inventory.getStack(oldSlot);
            try {
                serverInfoField.set(handler, new ServerInfo("simmc test", "play.simmc.cn", ServerInfo.ServerType.OTHER));
                activeField.setBoolean(null, true);
                settings.setProperty("arcaneHudEnabled", "true");
                ArcaneHudConfig config = new ArcaneHudConfig();
                config.arcaneEnabled = true;
                config.simesMode = true;
                config.manaHudEnabled = false;
                configField.set(null, config);
                cooldowns.clear();
                reset.invoke(null);

                ItemStack firstWand = wand();
                inventory.setStack(oldSlot, firstWand);
                require(ManaHud.handleExperiencePacket(new ExperienceBarUpdateS2CPacket(0.5f, 0, 0)),
                        "Hidden Mana wand packet was not consumed");
                require(readyField.getBoolean(null), "Hidden Mana packet did not mark Mana ready");
                assertNear(90.0, manaField.getDouble(null), "Hidden Mana packet did not update Mana");

                require(SimesArcaneHud.handleActionBar(Text.literal("Arcane 冷却剩余: 5s")),
                        "Cooldown sample was not accepted");
                require(cooldowns.containsKey("Arcane"), "Cooldown state was not seeded");

                ItemStack replacement = wand();
                inventory.setStack(oldSlot, replacement);
                observeWand.invoke(null, replacement);
                require(heldField.getBoolean(null), "Replacement wand was not observed");
                assertNear(0.0, manaField.getDouble(null), "Same-slot wand replacement retained old Mana");
                require(!readyField.getBoolean(null), "Same-slot wand replacement retained Mana readiness");
                require(cooldowns.containsKey("Arcane"), "Wand switch discarded genuine cooldown state");

                require(ManaHud.handleExperiencePacket(new ExperienceBarUpdateS2CPacket(0.25f, 0, 0)),
                        "Replacement wand packet was not consumed");
                assertNear(45.0, manaField.getDouble(null), "Replacement wand packet did not update Mana");

                inventory.setStack(oldSlot, ItemStack.EMPTY);
                tick.invoke(null, client);
                require(!heldField.getBoolean(null), "No-wand tick left Mana HUD held");
                assertNear(0.0, manaField.getDouble(null), "No-wand tick retained Mana");
                require(!readyField.getBoolean(null), "No-wand tick retained Mana readiness");
                require(cooldowns.containsKey("Arcane"), "No-wand clear discarded genuine cooldown state");

                inventory.setStack(oldSlot, firstWand);
                require(ManaHud.handleExperiencePacket(new ExperienceBarUpdateS2CPacket(0.75f, 0, 0)),
                        "Re-equipped wand packet was not consumed");
                require(cooldowns.containsKey("Arcane"), "Cooldown state changed before world reset");
                ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.invoker().afterWorldChange(client, client.world);
                assertNear(0.0, manaField.getDouble(null), "World change retained Mana");
                require(!heldField.getBoolean(null), "World change retained wand-held state");
                require(!readyField.getBoolean(null), "World change retained Mana readiness");
                require(cooldowns.isEmpty(), "World change did not clear cooldown state");

                inventory.setStack(oldSlot, ItemStack.EMPTY);
                inventory.setStack(oldSlot, ItemStack.EMPTY);
                require(!ManaHud.handleExperiencePacket(new ExperienceBarUpdateS2CPacket(0.5f, 0, 0)),
                        "Non-wand XP packet was consumed as Mana");
            } finally {
                reset.invoke(null);
                cooldowns.clear();
                cooldowns.putAll(oldCooldowns);
                inventory.setStack(oldSlot, oldStack);
                inventory.setSelectedSlot(oldSlot);
                configField.set(null, oldConfig);
                activeField.setBoolean(null, oldActive);
                serverInfoField.set(handler, oldServerInfo);
                if (oldArcaneSetting == null) settings.remove("arcaneHudEnabled");
                else settings.setProperty("arcaneHudEnabled", oldArcaneSetting);
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Mana client check reflection failed", error);
        }
    }

    private static ItemStack wand() {
        ItemStack stack = new ItemStack(Items.BOOK);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("sim_magic:codex_item"));
        return stack;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static void assertNear(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 0.0001, message + ": expected=" + expected + ", actual=" + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
