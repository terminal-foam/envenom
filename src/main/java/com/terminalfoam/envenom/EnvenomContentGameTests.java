package com.terminalfoam.envenom;

import com.terminalfoam.envenom.config.EnvenomConfig;
import com.terminalfoam.envenom.effect.EnvenomEffects;
import com.terminalfoam.envenom.effect.NauseaRules;
import com.terminalfoam.envenom.effect.VulnerabilityMath;
import com.terminalfoam.envenom.item.EnvenomItems;
import com.terminalfoam.envenom.item.PotionPackItem;
import com.terminalfoam.envenom.potion.EnvenomPotions;
import java.lang.reflect.Method;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.1 内容：反胃、易伤、动若脱兔、药包，以及审计里补上的边界。
 * 涂层和原版毒/再生的旧测试仍在 {@link EnvenomGameTests}。
 */
@GameTestHolder(Envenom.MODID)
@PrefixGameTestTemplate(false)
public final class EnvenomContentGameTests {
    private static final float EPS = 1.0E-3F;

    private EnvenomContentGameTests() {
    }

    @GameTest(template = "empty")
    public static void vulnerabilityFormula(GameTestHelper helper) {
        helper.assertTrue(Math.abs(EnvenomConfig.vulnerabilityPerLevel - 0.2D) < 1.0E-6D,
                "default vulnerability bonus should be 0.2, got " + EnvenomConfig.vulnerabilityPerLevel);
        helper.assertTrue(Math.abs(VulnerabilityMath.scaleDamage(10.0F, 0, 0.2D) - 12.0F) < EPS, "level I is +20%");
        helper.assertTrue(Math.abs(VulnerabilityMath.scaleDamage(10.0F, 1, 0.2D) - 14.0F) < EPS, "level II is +40%");
        helper.assertTrue(Math.abs(VulnerabilityMath.scaleDamage(10.0F, 4, 0.2D) - 20.0F) < EPS, "level V is +100%");
        helper.assertTrue(VulnerabilityMath.scaleDamage(0.0F, 0, 0.2D) == 0.0F, "zero damage stays zero");
        helper.assertTrue(VulnerabilityMath.scaleDamage(-2.0F, 0, 0.2D) == -2.0F, "negative damage is unchanged");
        float extreme = VulnerabilityMath.scaleDamage(10.0F, Integer.MAX_VALUE, 0.2D);
        helper.assertTrue(Float.isFinite(extreme) && extreme > 10.0F,
                "amplifier Integer.MAX_VALUE must stay a damage increase, got " + extreme);
        helper.assertTrue(VulnerabilityMath.scaleDamage(10.0F, 0, Double.NaN) == 10.0F, "NaN config must not poison the damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vulnerabilityIncreasesIncomingDamage(GameTestHelper helper) {
        Cow plain = toughCow(helper);
        plain.hurt(plain.damageSources().magic(), 10.0F);
        float plainLost = 40.0F - plain.getHealth();

        Cow marked = toughCow(helper);
        marked.addEffect(new MobEffectInstance(EnvenomEffects.VULNERABILITY, 200, 0));
        marked.hurt(marked.damageSources().magic(), 10.0F);
        float markedLost = 40.0F - marked.getHealth();

        float expected = VulnerabilityMath.scaleDamage(plainLost, 0, EnvenomConfig.vulnerabilityPerLevel);
        helper.assertTrue(Math.abs(markedLost - expected) < 0.05F,
                "vulnerability I should scale " + plainLost + " to " + expected + ", got " + markedLost);

        Cow markedTwo = toughCow(helper);
        markedTwo.addEffect(new MobEffectInstance(EnvenomEffects.VULNERABILITY, 200, 1));
        markedTwo.hurt(markedTwo.damageSources().magic(), 10.0F);
        float twoLost = 40.0F - markedTwo.getHealth();
        float expectedTwo = VulnerabilityMath.scaleDamage(plainLost, 1, EnvenomConfig.vulnerabilityPerLevel);
        helper.assertTrue(Math.abs(twoLost - expectedTwo) < 0.05F,
                "vulnerability II should scale to " + expectedTwo + ", got " + twoLost);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nauseaPurgesAndBlocks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0));
        int food = player.getFoodData().getFoodLevel();
        player.getFoodData().setSaturation(7.0F);

