package com.murphypotato.simmctoolset.internal.accessory.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;

public final class AccessoryAboutScreen extends Screen {
    public static final String ISSUES_URL = "https://github.com/MurphyPotato/simmc-tool-set/issues";
    private static final List<String> STATEMENTS = List.of(
        "MurphyPotato 制作的非官方玩家工具，与 simMC、Mojang、Microsoft 无官方关联。",
        "模组不收集、上传或向作者传输个人信息、物品数据、服务器地址、玩家名或 UUID。",
        "模组不包含网络请求、遥测或服务端入口，只读取客户端当前可见物品的名称与 tooltip。",
        "本地文件保存结构化饰品名称/词条、白名单图标组件和可选容器坐标；不保存完整 NBT、自定义名称组件、Lore 或 tooltip。",
        "容器坐标默认开启；服务器范围只保存本机随机盐生成的哈希，可在本页关闭或清除。",
        "点击“问题反馈”会交给系统默认浏览器打开 GitHub；是否发送内容由玩家自行决定。"
    );

    private final ClientAccessoryController controller;
    private final ToolSession session;

    public AccessoryAboutScreen(ClientAccessoryController controller, ToolSession session) {
        super(Text.literal("关于与反馈"));
        this.controller = controller;
        this.session = session;
    }

    @Override
    protected void init() {
        clearChildren();
        int settingsY = height - 54;
        addDrawableChild(ButtonWidget.builder(
            Text.literal("记录容器位置：" + (controller.saveContainerLocations() ? "开" : "关")),
            button -> toggleLocationRecording()
        ).dimensions(width / 2 - 154, settingsY, 150, 20).build());
        ButtonWidget clearLocations = ButtonWidget.builder(Text.literal("清除容器位置记录"), button -> confirmClearLocations())
            .dimensions(width / 2 + 4, settingsY, 150, 20).build();
        clearLocations.active = controller.hasContainerLocations();
        addDrawableChild(clearLocations);

        int y = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("返回工具"), button -> close())
            .dimensions(width / 2 - 104, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("问题反馈"), button ->
            Util.getOperatingSystem().open(ISSUES_URL)
        ).dimensions(width / 2 + 4, y, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, UiColors.BACKGROUND);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, UiColors.PRIMARY);
        context.drawCenteredTextWithShadow(textRenderer, "版本：3.0.0-fabric · Minecraft 1.21.11 · 仅客户端", width / 2, 28, UiColors.MUTED);
        int maxWidth = Math.max(120, width - 40);
        int y = 52;
        for (String statement : STATEMENTS) {
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(statement), maxWidth);
            for (OrderedText line : lines) {
                context.drawTextWithShadow(textRenderer, line, 20, y, UiColors.SECONDARY);
                y += 12;
            }
            y += 5;
        }
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(ISSUES_URL, maxWidth), 20, y, UiColors.ACCENT);
        super.render(context, mouseX, mouseY, delta);
    }

    private void toggleLocationRecording() {
        if (!controller.saveContainerLocations()) {
            controller.setSaveContainerLocations(true);
            clearAndInit();
            return;
        }
        if (client == null) return;
        client.setScreen(new AccessoryConfirmScreen(
            "关闭容器位置记录？",
            List.of("将清除已保存的方块坐标，但不会删除饰品、词条或计算数据。"),
            confirmed -> {
                if (confirmed) controller.setSaveContainerLocations(false);
                if (client != null) client.setScreen(new AccessoryAboutScreen(controller, session));
            }
        ));
    }

    private void confirmClearLocations() {
        if (client == null) return;
        client.setScreen(new AccessoryConfirmScreen(
            "清除容器位置记录？",
            List.of("只清除已保存的方块坐标，不删除饰品库。位置记录开关保持不变。"),
            confirmed -> {
                if (confirmed) controller.clearContainerLocations();
                if (client != null) client.setScreen(new AccessoryAboutScreen(controller, session));
            }
        ));
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(new AccessoryToolScreen(controller, session));
    }
}
