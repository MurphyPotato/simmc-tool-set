package com.murphypotato.simmctoolset.internal.scroll.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;

public final class AboutScreen extends TextPageScreen {
    public AboutScreen(Screen previous) {
        super(Text.literal("关于 / 隐私 / 问题反馈"), previous, List.of(
            "MurphyPotato 制作的非官方玩家工具；与 simMC、Mojang、Microsoft 无官方关联。",
            "版本：2.0.0-fabric；Minecraft 1.21.8；仅客户端。",
            "模组运行时不收集、上传或向作者传输个人信息、配方选择、玩家名、UUID、服务器地址、聊天、物品栏、NBT 或设备标识。",
            "模组不主动连接本地或非本地服务器，不包含 HTTP/HTTPS 请求、遥测、更新检查、WebView、React、Capacitor 或浏览器运行时。源码构建可能联网下载公开依赖。",
            "本地只保存排除材料与必要界面设置，路径为 .minecraft/config/simmc-arcane-scroll-calculator/settings-v1.1.1-fabric.json。",
            "只有主动点击下方“问题反馈”时，才会交给系统默认浏览器打开 GitHub Issues；模组不会自动打开浏览器。"
        ));
    }

    @Override
    protected void init() {
        super.init();
        int gap = 4;
        int buttonWidth = Math.max(60, Math.min(100, (width - 24 - gap) / 2));
        int left = Math.max(4, (width - buttonWidth * 2 - gap) / 2);
        backButton().setDimensionsAndPosition(buttonWidth, 20, left, bottomButtonY());
        addDrawableChild(ButtonWidget.builder(Text.literal("问题反馈"), button ->
            Util.getOperatingSystem().open(ModConstants.ISSUES_URL)
        ).dimensions(left + buttonWidth + gap, bottomButtonY(), buttonWidth, 20).build());
    }
}
