package com.terminalfoam.envenom;

import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * 26.1 的测试函数注册表在模组加载前就冻结。
 * {@link com.terminalfoam.envenom.mixin.BuiltinTestFunctionsMixin} 在原版 bootstrap 返回前调用这里。
 * 对应的 {@code data/envenom/test_instance/*.json} 用 {@code minecraft:function} 指向这些 id。
 */
public final class EnvenomTestFunctions {
    private EnvenomTestFunctions() {
    }

    public static void bootstrap(Registry<Consumer<GameTestHelper>> registry) {
        register(registry, "witch_menu_charges_and_returns_payment", EnvenomWitchGameTests::witchMenuChargesAndReturnsPayment);
        register(registry, "witch_stock_persists_and_restocks", EnvenomWitchGameTests::witchStockPersistsAndRestocks);
        register(registry, "witch_neutral_and_retaliates", EnvenomWitchGameTests::witchNeutralAndRetaliates);
        register(registry, "poison_intervals", EnvenomGameTests::poisonIntervals);
        register(registry, "regeneration_intervals", EnvenomGameTests::regenerationIntervals);
        register(registry, "poison_damage_vanilla_levels", EnvenomGameTests::poisonDamageVanillaLevels);
        register(registry, "poison_damage_mid_level", EnvenomGameTests::poisonDamageMidLevel);
        register(registry, "poison_damage_high_level_is_clamped", EnvenomGameTests::poisonDamageHighLevelIsClamped);
        register(registry, "regeneration_heals_per_tick", EnvenomGameTests::regenerationHealsPerTick);
        register(registry, "regeneration_does_not_overheal", EnvenomGameTests::regenerationDoesNotOverheal);
        register(registry, "coating_applies_then_triggers_on_attack", EnvenomGameTests::coatingAppliesThenTriggersOnAttack);
        register(registry, "vulnerability_formula", EnvenomContentGameTests::vulnerabilityFormula);
        register(registry, "vulnerability_increases_incoming_damage", EnvenomContentGameTests::vulnerabilityIncreasesIncomingDamage);
        register(registry, "nausea_purges_and_blocks", EnvenomContentGameTests::nauseaPurgesAndBlocks);
        register(registry, "nausea_blocks_eating_and_drinking", EnvenomContentGameTests::nauseaBlocksEatingAndDrinking);
        register(registry, "nausea_interrupt_does_not_consume", EnvenomContentGameTests::nauseaInterruptDoesNotConsume);
        register(registry, "nausea_creative_and_expiry", EnvenomContentGameTests::nauseaCreativeAndExpiry);
        register(registry, "nausea_blocks_cake_not_candles", EnvenomContentGameTests::nauseaBlocksCakeNotCandles);
        register(registry, "swift_as_a_hare_mirrors_turtle_master", EnvenomContentGameTests::swiftAsAHareMirrorsTurtleMaster);
        register(registry, "potion_packs_skip_empty_bases", EnvenomContentGameTests::potionPacksSkipEmptyBases);
        register(registry, "brewing_swift_and_packs", EnvenomContentGameTests::brewingSwiftAndPacks);
    }

    private static void register(Registry<Consumer<GameTestHelper>> registry, String path, Consumer<GameTestHelper> test) {
        Registry.register(registry, Identifier.fromNamespaceAndPath(Envenom.MODID, path), test);
    }
}