        helper.assertTrue(player.addEffect(new MobEffectInstance(EnvenomEffects.NAUSEA, 200, 0)),
                "nausea itself should apply");
        helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED), "beneficial speed should be cleared");
        helper.assertTrue(player.hasEffect(MobEffects.POISON), "harmful poison should stay");
        helper.assertTrue(player.hasEffect(MobEffects.GLOWING), "neutral glowing should stay");
        helper.assertTrue(player.hasEffect(EnvenomEffects.NAUSEA), "nausea should remain");
        helper.assertTrue(Math.abs(player.getFoodData().getSaturationLevel()) < EPS, "saturation should be 0");
        helper.assertTrue(player.getFoodData().getFoodLevel() == food, "hunger must not change");

        helper.assertTrue(!player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0)),
                "new beneficial effects should be rejected");
        helper.assertTrue(!player.hasEffect(MobEffects.REGENERATION), "regeneration should not stick");
        helper.assertTrue(!player.addEffect(new MobEffectInstance(MobEffects.HEAL, 1, 0)),
                "instant health from a command, beacon, or splash potion should be rejected");
        helper.assertTrue(!player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0)),
                "conduit power should be rejected");
        helper.assertTrue(!player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)),
                "absorption should be rejected");
        helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0)),
                "harmful effects should still apply");

        player.removeEffect(EnvenomEffects.NAUSEA);
        helper.assertTrue(player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0)),
                "beneficial effects apply again once nausea expires");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nauseaBlocksEatingAndDrinking(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(EnvenomEffects.NAUSEA, 200, 0));

        ItemStack bread = new ItemStack(Items.BREAD);
        ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
        ItemStack honey = new ItemStack(Items.HONEY_BOTTLE);
        ItemStack milk = new ItemStack(Items.MILK_BUCKET);
        ItemStack shield = new ItemStack(Items.SHIELD);

        helper.assertTrue(NauseaRules.isIngestion(bread), "bread is food");
        helper.assertTrue(NauseaRules.isIngestion(potion), "potion is a drink");
        helper.assertTrue(NauseaRules.isIngestion(honey), "honey bottle is ingestion");
        helper.assertTrue(NauseaRules.isIngestion(milk), "milk is a drink");
        helper.assertTrue(!NauseaRules.isIngestion(shield), "shield is not ingestion");

        helper.assertTrue(canceled(player, bread), "bread use should cancel");
        helper.assertTrue(canceled(player, potion), "potion drink should cancel");
        helper.assertTrue(canceled(player, honey), "honey drink should cancel");
        helper.assertTrue(canceled(player, milk), "milk drink should cancel");
        helper.assertTrue(!canceled(player, shield), "shield use should be allowed");

        player.removeEffect(EnvenomEffects.NAUSEA);
        helper.assertTrue(!canceled(player, bread), "eating resumes after nausea ends");

        ItemStack chorus = new ItemStack(Items.CHORUS_FRUIT);
        ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW);
        helper.assertTrue(NauseaRules.isIngestion(chorus), "chorus fruit is food");
        helper.assertTrue(NauseaRules.isIngestion(stew), "suspicious stew is food");
        player.addEffect(new MobEffectInstance(EnvenomEffects.NAUSEA, 200, 0));
        helper.assertTrue(canceled(player, chorus), "chorus fruit should cancel");
        helper.assertTrue(canceled(player, stew), "suspicious stew should cancel");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nauseaInterruptDoesNotConsume(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0.0F);

        assertInterruptKeeps(helper, player, new ItemStack(Items.BREAD), Items.BREAD);
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        assertInterruptKeeps(helper, player, new ItemStack(Items.CHORUS_FRUIT), Items.CHORUS_FRUIT);
        helper.assertTrue(player.position().distanceToSqr(x, y, z) < 0.01D, "interrupted chorus fruit must not teleport");
        assertInterruptKeeps(helper, player, new ItemStack(Items.SUSPICIOUS_STEW), Items.SUSPICIOUS_STEW);
        assertInterruptKeeps(helper, player, new ItemStack(Items.HONEY_BOTTLE), Items.HONEY_BOTTLE);
        assertInterruptKeeps(helper, player, new ItemStack(Items.MILK_BUCKET), Items.MILK_BUCKET);
        assertInterruptKeeps(helper, player, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), Items.POTION);

        helper.assertTrue(player.getFoodData().getFoodLevel() == 10, "hunger must stay 10 when the bite is interrupted");
        helper.assertTrue(player.hasEffect(EnvenomEffects.NAUSEA), "milk must not be drunk, so nausea stays");
        helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED), "the swiftness potion must not be drunk");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nauseaCreativeAndExpiry(GameTestHelper helper) {
        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        creative.setGameMode(GameType.CREATIVE);
        creative.getFoodData().setFoodLevel(20);
        creative.addEffect(new MobEffectInstance(EnvenomEffects.NAUSEA, 200, 0));
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
        creative.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(!creative.isUsingItem(), "creative mode must not start eating while nauseous");
        helper.assertTrue(creative.getMainHandItem().is(Items.BREAD) && creative.getMainHandItem().getCount() == 1,
                "creative bread must not be consumed");
        helper.assertTrue(creative.getFoodData().getFoodLevel() == 20, "creative hunger stays full");

        ServerPlayer survivor = helper.makeMockServerPlayerInLevel();
        survivor.setGameMode(GameType.SURVIVAL);
        survivor.getFoodData().setFoodLevel(10);
        survivor.addEffect(new MobEffectInstance(EnvenomEffects.NAUSEA, 1, 0));
        helper.assertTrue(survivor.hasEffect(EnvenomEffects.NAUSEA), "nausea should be active before it expires");
        for (int i = 0; i < 5 && survivor.hasEffect(EnvenomEffects.NAUSEA); i++) {
            tickEffects(survivor);
        }
        helper.assertTrue(!survivor.hasEffect(EnvenomEffects.NAUSEA), "a 1-tick nausea should expire");
        survivor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
        survivor.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(survivor.isUsingItem(), "eating should start again after nausea expires");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nauseaBlocksCakeNotCandles(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(10);
        BlockPos relative = new BlockPos(1, 2, 1);
        helper.setBlock(relative, Blocks.CAKE.defaultBlockState());

        useBlock(player, helper, relative);
        helper.assertTrue(helper.getBlockState(relative).getValue(CakeBlock.BITES) == 1, "a hungry player should eat cake");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 12, "cake should restore 2 hunger");

        helper.setBlock(relative, Blocks.CAKE.defaultBlockState());
        player.getFoodData().setFoodLevel(10);
        player.addEffect(new MobEffectInstance(EnvenomEffects.NAUSEA, 200, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        useBlock(player, helper, relative);
        helper.assertTrue(helper.getBlockState(relative).getValue(CakeBlock.BITES) == 0, "nausea should block eating cake");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 10, "blocked cake must not feed the player");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CANDLE));
        useBlock(player, helper, relative);
        helper.assertTrue(helper.getBlockState(relative).getBlock() instanceof CandleCakeBlock,
                "nausea should still allow placing a candle on a cake");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        useBlock(player, helper, relative);
        helper.assertTrue(helper.getBlockState(relative).getBlock() instanceof CandleCakeBlock,
                "nausea should block eating a candle cake");
        helper.assertTrue(player.getFoodData().getFoodLevel() == 10, "candle cake must not feed the player");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void swiftAsAHareMirrorsTurtleMaster(GameTestHelper helper) {
        assertPair(helper, EnvenomPotions.SWIFT_AS_A_HARE.get(), EnvenomPotions.BASE_DURATION, 3, 2);
        assertPair(helper, EnvenomPotions.LONG_SWIFT_AS_A_HARE.get(), EnvenomPotions.LONG_DURATION, 3, 2);
        assertPair(helper, EnvenomPotions.STRONG_SWIFT_AS_A_HARE.get(), EnvenomPotions.BASE_DURATION, 5, 3);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void potionPacksSkipEmptyBases(GameTestHelper helper) {
        helper.assertTrue(EnvenomItems.packs().size() == 16, "expected 16 potion packs, got " + EnvenomItems.packs().size());
        for (PotionPackItem pack : EnvenomItems.packs()) {
            Potion potion = pack.potion().value();
            helper.assertTrue(!potion.getEffects().isEmpty(),
                    BuiltInRegistries.ITEM.getKey(pack) + " must brew a potion that has effects");
            helper.assertTrue(potion != Potions.AWKWARD.value()
                            && potion != Potions.MUNDANE.value()
                            && potion != Potions.THICK.value()
                            && potion != Potions.WATER.value(),
                    "pack must not target awkward, mundane, thick, or water");
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(pack);
            boolean recipe = helper.getLevel().getRecipeManager().getRecipes().stream()
                    .anyMatch(holder -> holder.id().equals(id));
            helper.assertTrue(recipe, "missing crafting recipe for " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void brewingSwiftAndPacks(GameTestHelper helper) {
        PotionBrewing brewing = helper.getLevel().potionBrewing();
        ItemStack eye = new ItemStack(Items.FERMENTED_SPIDER_EYE);

        helper.assertTrue(isPotion(brewing.mix(eye, bottle(Potions.TURTLE_MASTER)), EnvenomPotions.SWIFT_AS_A_HARE),
                "turtle master + fermented spider eye should brew swift as a hare");
        helper.assertTrue(isPotion(brewing.mix(eye, bottle(Potions.LONG_TURTLE_MASTER)), EnvenomPotions.LONG_SWIFT_AS_A_HARE),
                "long turtle master should corrupt into the long potion");
        helper.assertTrue(isPotion(brewing.mix(eye, bottle(Potions.STRONG_TURTLE_MASTER)), EnvenomPotions.STRONG_SWIFT_AS_A_HARE),
                "strong turtle master should corrupt into the strong potion");
        helper.assertTrue(isPotion(
                brewing.mix(new ItemStack(Items.REDSTONE), bottle(EnvenomPotions.SWIFT_AS_A_HARE)),
                EnvenomPotions.LONG_SWIFT_AS_A_HARE),
                "redstone should extend swift as a hare");
        helper.assertTrue(isPotion(
                brewing.mix(new ItemStack(Items.GLOWSTONE_DUST), bottle(EnvenomPotions.SWIFT_AS_A_HARE)),
                EnvenomPotions.STRONG_SWIFT_AS_A_HARE),
                "glowstone should strengthen swift as a hare");
        ItemStack splash = brewing.mix(new ItemStack(Items.GUNPOWDER), bottle(EnvenomPotions.SWIFT_AS_A_HARE));
        helper.assertTrue(splash.is(Items.SPLASH_POTION) && isPotion(splash, EnvenomPotions.SWIFT_AS_A_HARE),
                "gunpowder should make a splash potion of swift as a hare");
        ItemStack lingering = brewing.mix(new ItemStack(Items.DRAGON_BREATH), splash.copy());
        helper.assertTrue(lingering.is(Items.LINGERING_POTION) && isPotion(lingering, EnvenomPotions.SWIFT_AS_A_HARE),
                "dragon's breath should make a lingering potion of swift as a hare");

        ItemStack water = bottle(Potions.WATER);
        helper.assertTrue(isPotion(brewing.mix(new ItemStack(EnvenomItems.SWIFTNESS_PACK.get()), water), Potions.SWIFTNESS),
                "water + swiftness pack should brew swiftness");
        helper.assertTrue(isPotion(brewing.mix(new ItemStack(EnvenomItems.TURTLE_MASTER_PACK.get()), water), Potions.TURTLE_MASTER),
                "water + turtle master pack should brew turtle master");
        helper.assertTrue(isPotion(brewing.mix(new ItemStack(EnvenomItems.INFESTED_PACK.get()), water), Potions.INFESTED),
                "water + infested pack should brew infestation");
        helper.assertTrue(isPotion(brewing.mix(new ItemStack(EnvenomItems.WEAKNESS_PACK.get()), water), Potions.WEAKNESS),
                "water + weakness pack should brew weakness");
        helper.succeed();
    }

    private static void assertPair(GameTestHelper helper, Potion potion, int duration, int speedAmp, int vulnAmp) {
        var effects = potion.getEffects();
        helper.assertTrue(effects.size() == 2, "swift potion should have two effects, got " + effects.size());
        MobEffectInstance speed = effects.get(0);
        MobEffectInstance vuln = effects.get(1);
        helper.assertTrue(speed.getEffect() == MobEffects.MOVEMENT_SPEED, "first effect should be speed");
        helper.assertTrue(vuln.getEffect().value() == EnvenomEffects.VULNERABILITY.get(), "second effect should be vulnerability");
        helper.assertTrue(speed.getDuration() == duration && vuln.getDuration() == duration,
                "duration should be " + duration + ", got " + speed.getDuration() + "/" + vuln.getDuration());
        helper.assertTrue(speed.getAmplifier() == speedAmp, "speed amplifier " + speed.getAmplifier());
        helper.assertTrue(vuln.getAmplifier() == vulnAmp, "vulnerability amplifier " + vuln.getAmplifier());
    }

    private static void assertInterruptKeeps(GameTestHelper helper, ServerPlayer player, ItemStack stack, net.minecraft.world.item.Item expected) {
        player.removeEffect(EnvenomEffects.NAUSEA);
        player.stopUsingItem();
        player.getFoodData().setFoodLevel(10);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        player.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), expected + " should start being used");
        player.addEffect(new MobEffectInstance(EnvenomEffects.NAUSEA, 200, 0));
        tickItemUse(player);
        helper.assertTrue(!player.isUsingItem(), expected + " use should stop when nausea is applied");
        helper.assertTrue(player.getMainHandItem().is(expected) && player.getMainHandItem().getCount() == stack.getCount(),
                expected + " must not be consumed, got " + player.getMainHandItem());
    }

    private static void tickItemUse(ServerPlayer player) {
        try {
            Method method = LivingEntity.class.getDeclaredMethod("updateUsingItem", ItemStack.class);
            method.setAccessible(true);
            method.invoke(player, player.getUseItem());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot tick item use: " + e, e);
        }
    }

    private static void tickEffects(LivingEntity entity) {
        try {
            Method method = LivingEntity.class.getDeclaredMethod("tickEffects");
            method.setAccessible(true);
            method.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot tick effects: " + e, e);
        }
    }

    private static void useBlock(ServerPlayer player, GameTestHelper helper, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static boolean canceled(Player player, ItemStack stack) {
        LivingEntityUseItemEvent.Start event = new LivingEntityUseItemEvent.Start(player, stack, InteractionHand.MAIN_HAND, 32);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static ItemStack bottle(net.minecraft.core.Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static boolean isPotion(ItemStack stack, net.minecraft.core.Holder<Potion> potion) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(potion);
    }

    private static Cow toughCow(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, 1, 2, 1);
        cow.setPersistenceRequired();
        Objects.requireNonNull(cow.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(40.0D);
        cow.setHealth(40.0F);
        return cow;
    }
}
