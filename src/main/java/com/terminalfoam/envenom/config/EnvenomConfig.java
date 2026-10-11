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

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static int defaultUsesNormal;
    public static int defaultUsesSplash;
    public static int defaultUsesLingering;

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        defaultUsesNormal = USES_DRINKABLE.get();
        defaultUsesSplash = USES_SPLASH.get();
        defaultUsesLingering = USES_LINGERING.get();
        LOGGER.info("Envenom config loaded: normal={} splash={} lingering={}",
                defaultUsesNormal, defaultUsesSplash, defaultUsesLingering);
    }
}
