package com.murphypotato.simmctoolset.internal.scroll.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

public final class HelpScreen extends TextPageScreen {
    public HelpScreen(Screen previous) {
        super(Text.literal("使用说明"), previous, List.of(
            "1. 搜索并选择卷轴，填写目标数量；主材料是否计入总数可切换。",
            "2. 在“材料排除”中取消不想使用的原材料。排除会保存到本地配置，全部卷轴共用。",
            "3. 点击“计算”。目标元素只能多不能缺，所有非目标元素的杂质总和必须小于 8。",
            "4. 每个方案显示单个卷轴辅料、目标数量总材料、实际元素供给、目标溢出和杂质。",
            "5. 连续使用同一材料约 64 次后可能衰减；重置方式未知，轮换建议只用于降低连续使用风险。",
            "键盘：O 打开工具；该按键可在 Minecraft“控制”页面改绑、清除或重置。命令 /aoshuscroll 也可打开。列表支持滚轮、方向键和 Page Up/Page Down。"
        ));
    }
}
