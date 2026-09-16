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
            "5. 计划按共享的当前 M 逐批重算；默认自动方案冻结，编辑数量后不会偷偷重新搜索。",
            "6. 确认使用会写入 UUID/北京时间分隔的本地记录；重复确认不会重复计数。使用记录页可查看历史并审计编辑 M。",
            "7. 验证使用未取整的衰减值；界面显示的“约”值可能仍低于目标。该模型不是服务器权威数据。",
            "8. 搜索超时会保留已找到的可执行批次；“预算内未找到”不代表无解。最接近候选只作诊断，不能确认使用。",
            "9. 自动搜索不利用负边际元素抵消杂质。若元素/杂质比例存在冲突，会显示具体原因；无效的旧轮换阈值输入已移除。",
            "键盘：O 打开工具；该按键可在 Minecraft“控制”页面改绑、清除或重置。命令 /aoshuscroll 也可打开。列表支持滚轮、方向键和 Page Up/Page Down。"
        ));
    }
}
