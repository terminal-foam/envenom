package com.terminalfoam.envenom.effect;

import com.terminalfoam.envenom.Envenom;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EnvenomEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Envenom.MODID);

    /** 新「反胃」。原版 minecraft:nausea 只改显示名，ID 与摇晃视觉保持不变。 */
    public static final DeferredHolder<MobEffect, NauseaEffect> NAUSEA =
            EFFECTS.register("nausea", NauseaEffect::new);

    /** 易伤：受到的伤害按等级放大。 */
    public static final DeferredHolder<MobEffect, VulnerabilityEffect> VULNERABILITY =
            EFFECTS.register("vulnerability", VulnerabilityEffect::new);

    private EnvenomEffects() {
    }
}
