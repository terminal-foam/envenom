package com.terminalfoam.envenom.effect;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/** 反胃的纯规则：施加时清场，以及什么算「进食 / 饮用」。 */
public final class NauseaRules {
    private NauseaRules() {
    }

    /**
     * 立刻清掉身上所有有益效果，并把玩家饱和度打成 0。饥饿值不动。
     * 只在服务端改世界状态；客户端等同步包。
     */
    public static void purgeOnApply(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        List<Holder<MobEffect>> beneficial = new ArrayList<>();
        for (MobEffectInstance active : entity.getActiveEffects()) {
            if (active.getEffect().value().isBeneficial()) {
                beneficial.add(active.getEffect());
            }
        }
        for (Holder<MobEffect> effect : beneficial) {
            entity.removeEffect(effect);
        }
        if (entity instanceof Player player) {
            player.getFoodData().setSaturation(0.0F);
        }
    }

    /**
     * 食物组件、吃/喝动画，或 NeoForge 的 {@code getFoodProperties}。
     * 最后一项接住只重写食物属性、不用原版食物组件和吃喝动画的模组食物。
     * 药水、牛奶、蜜瓶走喝动画；紫颂果、迷之炖菜、蛋糕以外的原版食物走食物组件。
     */
    public static boolean isIngestion(ItemStack stack) {
        return isIngestion(null, stack);
    }

    public static boolean isIngestion(LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        UseAnim animation = stack.getItem().getUseAnimation(stack);
        if (animation == UseAnim.EAT || animation == UseAnim.DRINK) {
            return true;
        }
        if (stack.has(DataComponents.FOOD)) {
            return true;
        }
        return stack.getFoodProperties(entity) != null;
    }
}
