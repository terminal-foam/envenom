package com.terminalfoam.envenom.handler;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Internal entry point supplied to every LivingEntity by LivingEntityInjectionMixin. */
public interface InjectionTarget {
    void envenom$addInjectedEffect(MobEffectInstance effect, @Nullable Entity source);
}
