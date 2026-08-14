package com.murphypotato.simmctoolset.internal.map.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

import java.util.List;
import java.util.Objects;

/** One Minecraft GUI element for up to 65,536 consecutive colored rectangles. */
public final class BatchedColoredQuadGuiElementRenderState implements SimpleGuiElementRenderState {
    private static final TextureSetup EMPTY_TEXTURE = TextureSetup.empty();

    private final Matrix3x2f pose;
    private final List<ColoredQuad> quads;
    private final ScreenRect scissorArea;
    private final ScreenRect bounds;

    public BatchedColoredQuadGuiElementRenderState(Matrix3x2f pose, List<ColoredQuad> quads,
                                                   ScreenRect scissorArea) {
        this.pose = new Matrix3x2f(Objects.requireNonNull(pose, "pose"));
        this.quads = List.copyOf(quads);
        if (this.quads.isEmpty() || this.quads.size() > ColoredCommandRenderPlanner.MAX_QUADS_PER_BATCH) {
            throw new IllegalArgumentException("invalid colored batch size");
        }
        ColoredCommandRenderPlanner.checkedVertexCount(this.quads.size());
        this.scissorArea = scissorArea;
        this.bounds = createBounds(this.quads, this.pose, scissorArea);
    }

    @Override
    public void setupVertices(VertexConsumer consumer, float depth) {
        for (ColoredQuad quad : quads) {
            consumer.vertex(pose, quad.left(), quad.top(), depth).color(quad.argb());
            consumer.vertex(pose, quad.left(), quad.bottom(), depth).color(quad.argb());
            consumer.vertex(pose, quad.right(), quad.bottom(), depth).color(quad.argb());
            consumer.vertex(pose, quad.right(), quad.top(), depth).color(quad.argb());
        }
    }

    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return EMPTY_TEXTURE; }
    @Override public ScreenRect scissorArea() { return scissorArea; }
    @Override public ScreenRect bounds() { return bounds; }
    public List<ColoredQuad> quads() { return quads; }

    private static ScreenRect createBounds(List<ColoredQuad> quads, Matrix3x2f pose, ScreenRect scissor) {
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (ColoredQuad quad : quads) {
            left = Math.min(left, quad.left());
            top = Math.min(top, quad.top());
            right = Math.max(right, quad.right());
            bottom = Math.max(bottom, quad.bottom());
        }
        ScreenRect transformed = new ScreenRect(left, top,
                Math.subtractExact(right, left), Math.subtractExact(bottom, top)).transformEachVertex(pose);
        return scissor == null ? transformed : scissor.intersection(transformed);
    }
}
