package com.terminalfoam.envenom.effect;

/** Damage per pulse for wither II and above; amplifier is displayed level minus one. */
public final class WitherDamage {
    private WitherDamage() {}

    public static float calculate(int amplifier) {
        return amplifier < 3 ? 10.0F * amplifier / 6.0F : 10.0F + 2.4F * (amplifier - 3);
    }
}
