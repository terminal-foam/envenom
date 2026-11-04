package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.effect.EnvenomEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Applies a captured injection hit without depending on entity classes or the current held item. */
public final class InjectionHandler {
    private InjectionHandler() {}

    public static void apply(LivingEntity target, MobEffectInstance effect, @Nullable Entity source) {
        if (target.level().isClientSide() || !target.isAlive() || target.isRemoved()) return;
        // Injection bypasses potion immunity, but does not cancel Envenom's nausea rules.
        if (effect.getEffect().value().isBeneficial() && target.hasEffect(EnvenomEffects.NAUSEA)) return;
        ((InjectionTarget) target).envenom$addInjectedEffect(effect, source);
    }
}
