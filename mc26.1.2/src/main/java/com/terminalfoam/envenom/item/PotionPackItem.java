package com.terminalfoam.envenom.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

/** 纸包起来的一份药水原材料。和水瓶一起酿，直接得到对应的基础药水。 */
public class PotionPackItem extends Item {
    /** 药包对应的瓶型：决定酿造产物与提示文本。 */
    public enum Container {
        DRINKABLE,
        SPLASH,
        LINGERING
    }

    private final Holder<Potion> potion;
    private final Container container;

    public PotionPackItem(Properties properties, Holder<Potion> potion) {
        this(properties, potion, Container.DRINKABLE);
    }

    public PotionPackItem(Properties properties, Holder<Potion> potion, boolean splash) {
        this(properties, potion, splash ? Container.SPLASH : Container.DRINKABLE);
    }

    public PotionPackItem(Properties properties, Holder<Potion> potion, Container container) {
        super(properties);
        this.potion = potion;
        this.container = container;
    }

    public Holder<Potion> potion() {
        return this.potion;
    }

    public Container container() {
        return this.container;
    }

    public boolean splash() {
        return this.container == Container.SPLASH;
    }

    public boolean lingering() {
        return this.container == Container.LINGERING;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        String path = this.potion.unwrapKey().map(key -> key.identifier().getPath()).orElse("water");
        tooltip.accept(Component.translatable("item.envenom.potion_pack.brew").withStyle(ChatFormatting.GRAY));
        String prefix = switch (this.container) {
            case SPLASH -> "item.minecraft.splash_potion.effect.";
            case LINGERING -> "item.minecraft.lingering_potion.effect.";
            case DRINKABLE -> "item.minecraft.potion.effect.";
        };
        tooltip.accept(Component.translatable(prefix + path).withStyle(ChatFormatting.BLUE));
        PotionContents.addPotionTooltip(this.potion.value().getEffects(), tooltip, 1.0F, context.tickRate());
    }
}

