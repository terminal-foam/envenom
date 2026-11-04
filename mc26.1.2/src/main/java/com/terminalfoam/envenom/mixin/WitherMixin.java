package com.terminalfoam.envenom.mixin;

import com.terminalfoam.envenom.effect.WitherDamage;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wither I remains vanilla; higher levels use a lethal, ten-tick damage curve. */
@Mixin(targets = "net.minecraft.world.effect.WitherMobEffect")
public abstract class WitherMixin {
    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void envenom$interval(int tickCount, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (amplifier < 1) return;
        cir.setReturnValue(tickCount % 10 == 0);
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void envenom$damage(ServerLevel level, LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> cir) {
        if (amplifier < 1) return;
        entity.hurtServer(level, entity.damageSources().wither(), WitherDamage.calculate(amplifier));
        cir.setReturnValue(true);
    }
}
