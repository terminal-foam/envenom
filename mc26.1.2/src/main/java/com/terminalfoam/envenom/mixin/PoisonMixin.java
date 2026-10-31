package com.terminalfoam.envenom.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForgeMod;
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
 *
 * <p>低等级直接 {@code return} 交还原版，只有本 mod 真正改变行为的等级段才接管。
 * 26.1 的伤害类型与原版一致，走 {@code neoforge:poison}（连不上时退回魔法伤害）。</p>
 */
@Mixin(targets = "net.minecraft.world.effect.PoisonMobEffect")
public abstract class PoisonMixin {

    /**
     * 中毒 III（amplifier >= 2）起：触发间隔固定为 10 tick。I/II 级交还原版。
     */
    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void envenom$shouldApplyEffectTickThisTick(int tickCount, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (amplifier < 2) {
            return;
        }
        cir.setReturnValue(tickCount % 10 == 0);
    }

    /**
     * 中毒 III（amplifier >= 2）起：本 mod 接管伤害。
     * I/II 级交还原版。
     */
    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void envenom$applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (amplifier < 2) {
            return;
        }
        if (entity.getHealth() > 1.0F) {
            float cap = entity.getHealth() - 1.0F;
            float damage;
            if (amplifier >= 4) {
                damage = Math.min(10.0F + 2.4F * (amplifier - 4), cap);
            } else {
                damage = Math.min(10.0F * (amplifier - 1) / 6.0F, cap);
            }
            var types = entity.damageSources().damageTypes;
            var type = types.get(NeoForgeMod.POISON_DAMAGE).orElse(types.getOrThrow(DamageTypes.MAGIC));
            entity.hurtServer(level, new DamageSource(type), damage);
        }
        cir.setReturnValue(true);
    }
}
