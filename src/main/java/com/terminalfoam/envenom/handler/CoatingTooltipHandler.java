package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.Envenom;
import com.terminalfoam.envenom.coating.Coating;
import com.terminalfoam.envenom.coating.CoatingSlot;
import com.terminalfoam.envenom.coating.EnvenomDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

public class CoatingTooltipHandler {

    /**
     * 原版属性 tooltip 的"分组标题"用这个前缀（如 {@code item.modifiers.mainhand} = "When in Main Hand:"）。
     * 展开态下由 {@code ItemStack.addModifierTooltip} 拼出的就是 {@code "item.modifiers." + 槽位名}。
     */
    private static final String MODIFIER_HEADER_PREFIX = "item.modifiers.";

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Coating> coatings = stack.getOrDefault(EnvenomDataComponents.COATINGS, List.of());
        if (coatings.isEmpty()) return;

        List<Component> tooltips = event.getToolTip();

        // C5：插入位置改按【组件 / 翻译键】定位，不再匹配渲染后的文字。
        //
        // 旧写法找的是 text.contains("SHIFT") || text.contains("修饰符")，两个问题：
        //   ① 依赖原版文案与语言的【字面量】—— 换语言、或原版改一次措辞，锚点就失效；
        //   ② 折叠态的"按住 Shift"提示是原版在文本里拼进去的，遍历 tooltip 行抓不到，
        //      于是多数情况下 insertIndex 直接停在末尾（锚点形同虚设）。
        //
        // 新写法看每一行的 contents：若它是 TranslatableContents 且 key 以
        // "item.modifiers." 开头，那就是原版属性分组的标题行，把涂层插在它前面。
        // 折叠态下原版不产出这一行 ⇒ 自然追加到末尾（合理的降级，不是失效）。
        int insertIndex = tooltips.size();
        for (int i = 0; i < tooltips.size(); i++) {
            if (tooltips.get(i).getContents() instanceof TranslatableContents tc
                    && tc.getKey().startsWith(MODIFIER_HEADER_PREFIX)) {
                insertIndex = i;
                break;
            }
        }

        for (int idx = coatings.size() - 1; idx >= 0; idx--) {
            Coating coating = coatings.get(idx);
            Component line = Component.translatable(
                    "tooltip.envenom.coating",
                    coating.effect().value().getDisplayName(),
                    Component.translatable("slot.envenom." + coating.slot().getSerializedName())
                            .withStyle(coating.slot() == CoatingSlot.HILT
                                    ? ChatFormatting.GOLD
                                    : coating.slot() == CoatingSlot.BLADE
                                            ? ChatFormatting.RED
                                            : ChatFormatting.BLUE),
                    coating.amplifier() + 1,
                    coating.remainingUses()
            ).withStyle(ChatFormatting.GRAY);
            tooltips.add(insertIndex, line);
        }
    }
}
