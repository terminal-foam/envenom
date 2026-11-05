package com.terminalfoam.envenom.effect;

/**
 * 每 10 tick 的凋零基础伤害；实际脉冲会再乘以 {@code effectPulseTicks / 10}。
 *
 * <p>按原版比例跟随中毒 DPS：I 级 62.5%、II–IV 级 60%、V 级 50%，
 * VI 级起与中毒拉平（原版两级都封顶 20/s）。</p>
 */
public final class WitherDamage {
    private WitherDamage() {}

    public static float calculate(int amplifier) {
        return switch (amplifier) {
            case 0 -> 0.25F;   // 中毒 I 0.4 的 62.5%
            case 1 -> 0.5F;    // 中毒 II 0.8333 的 60%
            case 2 -> 1.0F;
            case 3 -> 2.0F;
            case 4 -> 5.0F;    // 中毒 V 10.0 的 50%
            default -> 10.0F + 2.4F * (amplifier - 4);
        };
    }
}
