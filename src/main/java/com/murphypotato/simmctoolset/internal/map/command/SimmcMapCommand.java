package com.murphypotato.simmctoolset.internal.map.command;

import com.murphypotato.simmctoolset.internal.map.SimmcMapClient;
import com.murphypotato.simmctoolset.internal.map.gui.SimmcMapConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Internal command surface; the standalone simmcmap mod owns this name when installed. */
public final class SimmcMapCommand {
    private SimmcMapCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("simmcmap")
                        .then(literal("refresh").executes(context -> {
                            SimmcMapClient.requestRefresh();
                            context.getSource().sendFeedback(Text.translatable("simmc_tool_set.status.refreshed"));
                            return 1;
                        }))
                        .then(literal("toggle").executes(context -> toggle(context, SimmcMapClient::toggleWorldMap)))
                        .then(literal("background").executes(context -> toggle(context, SimmcMapClient::toggleWorldBackground)))
                        .then(literal("minimap").executes(context -> toggle(context, SimmcMapClient::toggleMinimapBackground)))
                        .then(literal("cache").executes(context -> {
                            context.getSource().sendFeedback(Text.translatable("simmc_tool_set.status.cache_ready"));
                            return 1;
                        }))
                        .then(literal("settings").executes(context -> {
                            var client = context.getSource().getClient();
                            client.send(() -> client.setScreen(new SimmcMapConfigScreen(client.currentScreen)));
                            return 1;
                        }))));
    }

    private static int toggle(
            com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> context,
            Runnable action) {
        action.run();
        context.getSource().sendFeedback(Text.translatable("simmc_tool_set.status.toggled"));
        return 1;
    }
}
