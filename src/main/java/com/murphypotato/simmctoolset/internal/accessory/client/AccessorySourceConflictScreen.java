package com.murphypotato.simmctoolset.internal.accessory.client;

import com.murphypotato.simmctoolset.internal.accessory.domain.AccessoryRecord;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class AccessorySourceConflictScreen extends Screen {
    private static final int PAGE_SIZE = 5;

    private final ClientAccessoryController controller;
    private final ToolSession session;
    private final String reviewId;
    private int page;

    public AccessorySourceConflictScreen(
        ClientAccessoryController controller,
        ToolSession session,
        String reviewId
    ) {
        super(Text.literal("确认同款饰品来源"));
        this.controller = controller;
        this.session = session;
        this.reviewId = reviewId;
    }

    @Override
    protected void init() {
        clearChildren();
        List<AccessoryRecord> candidates = controller.sourceConflictCandidates(reviewId);
        int pages = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        int start = page * PAGE_SIZE;
        int end = Math.min(candidates.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            AccessoryRecord candidate = candidates.get(index);
            int y = 58 + (index - start) * 36;
            addDrawableChild(ButtonWidget.builder(Text.literal("移动此记录"), button -> resolve(candidate.id()))
                .dimensions(width - 104, y, 92, 20).build());
        }
        if (pages > 1) {
            ButtonWidget previous = ButtonWidget.builder(Text.literal("<"), button -> {
                page--;
                clearAndInit();
            }).dimensions(12, height - 52, 28, 20).build();
            previous.active = page > 0;
            addDrawableChild(previous);
            ButtonWidget next = ButtonWidget.builder(Text.literal(">"), button -> {
                page++;
                clearAndInit();
            }).dimensions(44, height - 52, 28, 20).build();
            next.active = page + 1 < pages;
            addDrawableChild(next);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("作为新副本"), button -> resolve(null))
            .dimensions(width / 2 - 104, height - 28, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回复核"), button -> close())
            .dimensions(width / 2 + 4, height - 28, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, UiColors.PRIMARY);
        context.drawCenteredTextWithShadow(
            textRenderer,
            textRenderer.trimToWidth("选择被移动的旧记录；确实是另一件同款时选择“作为新副本”", Math.max(40, width - 20)),
            width / 2,
            30,
            UiColors.WARNING
        );
        List<AccessoryRecord> candidates = controller.sourceConflictCandidates(reviewId);
        int start = Math.min(candidates.size(), page * PAGE_SIZE);
        int end = Math.min(candidates.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            AccessoryRecord candidate = candidates.get(index);
            int y = 58 + (index - start) * 36;
            context.fill(10, y - 4, width - 10, y + 24, index % 2 == 0 ? 0x7A151B23 : 0x7A1B222C);
            String line = candidate.name() + " · " + controller.sourceLabel(candidate);
            context.drawTextWithShadow(
                textRenderer,
                textRenderer.trimToWidth(line, Math.max(40, width - 126)),
                16,
                y + 6,
                UiColors.SECONDARY
            );
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(new AccessoryToolScreen(controller, session));
    }

    private void resolve(String targetId) {
        controller.resolveSourceConflict(reviewId, targetId);
        close();
    }
}
