package com.terminalfoam.envenom.config;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

public class EnvenomConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue USES_DRINKABLE =
            BUILDER.comment("Default coating uses for normal drinkable potions")
                    .defineInRange("usesDrinkable", 300, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue USES_SPLASH =
            BUILDER.comment("Default coating uses for splash potions")
                    .defineInRange("usesSplash", 210, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue USES_LINGERING =
            BUILDER.comment("Default coating uses for lingering potions")
                    .defineInRange("usesLingering", 210, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.DoubleValue VULNERABILITY_PER_LEVEL =
            BUILDER.comment(
                            "易伤每级追加的受伤倍率。I 级（amplifier 0）加一份，II 级加两份。",
                            "最终伤害 = 原始伤害 * (1 + vulnerabilityPerLevel * (amplifier + 1))。",
                            "默认 0.2 表示每级 +20%。",
                            "Incoming-damage bonus per Vulnerability level. Level I adds this once, level II twice.",
                            "Final damage = incoming * (1 + vulnerabilityPerLevel * (amplifier + 1)). Default 0.2 = +20% per level."
                    )
                    .defineInRange("vulnerabilityPerLevel", 0.2D, 0.0D, 100.0D);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int defaultUsesNormal;
    public static int defaultUsesSplash;
    public static int defaultUsesLingering;
    /** 配置加载前也按计划书的默认 +20%/级 计算，避免事件早于配置回调。 */
    public static double vulnerabilityPerLevel = 0.2D;

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        defaultUsesNormal = USES_DRINKABLE.get();
        defaultUsesSplash = USES_SPLASH.get();
        defaultUsesLingering = USES_LINGERING.get();
        vulnerabilityPerLevel = VULNERABILITY_PER_LEVEL.get();
        LOGGER.info("Envenom config loaded: normal={} splash={} lingering={} vulnerabilityPerLevel={}",
                defaultUsesNormal, defaultUsesSplash, defaultUsesLingering, vulnerabilityPerLevel);
    }
}
