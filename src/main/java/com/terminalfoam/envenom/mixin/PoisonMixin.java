package com.terminalfoam.envenom.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 中毒效果改造（@Inject 版，避免 @Overwrite 与其它模组的 mixin 直接冲突）。
 *
 * - 触发间隔：中毒 III（amplifier >= 2）起固定 10 tick；I/II 级保持原版。
 * - 伤害：中毒 V（amplifier >= 4）起线性递增，斜率取 I→V 的平均 DPS 增长，
 *   V 级 10 点/10tick（原版 amp4 DPS 连续），之后每级 +2.4 点：
 *   damage = 10 + 2.4*(amp-4)；III/IV 级（amp 2/3）按原版 DPS 连续（1.667/3.333）。
 * - 不致死：高等级伤害钳制到（当前生命 - 1），保留原版"中毒不致死"语义。
 */
@Mixin(targets = "net.minecraft.world.effect.PoisonMobEffect")
public abstract class PoisonMixin {

    /**
     * 中毒 III（amplifier >= 2）起：触发间隔固定为 10 tick。I/II 级保持原版逻辑。
     */
    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void envenom$shouldApplyEffectTickThisTick(int tickCount, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (amplifier >= 2) {
            cir.setReturnValue(tickCount % 10 == 0);
            return;
        }
        int interval = 25 >> amplifier;
        cir.setReturnValue(tickCount % Math.max(interval, 1) == 0);
    }

    /**
     * 中毒 V（amplifier >= 4）起：单次伤害线性递增；III/IV 按原版 DPS 连续。
     * 单次伤害钳制到（当前生命 - 1），保证"中毒不致死"（最低留 1 血）。
     */
    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void envenom$applyEffectTick(LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (entity.getHealth() > 1.0F) {
            float cap = entity.getHealth() - 1.0F; // 不致死：最高扣到剩 1 血
            if (amplifier >= 4) {
                float damage = Math.min(10.0F + 2.4F * (amplifier - 4), cap);
                entity.hurt(entity.damageSources().magic(), damage);
            } else if (amplifier >= 2) {
                float damage = Math.min(10.0F * (amplifier - 1) / 6.0F, cap);
                entity.hurt(entity.damageSources().magic(), damage);
            } else {
                entity.hurt(entity.damageSources().magic(), 1.0F);
            }
        }
        cir.setReturnValue(true);
    }
}