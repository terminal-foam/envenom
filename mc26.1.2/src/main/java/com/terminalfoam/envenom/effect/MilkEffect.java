package com.terminalfoam.envenom.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 牛奶（{@code envenom:milk}）：瞬间清除目标身上的全部状态效果。
 *
 * <p>做成「瞬间效果」而不是自定义物品逻辑，是为了让原版所有投递方式共用同一条路径：
 * 饮用（{@code PotionItem}）、喷溅（{@code ThrownPotion}）、滞留云（{@code AreaEffectCloud}）
 * 都会对瞬间效果调用 {@link #applyInstantenousEffect}，于是牛奶瓶、喷溅型、滞留型
 * 只需要注册药水，行为由原版代码保证。
 */
public class MilkEffect extends MobEffect {
    /** 牛奶白。 */
    public static final int COLOR = 0xF2F2F2;

    public MilkEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }

    @Override
    public boolean isInstantenous() {
        return true;
    }

    @Override
    public void applyInstantenousEffect(ServerLevel level, Entity source, Entity indirectSource, LivingEntity entity, int amplifier, double health) {
        entity.removeAllEffects();
    }

    /** 兜底：被 /effect 或其它模组按普通效果添加时，下一 tick 也清除。 */
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        entity.removeAllEffects();
        return true;
    }
}
