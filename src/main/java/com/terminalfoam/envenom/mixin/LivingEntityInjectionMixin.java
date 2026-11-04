package com.terminalfoam.envenom.mixin;

import com.terminalfoam.envenom.handler.InjectionTarget;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * A dedicated insertion path for injection coatings. It bypasses canBeAffected,
 * Applicable vetoes, and overrides of addEffect/forceAddEffect in modded entities.
 * Ordinary potions still use the original methods and retain their immunity checks.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityInjectionMixin implements InjectionTarget {
    @Shadow @Final private Map<Holder<MobEffect>, MobEffectInstance> activeEffects;

    @Shadow protected abstract void onEffectAdded(MobEffectInstance effect, @Nullable Entity source);
    @Shadow protected abstract void onEffectUpdated(MobEffectInstance effect, boolean refreshAttributes,
            @Nullable Entity source);

    @Override
    @Unique
    public final void envenom$addInjectedEffect(MobEffectInstance effect, @Nullable Entity source) {
        LivingEntity target = (LivingEntity) (Object) this;
        MobEffectInstance existing = this.activeEffects.get(effect.getEffect());
        NeoForge.EVENT_BUS.post(new MobEffectEvent.Added(target, existing, effect, source));
        // Match vanilla merging and callbacks, including hidden lower-level effects,
        // attribute modifiers, effect-added behavior, and client synchronization.
        if (existing == null) {
            this.activeEffects.put(effect.getEffect(), effect);
            this.onEffectAdded(effect, source);
            effect.onEffectAdded(target);
        } else if (existing.update(effect)) {
            this.onEffectUpdated(existing, true, source);
        }
        effect.onEffectStarted(target);
    }
}
