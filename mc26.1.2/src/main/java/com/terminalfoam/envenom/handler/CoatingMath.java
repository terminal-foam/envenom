package com.terminalfoam.envenom.handler;

/** Arithmetic shared by blade, hilt and armor coatings. */
public final class CoatingMath {
    private CoatingMath() {}

    public static int scaleDuration(int ticks, float power) {
        if (ticks == -1) return -1;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(20L, Math.round((double) ticks * power)));
    }

    public static int multiplyDuration(int ticks, int multiplier) {
        if (ticks == -1) return -1;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) ticks * multiplier));
    }

    public static float instantAmount(int amplifier, float power) {
        // Match regeneration's level cap; Java int shifts wrap at 32 and turn negative at 31.
        return 3.0F * (1 << Math.clamp(amplifier, 0, 30)) * power;
    }
}
