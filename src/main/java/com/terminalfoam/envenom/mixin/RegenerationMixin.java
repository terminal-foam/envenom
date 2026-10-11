package com.terminalfoam.envenom.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 再生效果改造（@Inject 版，避免 @Overwrite 与其它模组的 mixin 直接冲突）。
 *
 * - 触发：再生 V（amplifier >= 4）起每 tick 回血（heal 不走 hurt/无敌帧，安全）；
 *   I-IV 级保持原版逻辑。
 * - 回复量：V 起每 tick = 2^amp / 50（原版理论 DPS 反推），防溢出钳制 amp <= 30。
 */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationMixin {

    /** 防溢出：amplifier >= 31 时 1<<amp 溢为负数，钳制到 30 */
    private static final int MAX_SAFE_AMPLIFIER = 30;

    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void envenom$shouldApplyEffectTickThisTick(int tickCount, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (amplifier >= 4) {
            cir.setReturnValue(true); // 每 tick
            return;
        }
        int interval = 50 >> amplifier;
        cir.setReturnValue(interval > 0 ? tickCount % interval == 0 : true);
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void envenom$applyEffectTick(LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            if (amplifier >= 4) {
                int safeAmp = Math.min(amplifier, MAX_SAFE_AMPLIFIER);
                float amount = (1 << safeAmp) / 50.0F;
                entity.heal(amount);
            } else {
                entity.heal(1.0F);
            }
        }
        cir.setReturnValue(true);
    }
}