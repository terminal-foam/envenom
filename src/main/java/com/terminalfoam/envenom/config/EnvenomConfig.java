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

    private static final ModConfigSpec.BooleanValue HARMFUL_FOOD_ENABLED =
            BUILDER.comment("有害食物过量是否启用。", "Enable harmful food overload nausea.")
                    .define("harmfulFoodEnabled", true);

    private static final ModConfigSpec.IntValue HARMFUL_FOOD_WINDOW =
            BUILDER.comment("统计窗口（tick）。", "Rolling window in ticks for harmful foods.")
                    .defineInRange("harmfulFoodWindowTicks", 2400, 20, 72000);

    private static final ModConfigSpec.IntValue HARMFUL_FOOD_THRESHOLD =
            BUILDER.comment("窗口内吃几个有害食物触发反胃。", "Harmful foods needed within the window to trigger nausea.")
                    .defineInRange("harmfulFoodThreshold", 7, 1, 64);

    private static final ModConfigSpec.IntValue HARMFUL_FOOD_NAUSEA_TICKS =
            BUILDER.comment("触发后反胃的持续时间（tick）。", "Nausea duration in ticks when triggered.")
                    .defineInRange("harmfulFoodNauseaTicks", 1200, 20, 72000);

    private static final ModConfigSpec.DoubleValue STACKING_RESISTANCE =
            BUILDER.comment("叠层概率公式 p = L / (L + amp * 系数)，越低越容易触发。",
                            "Stacking chance: p = L / (L + amp * factor); lower means easier.")
                    .defineInRange("stackingResistance", 0.75D, 0.0D, 4.0D);

    private static final ModConfigSpec.IntValue EFFECT_PULSE_TICKS =
            BUILDER.comment("中毒/凋零的触发间隔（tick）。伤害按 DPS 等比缩放，默认 30。",
                            "Poison/wither pulse interval in ticks; damage is scaled to keep DPS. Default 30.")
                    .defineInRange("effectPulseTicks", 30, 10, 100);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int defaultUsesNormal;
    public static int defaultUsesSplash;
    public static int defaultUsesLingering;
    /** 配置加载前也按计划书的默认 +20%/级 计算，避免事件早于配置回调。 */
    public static double vulnerabilityPerLevel = 0.2D;
    public static boolean harmfulFoodEnabled = true;
    public static int harmfulFoodWindowTicks = 2400;
    public static int harmfulFoodThreshold = 7;
    public static int harmfulFoodNauseaTicks = 1200;
    public static double stackingResistance = 0.75D;
    public static int effectPulseTicks = 30;

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        defaultUsesNormal = USES_DRINKABLE.get();
        defaultUsesSplash = USES_SPLASH.get();
        defaultUsesLingering = USES_LINGERING.get();
        vulnerabilityPerLevel = VULNERABILITY_PER_LEVEL.get();
        harmfulFoodEnabled = HARMFUL_FOOD_ENABLED.get();
        harmfulFoodWindowTicks = HARMFUL_FOOD_WINDOW.get();
        harmfulFoodThreshold = HARMFUL_FOOD_THRESHOLD.get();
        harmfulFoodNauseaTicks = HARMFUL_FOOD_NAUSEA_TICKS.get();
        stackingResistance = STACKING_RESISTANCE.get();
        effectPulseTicks = EFFECT_PULSE_TICKS.get();
        LOGGER.info("Envenom config loaded: normal={} splash={} lingering={} vulnerabilityPerLevel={} harmfulFood={}/{}/{} stackingResistance={} effectPulseTicks={}",
                defaultUsesNormal, defaultUsesSplash, defaultUsesLingering, vulnerabilityPerLevel,
                harmfulFoodEnabled, harmfulFoodThreshold, harmfulFoodNauseaTicks, stackingResistance, effectPulseTicks);
    }
}
