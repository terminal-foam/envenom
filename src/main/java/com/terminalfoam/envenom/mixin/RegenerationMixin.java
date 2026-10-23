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
 *
 * <p><b>兼容性修正（2026-10-19，见 docs/backup/mixin-before-compat-fix）</b>：
 * 旧写法在 I-IV 级也取消方法体，等于 {@code @Overwrite}；现在低于 V 直接 {@code return}，
 * 交还原版与其它 mod，只有本 mod 真正改变的等级段才接管。</p>
 */
@Mixin(targets = "net.minecraft.world.effect.RegenerationMobEffect")
public abstract class RegenerationMixin {

    /** 防溢出：amplifier >= 31 时 1<<amp 溢为负数，钳制到 30 */
    private static final int MAX_SAFE_AMPLIFIER = 30;

    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void envenom$shouldApplyEffectTickThisTick(int tickCount, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (amplifier < 4) {
            return; // 原版逻辑（50 >> amp）与其它 mod 的注入保持有效
        }
        cir.setReturnValue(true); // 每 tick
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void envenom$applyEffectTick(LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (amplifier < 4) {
            return; // 交还原版（I-IV 级）
        }
        if (entity.getHealth() < entity.getMaxHealth()) {
            int safeAmp = Math.min(amplifier, MAX_SAFE_AMPLIFIER);
            entity.heal((1 << safeAmp) / 50.0F);
        }
        cir.setReturnValue(true);
    }
}
