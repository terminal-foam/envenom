package com.terminalfoam.envenom;

import com.terminalfoam.envenom.effect.EnvenomEffects;
import com.terminalfoam.envenom.handler.CoatingEffectHandler;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** Synthetic modded mobs exercise immunity mechanisms without a mod whitelist. */
public final class EnvenomInjectionGameTests {
    private EnvenomInjectionGameTests() {}

    public static void injectionBypassesEntityOverrides(GameTestHelper helper) {
        // One mob rejects canBeAffected; the other also overrides both insertion methods.
        Mob checkImmune = new Mob(EntityType.ZOMBIE, helper.getLevel()) {
            @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
        };
        for (Mob target : new Mob[]{checkImmune, hardImmuneMob(helper)}) {
            var source = helper.makeMockServerPlayerInLevel();
            helper.assertTrue(source.getMainHandItem().isEmpty(), "fixture models switching away after an injection hit");
            apply(target, new MobEffectInstance(MobEffects.POISON, 200), source, false);
            helper.assertTrue(!target.hasEffect(MobEffects.POISON), "ordinary coatings must respect modded immunity");
            apply(target, new MobEffectInstance(MobEffects.POISON, 200), source, true);
            helper.assertTrue(target.hasEffect(MobEffects.POISON), "injection must bypass modded rejection methods");
            target.removeEffect(MobEffects.POISON);
            apply(target, new MobEffectInstance(MobEffects.POISON, 200), source, false);
            helper.assertTrue(!target.hasEffect(MobEffects.POISON), "injection must not permanently disable immunity");
        }
        var silverfish = helper.spawn(EntityType.SILVERFISH, 1, 2, 1);
        silverfish.setNoAi(true);
        apply(silverfish, new MobEffectInstance(MobEffects.INFESTED, 200), null, false);
        helper.assertTrue(!silverfish.hasEffect(MobEffects.INFESTED), "fixture must have vanilla tag immunity");
        apply(silverfish, new MobEffectInstance(MobEffects.INFESTED, 200), null, true);
        helper.assertTrue(silverfish.hasEffect(MobEffects.INFESTED), "injection must also bypass tag immunity");
        helper.succeed();
    }

    public static void injectionBypassesApplicableVeto(GameTestHelper helper) {
        var target = helper.spawn(EntityType.COW, 1, 2, 1);
        var blocker = new RejectEffects(target);
        NeoForge.EVENT_BUS.register(blocker);
        try {
            apply(target, new MobEffectInstance(MobEffects.POISON, 200), null, false);
            helper.assertTrue(!target.hasEffect(MobEffects.POISON), "ordinary effects must respect the event veto");
            apply(target, new MobEffectInstance(MobEffects.POISON, 200), null, true);
            helper.assertTrue(target.hasEffect(MobEffects.POISON), "injection must bypass event-based potion immunity");
            target.removeEffect(MobEffects.POISON);
            apply(target, new MobEffectInstance(MobEffects.POISON, 200), null, false);
            helper.assertTrue(!target.hasEffect(MobEffects.POISON), "event immunity still applies after injection");
        } finally {
            NeoForge.EVENT_BUS.unregister(blocker);
        }
        helper.succeed();
    }

    public static void injectionPreservesAttributesAndHiddenEffects(GameTestHelper helper) {
        Mob target = hardImmuneMob(helper);
        double baseSpeed = target.getAttributeValue(Attributes.MOVEMENT_SPEED);
        apply(target, new MobEffectInstance(MobEffects.SPEED, 200, 0), null, true);
        double speedOne = target.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(speedOne > baseSpeed, "injection must install attribute modifiers");
        apply(target, new MobEffectInstance(MobEffects.SPEED, 200, 1), null, true);
        helper.assertTrue(target.getAttributeValue(Attributes.MOVEMENT_SPEED) > speedOne,
                "upgrading injection must refresh attribute modifiers");
        target.removeEffect(MobEffects.SPEED);
        helper.assertTrue(Math.abs(target.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 0.00001,
                "removing an injected effect must clean up attributes");

        apply(target, new MobEffectInstance(MobEffects.POISON, 2, 1), null, true);
        apply(target, new MobEffectInstance(MobEffects.POISON, 200, 0), null, true);
        MobEffectInstance poison = target.getEffect(MobEffects.POISON);
        helper.assertTrue(poison.getAmplifier() == 1, "a weaker injection must not downgrade the active effect");
        for (int tick = 0; tick < 3; tick++) poison.tickServer(helper.getLevel(), target, () -> {});
        helper.assertTrue(poison.getAmplifier() == 0 && poison.getDuration() > 0,
                "the longer weaker injection must survive as a hidden effect after the stronger one expires");
        helper.succeed();
    }

    public static void injectionRespectsNauseaAndOrdinaryEffects(GameTestHelper helper) {
        Mob target = hardImmuneMob(helper);
        apply(target, new MobEffectInstance(MobEffects.NIGHT_VISION, 200), null, true);
        helper.assertTrue(target.hasEffect(MobEffects.NIGHT_VISION), "fixture receives beneficial injection");
        apply(target, new MobEffectInstance(EnvenomEffects.NAUSEA, 200), null, true);
        helper.assertTrue(target.hasEffect(EnvenomEffects.NAUSEA), "injection must accept modded potion effects");
        helper.assertTrue(!target.hasEffect(MobEffects.NIGHT_VISION), "effect-added behavior must purge beneficial effects");
        apply(target, new MobEffectInstance(MobEffects.NIGHT_VISION, 200), null, true);
        helper.assertTrue(!target.hasEffect(MobEffects.NIGHT_VISION), "injection must retain nausea's beneficial-effect ban");
        apply(target, new MobEffectInstance(MobEffects.POISON, 200), null, true);
        helper.assertTrue(target.hasEffect(MobEffects.POISON), "nausea must not block harmful injections");
        var cow = helper.spawn(EntityType.COW, 1, 2, 1);
        apply(cow, new MobEffectInstance(MobEffects.NIGHT_VISION, 200), null, false);
        helper.assertTrue(cow.hasEffect(MobEffects.NIGHT_VISION), "ordinary potion application must keep working");
        helper.succeed();
    }

    private static Mob hardImmuneMob(GameTestHelper helper) {
        return new Mob(EntityType.ZOMBIE, helper.getLevel()) {
            @Override public boolean canBeAffected(MobEffectInstance effect) { return false; }
            @Override public boolean addEffect(MobEffectInstance effect, Entity source) { return false; }
            @Override public void forceAddEffect(MobEffectInstance effect, Entity source) {}
        };
    }

    private static void apply(LivingEntity target, MobEffectInstance effect, Entity source, boolean injection) {
        try {
            var method = CoatingEffectHandler.class.getDeclaredMethod("addEffectOrInject",
                    LivingEntity.class, MobEffectInstance.class, Entity.class, boolean.class);
            method.setAccessible(true);
            method.invoke(null, target, effect, source, injection);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot exercise coating injection", e);
        }
    }

    public static final class RejectEffects {
        private final LivingEntity target;
        private RejectEffects(LivingEntity target) { this.target = target; }
        @SubscribeEvent
        public void onApplicable(MobEffectEvent.Applicable event) {
            if (event.getEntity() == target) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
