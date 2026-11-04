package com.terminalfoam.envenom;

import com.terminalfoam.envenom.handler.MilkHandler;
import com.terminalfoam.envenom.potion.EnvenomPotions;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Envenom.MODID)
@PrefixGameTestTemplate(false)
public final class EnvenomMilkGameTests {
    @GameTest(template = "empty")
    public static void milkHasNoStatusEffect(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.MOB_EFFECT.keySet().stream().noneMatch(id -> id.toString().equals("envenom:milk")),
                "milk must not be a registered status effect");
        var contents = new PotionContents(EnvenomPotions.MILK);
        helper.assertTrue(!contents.hasEffects(), "milk potion must not contain a placeholder effect");
        helper.assertTrue(contents.getColor() == 0xFFFFFFFF, "milk stays opaque white");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void milkSplashClearsEffects(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, 1, 2, 1);
        cow.setNoAi(true);
        cow.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        cow.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 200));
        var bottle = helper.spawn(EntityType.POTION, 1, 2, 1);
        bottle.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, EnvenomPotions.MILK));
        var event = new ProjectileImpactEvent(bottle,
                new BlockHitResult(bottle.position(), Direction.UP, bottle.blockPosition(), false));
        MilkHandler.onImpact(event);
        helper.assertTrue(event.isCanceled() && bottle.isRemoved(), "milk splash completes exactly once");
        helper.assertTrue(cow.getActiveEffects().isEmpty(), "splash clears harmful and beneficial effects");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void milkCloudClearsRepeatedly(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, 1, 2, 1);
        cow.setNoAi(true);
        var cloud = new AreaEffectCloud(helper.getLevel(), cow.getX(), cow.getY(), cow.getZ());
        cloud.setPotionContents(new PotionContents(EnvenomPotions.MILK));
        cloud.setWaitTime(10);
        cloud.setRadius(3.0F);
        cloud.setRadiusOnUse(-0.5F);
        cow.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        cloud.tickCount = 10;
        cloud.tick();
        helper.assertTrue(!cow.hasEffect(MobEffects.POISON), "milk cloud clears without any status effect payload");
        cow.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        cloud.tickCount = 15;
        cloud.tick();
        helper.assertTrue(cow.hasEffect(MobEffects.POISON), "cloud preserves its reapplication delay");
        cloud.tickCount = 30;
        cloud.tick();
        helper.assertTrue(!cow.hasEffect(MobEffects.POISON), "cloud clears newly acquired effects again");
        helper.succeed();
    }
}
