package com.terminalfoam.envenom.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 反胃（{@code envenom:nausea}）。
 *
 * <p>施加瞬间的清除在 {@link #onEffectAdded} 里做；持续期间的禁食、禁饮、拒绝新有益效果
 * 在 {@code EnvenomEffectHandler} 里做，这样喷溅、口令、信标都走同一条路径。
 */
public class NauseaEffect extends MobEffect {
    /** 胃部不适的黄绿色粒子。 */
    public static final int COLOR = 0x6B8F2A;

    public NauseaEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        NauseaRules.purgeOnApply(entity);
    }
}
