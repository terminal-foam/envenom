package com.terminalfoam.envenom.effect;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** Identifies our high-level poison pulses without changing their registered damage type. */
public final class PoisonDamageSource extends DamageSource {
    public PoisonDamageSource(Holder<DamageType> type) {
        super(type);
    }
}
