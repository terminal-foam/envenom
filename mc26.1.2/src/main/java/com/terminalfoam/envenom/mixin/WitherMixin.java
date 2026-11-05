package com.terminalfoam.envenom.mixin;

import com.terminalfoam.envenom.config.EnvenomConfig;
import com.terminalfoam.envenom.effect.WitherDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 所有凋零等级统一为 {@code effectPulseTicks}（默认 30 tick）一跳，伤害按 DPS 等比缩放。 */
@Mixin(targets = "net.minecraft.world.effect.WitherMobEffect")
public abstract class WitherMixin {
    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void envenom$interval(int tickCount, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(tickCount % EnvenomConfig.effectPulseTicks == 0);
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void envenom$damage(ServerLevel level, LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        int pulse = EnvenomConfig.effectPulseTicks;
        float damage = WitherDamage.calculate(amplifier) * pulse / 10.0F;
        entity.hurtServer(level, entity.damageSources().wither(), damage);
        cir.setReturnValue(true);
    }
}
