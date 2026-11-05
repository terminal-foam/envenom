package com.terminalfoam.envenom.mixin;

import com.terminalfoam.envenom.config.EnvenomConfig;
import com.terminalfoam.envenom.effect.PoisonDamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 中毒效果改造（@Inject 版，避免 @Overwrite 与其它模组的 mixin 直接冲突）。
 *
 * <p>所有等级统一为 {@code effectPulseTicks}（默认 30 tick）一跳，每跳伤害按原 DPS 等比缩放：
 * I/II 沿用原版 DPS（1/25t、1/12t），III 起沿用本 mod 的曲线。伤害仍钳制为（当前生命 - 1），
 * 保留原版“中毒不致死”语义。</p>
 *
 * <p><b>兼容性提示：</b>现在连 I/II 也接管方法体，其它 mod 对
 * {@code PoisonMobEffect.applyEffectTick} 方法体的注入不会再执行。</p>
 */
@Mixin(targets = "net.minecraft.world.effect.PoisonMobEffect")
public abstract class PoisonMixin {

    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void envenom$shouldApplyEffectTickThisTick(int tickCount, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(tickCount % EnvenomConfig.effectPulseTicks == 0);
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void envenom$applyEffectTick(LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (entity.getHealth() > 1.0F) {
            int pulse = EnvenomConfig.effectPulseTicks;
            float rawDamage;
            if (amplifier == 0) {
                rawDamage = 0.04F * pulse;
            } else if (amplifier == 1) {
                rawDamage = pulse / 12.0F;
            } else if (amplifier < 4) {
                rawDamage = (amplifier - 1) * pulse / 6.0F;
            } else {
                rawDamage = (10.0F + 2.4F * (amplifier - 4)) * pulse / 10.0F;
            }
            float damage = Math.min(rawDamage, entity.getHealth() - 1.0F);
            entity.hurt(new PoisonDamageSource(entity.damageSources().magic().typeHolder()), damage);
        }
        cir.setReturnValue(true);
    }
}
