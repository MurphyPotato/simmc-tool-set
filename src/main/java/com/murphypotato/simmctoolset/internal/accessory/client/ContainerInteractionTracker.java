package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.ContainerLocation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class ContainerInteractionTracker {
    private static final long MAX_OPEN_DELAY_TICKS = 60;
    private static final double MAX_DISTANCE_SQUARED = 8.0 * 8.0;

    private final Map<Object, ContainerLocation> boundScreens = new IdentityHashMap<>();
    private Interaction pending;

    public void record(MinecraftClient client, World world, BlockHitResult hit, String serverScope) {
        if (client == null || world == null || hit == null || client.player == null || !world.isClient()) return;
        BlockPos clicked = hit.getBlockPos().toImmutable();
        BlockPos normalized = normalizeContainerPosition(world, clicked);
        BlockState state = world.getBlockState(normalized);
        pending = new Interaction(
            serverScope,
            world.getRegistryKey().getValue().toString(),
            normalized,
            containerType(state.getBlock()),
            world.getTime()
        );
    }

    public Optional<ContainerLocation> consumeForScreen(MinecraftClient client, Object screenIdentity) {
        if (screenIdentity == null) return Optional.empty();
        ContainerLocation existing = boundScreens.get(screenIdentity);
        if (existing != null) return Optional.of(existing);
        if (pending == null || client == null || client.world == null || client.player == null) return Optional.empty();
        long age = client.world.getTime() - pending.tick();
        if (age < 0 || age > MAX_OPEN_DELAY_TICKS
            || !client.world.getRegistryKey().getValue().toString().equals(pending.dimension())
            || client.player.squaredDistanceTo(pending.position().toCenterPos()) > MAX_DISTANCE_SQUARED) {
            pending = null;
            return Optional.empty();
        }
        ContainerLocation location = new ContainerLocation(
            "block",
            pending.serverScope(),
            pending.dimension(),
            pending.position().getX(),
            pending.position().getY(),
            pending.position().getZ(),
            pending.containerType(),
            0,
            pending.containerType() + " (" + pending.position().getX() + ", "
                + pending.position().getY() + ", " + pending.position().getZ() + ")"
        );
        boundScreens.put(screenIdentity, location);
        pending = null;
        return Optional.of(location);
    }

    public void bind(Object screenIdentity, ContainerLocation location) {
        if (screenIdentity != null && location != null) {
            boundScreens.put(screenIdentity, location);
            pending = null;
        }
    }

    public Optional<ContainerLocation> location(Object screenIdentity) {
        return Optional.ofNullable(boundScreens.get(screenIdentity));
    }

    public void clear() {
        pending = null;
        boundScreens.clear();
    }

    static BlockPos normalizeContainerPosition(World world, BlockPos position) {
        BlockState state = world.getBlockState(position);
        if (!(state.getBlock() instanceof ChestBlock) || state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
            return position;
        }
        return Direction.Type.HORIZONTAL.stream()
            .map(position::offset)
            .filter(next -> {
                BlockState neighbor = world.getBlockState(next);
                return neighbor.getBlock() == state.getBlock()
                    && neighbor.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE;
            })
            .min(Comparator.comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ))
            .map(next -> compare(next, position) < 0 ? next : position)
            .orElse(position);
    }

    private static int compare(BlockPos left, BlockPos right) {
        int x = Integer.compare(left.getX(), right.getX());
        if (x != 0) return x;
        int y = Integer.compare(left.getY(), right.getY());
        return y != 0 ? y : Integer.compare(left.getZ(), right.getZ());
    }

    static String containerType(Block block) {
        String path = net.minecraft.registry.Registries.BLOCK.getId(block).getPath();
        if (path.equals("barrel")) return "木桶";
        if (path.equals("chest") || path.equals("trapped_chest")) return "箱子";
        if (path.endsWith("shulker_box")) return "潜影盒";
        return "容器";
    }

    private record Interaction(
        String serverScope,
        String dimension,
        BlockPos position,
        String containerType,
        long tick
    ) {
    }
}
