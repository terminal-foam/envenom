package com.terminalfoam.envenom;

import com.terminalfoam.envenom.coating.Coating;
import com.terminalfoam.envenom.coating.CoatingSlot;
import com.terminalfoam.envenom.coating.EnvenomDataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 把 Envenom 对原版效果的行为钉住，防止阈值/公式被无意改动。
 *
 * <p>这里调用的 {@code applyEffectTick} / {@code shouldApplyEffectTickThisTick}
 * 正是 mixin 的注入点，所以测试同时覆盖了「mixin 是否还在按预期接管」。</p>
 *
 * <p>26.1 的游戏测试改成数据包 {@code test_instance}，函数在
 * {@link com.terminalfoam.envenom.EnvenomTestFunctions} 里登记。</p>
 */
public final class EnvenomGameTests {
    private static final float EPS = 1.0E-3F;

    private EnvenomGameTests() {
    }

    // ── 触发间隔 ──────────────────────────────────────────────
    // I/II 级是原版语义（25>>amp），III 级起由本 mod 接管为固定 10 tick。
    public static void poisonIntervals(GameTestHelper helper) {
        MobEffect poison = MobEffects.POISON.value();
        helper.assertTrue(poison.shouldApplyEffectTickThisTick(25, 0), "poison I should tick on 25");
        helper.assertTrue(!poison.shouldApplyEffectTickThisTick(24, 0), "poison I should not tick on 24");
        helper.assertTrue(poison.shouldApplyEffectTickThisTick(12, 1), "poison II should tick on 12");
        helper.assertTrue(!poison.shouldApplyEffectTickThisTick(11, 1), "poison II should not tick on 11");
        helper.assertTrue(poison.shouldApplyEffectTickThisTick(10, 2), "poison III should tick on 10");
        helper.assertTrue(!poison.shouldApplyEffectTickThisTick(11, 2), "poison III should not tick on 11");
        helper.assertTrue(poison.shouldApplyEffectTickThisTick(10, 4), "poison V should tick on 10");
        helper.assertTrue(!poison.shouldApplyEffectTickThisTick(11, 4), "poison V should not tick every tick");
        helper.succeed();
    }

    public static void regenerationIntervals(GameTestHelper helper) {
        MobEffect regen = MobEffects.REGENERATION.value();
        helper.assertTrue(regen.shouldApplyEffectTickThisTick(50, 0), "regen I should tick on 50");
        helper.assertTrue(!regen.shouldApplyEffectTickThisTick(49, 0), "regen I should not tick on 49");
        // V 起每 tick 回血
        helper.assertTrue(regen.shouldApplyEffectTickThisTick(1, 4), "regen V should tick every tick");
        helper.succeed();
    }

    // ── 伤害/回复数值 ─────────────────────────────────────────
    public static void poisonDamageVanillaLevels(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 10.0F);
        MobEffects.POISON.value().applyEffectTick(helper.getLevel(), cow, 0);
        helper.assertTrue(Math.abs(cow.getHealth() - 9.0F) < EPS,
                "poison I should deal 1.0, got " + cow.getHealth());
        helper.succeed();
    }

    public static void poisonDamageMidLevel(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 10.0F);
        // III 级（amp 2）：10 * (2-1)/6 = 1.6667
        MobEffects.POISON.value().applyEffectTick(helper.getLevel(), cow, 2);
        helper.assertTrue(Math.abs(cow.getHealth() - (10.0F - 10.0F / 6.0F)) < EPS,
                "poison III should deal 1.6667, got delta " + (10.0F - cow.getHealth()));
        helper.succeed();
    }

    public static void poisonDamageHighLevelIsClamped(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 10.0F);
        // V 级（amp 4）：10 点，但不致死 → 10 - min(10, 9) = 1
        MobEffects.POISON.value().applyEffectTick(helper.getLevel(), cow, 4);
        helper.assertTrue(Math.abs(cow.getHealth() - 1.0F) < EPS,
                "poison V should clamp to 1 HP, got " + cow.getHealth());
        // 再打一次不应致死、也不应再扣
        MobEffects.POISON.value().applyEffectTick(helper.getLevel(), cow, 4);
        helper.assertTrue(Math.abs(cow.getHealth() - 1.0F) < EPS,
                "poison V must never kill, got " + cow.getHealth());
        helper.assertTrue(cow.isAlive(), "cow should still be alive");
        helper.succeed();
    }

    public static void regenerationHealsPerTick(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 5.0F);
        // V 级：2^4/50 = 0.32
        MobEffects.REGENERATION.value().applyEffectTick(helper.getLevel(), cow, 4);
        helper.assertTrue(Math.abs(cow.getHealth() - 5.32F) < EPS,
                "regen V should heal 0.32, got " + cow.getHealth());
        helper.succeed();
    }

    public static void regenerationDoesNotOverheal(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 10.0F);
        MobEffects.REGENERATION.value().applyEffectTick(helper.getLevel(), cow, 4);
        helper.assertTrue(Math.abs(cow.getHealth() - 10.0F) < EPS,
                "regen must not overheal, got " + cow.getHealth());
        helper.succeed();
    }

    // ── 涂层端到端：涂药 → 攻击 → 目标中效果 + 次数 -1 ─────────
    public static void coatingAppliesThenTriggersOnAttack(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.POISON);

        // 剑在主手、药水在副手 → 右键 → 剑刃(BLADE)涂层
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        player.setItemInHand(InteractionHand.OFF_HAND, potion);
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.OFF_HAND));

        List<Coating> coatings = sword.get(EnvenomDataComponents.COATINGS);
        helper.assertTrue(coatings != null && coatings.size() == 1,
                "sword should carry exactly one coating, got " + coatings);
        Coating coating = coatings.get(0);
        helper.assertTrue(coating.slot() == CoatingSlot.BLADE, "coating slot should be BLADE");
        helper.assertTrue(coating.effect().value() == MobEffects.POISON.value(), "coating effect should be poison");
        helper.assertTrue(coating.remainingUses() == 300,
                "default uses should be 300, got " + coating.remainingUses());

        // 剑刃涂层挂在 LivingDamageEvent.Pre 上，而且只认 ServerPlayer 的近战。
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        attacker.setGameMode(GameType.SURVIVAL);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, sword);
        setAttackStrengthTicker(attacker, 100);

        Cow cow = spawnCow(helper, 20.0F);
        helper.assertTrue(cow.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 4.0F),
                "cow should take the melee hit that triggers the blade coating");

        helper.runAfterDelay(3L, () -> {
            helper.assertTrue(cow.hasEffect(MobEffects.POISON),
                    "target should be poisoned by the blade coating");
            List<Coating> after = sword.get(EnvenomDataComponents.COATINGS);
            helper.assertTrue(after != null && after.size() == 1,
                    "coating should still exist after one hit");
            helper.assertTrue(after.get(0).remainingUses() == 299,
                    "remaining uses should drop to 299, got " + after.get(0).remainingUses());
            helper.succeed();
        });
    }

    public static void coatingInteractionsConsumeClick(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        player.setItemInHand(InteractionHand.OFF_HAND,
                PotionContents.createItemStack(Items.POTION, Potions.POISON));
        var coat = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(coat);
        helper.assertTrue(coat.isCanceled() && coat.getCancellationResult().consumesAction(),
                "coating must stop the client from trying the other hand");
        helper.assertTrue(player.getOffhandItem().is(Items.GLASS_BOTTLE), "one potion returns one bottle");

        Coating poison = sword.get(EnvenomDataComponents.COATINGS).get(0);
        Coating vision = new Coating(MobEffects.NIGHT_VISION, 0, 600, 10, CoatingSlot.BLADE, false);
        sword.set(EnvenomDataComponents.COATINGS, List.of(poison, vision));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.HONEY_BOTTLE, 2));
        var honey = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(honey);
        helper.assertTrue(honey.isCanceled() && honey.getCancellationResult().consumesAction(),
                "cleaning must stop the client from processing a second honey bottle");
        helper.assertTrue(player.getOffhandItem().is(Items.HONEY_BOTTLE)
                && player.getOffhandItem().getCount() == 1, "cleaning consumes only one honey bottle");
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 1,
                "cleaning returns exactly one bottle");
        helper.assertTrue(sword.get(EnvenomDataComponents.COATINGS).equals(List.of(vision)),
                "honey removes harmful coatings and keeps beneficial ones");

        sword.set(EnvenomDataComponents.COATINGS, List.of(poison, vision));
        player.setItemInHand(InteractionHand.OFF_HAND,
                PotionContents.createItemStack(Items.POTION, Potions.WATER));
        var water = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(water);
        helper.assertTrue(water.getCancellationResult().consumesAction(), "water cleaning consumes the click");
        helper.assertTrue(sword.get(EnvenomDataComponents.COATINGS).equals(List.of(poison)),
                "one water bottle removes exactly one coating");
        helper.assertTrue(player.getOffhandItem().is(Items.GLASS_BOTTLE), "water returns its bottle");
        helper.succeed();
    }

    public static void coatingRefillPreservesLongerDuration(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        sword.set(EnvenomDataComponents.COATINGS,
                List.of(new Coating(MobEffects.POISON, 0, 900, 1, CoatingSlot.BLADE, false)));
        player.setItemInHand(InteractionHand.OFF_HAND,
                PotionContents.createItemStack(Items.POTION, Potions.LONG_POISON));
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
        Coating extended = sword.get(EnvenomDataComponents.COATINGS).get(0);
        helper.assertTrue(extended.durationTicks() == Potions.LONG_POISON.value().getEffects().get(0).getDuration(),
                "a long potion must extend the same-level coating");
        helper.assertTrue(extended.remainingUses() == com.terminalfoam.envenom.config.EnvenomConfig.defaultUsesNormal,
                "extending must also replenish coating uses");
        player.setItemInHand(InteractionHand.OFF_HAND,
                PotionContents.createItemStack(Items.POTION, Potions.POISON));
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
        helper.assertTrue(sword.get(EnvenomDataComponents.COATINGS).get(0).durationTicks() == extended.durationTicks(),
                "a shorter refill must not shorten the coating");
        sword.set(EnvenomDataComponents.COATINGS,
                List.of(new Coating(MobEffects.POISON, 0, -1, 1, CoatingSlot.BLADE, false)));
        player.setItemInHand(InteractionHand.OFF_HAND,
                PotionContents.createItemStack(Items.POTION, Potions.POISON));
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
        helper.assertTrue(sword.get(EnvenomDataComponents.COATINGS).get(0).durationTicks() == -1,
                "a refill must preserve infinite duration");
        helper.succeed();
    }

    public static void freshPassiveCoatingsTriggerAtTimeZero(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        // Pass controlled game times without changing the shared world's clock.
        for (CoatingSlot slot : new CoatingSlot[]{CoatingSlot.HILT, CoatingSlot.ARMOR}) {
            ItemStack stack = new ItemStack(slot == CoatingSlot.HILT ? Items.IRON_SWORD : Items.IRON_CHESTPLATE);
            stack.set(EnvenomDataComponents.COATINGS,
                    List.of(new Coating(MobEffects.NIGHT_VISION, 0, 1200, 10, slot, false)));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.removeEffect(MobEffects.NIGHT_VISION);
            tickPassiveCoating(player, stack, slot, 0L);
            helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION), "fresh coating must apply at time zero");
            helper.assertTrue(stack.get(EnvenomDataComponents.COATINGS).get(0).remainingUses() == 9,
                    "first trigger consumes one use");
            tickPassiveCoating(player, stack, slot, 599L);
            helper.assertTrue(stack.get(EnvenomDataComponents.COATINGS).get(0).remainingUses() == 9,
                    "a recorded time zero must still enforce the cooldown");
            tickPassiveCoating(player, stack, slot, 600L);
            helper.assertTrue(stack.get(EnvenomDataComponents.COATINGS).get(0).remainingUses() == 8,
                    "the next trigger is due at 600 ticks");
        }
        helper.succeed();
    }

    private static void tickPassiveCoating(Player player, ItemStack stack, CoatingSlot slot, long now) {
        try {
            var handler = com.terminalfoam.envenom.handler.CoatingEffectHandler.class;
            var living = net.minecraft.world.entity.LivingEntity.class;
            var method = slot == CoatingSlot.HILT
                    ? handler.getDeclaredMethod("checkEquipment", living, InteractionHand.class, long.class)
                    : handler.getDeclaredMethod("checkArmorSlot", living, ItemStack.class, long.class);
            method.setAccessible(true);
            method.invoke(null, player, slot == CoatingSlot.HILT ? InteractionHand.MAIN_HAND : stack, now);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot exercise passive coating ticks", e);
        }
    }

    public static void witherIntervals(GameTestHelper helper) {
        MobEffect wither = MobEffects.WITHER.value();
        helper.assertTrue(wither.shouldApplyEffectTickThisTick(40, 0), "wither I retains its 40-tick interval");
        helper.assertTrue(!wither.shouldApplyEffectTickThisTick(10, 0), "wither I is not accelerated");
        for (int amp : new int[]{1, 2, 3, 4, 9, 20, 255}) {
            helper.assertTrue(wither.shouldApplyEffectTickThisTick(10, amp), "higher wither ticks every ten ticks");
            helper.assertTrue(!wither.shouldApplyEffectTickThisTick(9, amp), "higher wither must not tick early");
        }
        helper.succeed();
    }

    public static void witherDamageCurveAndType(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, 1, 2, 1);
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0);
        int[] amps = {0, 1, 2, 3, 4, 9, 20};
        float[] expected = {1.0F, 10.0F / 6.0F, 20.0F / 6.0F, 10.0F, 12.4F, 24.4F, 50.8F};
        for (int i = 0; i < amps.length; i++) {
            cow.setHealth(200.0F);
            cow.invulnerableTime = 0;
            MobEffects.WITHER.value().applyEffectTick(helper.getLevel(), cow, amps[i]);
            helper.assertTrue(Math.abs((200.0F - cow.getHealth()) - expected[i]) < EPS,
                    "wither damage must match the requested curve at amplifier " + amps[i]);
            helper.assertTrue(cow.getLastDamageSource() != null
                    && cow.getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.WITHER),
                    "wither pulses must preserve minecraft:wither damage");
        }
        helper.succeed();
    }

    public static void witherKillsAndDamagesUndead(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, 1, 2, 1);
        cow.setHealth(1.0F);
        MobEffects.WITHER.value().applyEffectTick(helper.getLevel(), cow, 1);
        helper.assertTrue(cow.isDeadOrDying(), "wither II must kill rather than clamp to one HP");
        var zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 1);
        helper.assertTrue(zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WITHER, 200, 1)),
                "ordinary undead remain susceptible to wither");
        float before = zombie.getHealth();
        MobEffects.WITHER.value().applyEffectTick(helper.getLevel(), zombie, 1);
        helper.assertTrue(zombie.getHealth() < before, "wither damages undead");
        var boss = helper.spawn(EntityType.WITHER, 1, 2, 3);
        helper.assertTrue(!boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WITHER, 200, 1)),
                "ordinary wither application must not bypass the wither boss's immunity");
        helper.succeed();
    }

    public static void reagentSwordDealsNoDirectDamage(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        var sword = new ItemStack(com.terminalfoam.envenom.item.EnvenomItems.REAGENT_SWORD.get());
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        sword.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
        sword.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(100.0);
        var cow = helper.spawn(EntityType.COW, 1, 2, 1);
        player.setPos(cow.getX(), cow.getY(), cow.getZ());
        float health = cow.getHealth();
        setAttackStrengthTicker(player, 100);
        player.attack(cow);
        helper.assertTrue(cow.getHealth() == health, "even enchanted reagent sword attacks deal no direct damage");
        helper.assertTrue(!cow.isOnFire(), "fire aspect must not add damage to reagent sword attacks");
        helper.assertTrue(sword.getDamageValue() == 1, "a valid contact consumes one durability");
        helper.assertTrue(sword.is(net.minecraft.tags.ItemTags.SWORDS), "reagent sword is tagged as a sword");
        helper.succeed();
    }

    public static void reagentSwordDeliversInjection(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var sword = new ItemStack(com.terminalfoam.envenom.item.EnvenomItems.REAGENT_SWORD.get());
        sword.enchant(com.terminalfoam.envenom.enchantment.EnvenomEnchantments.injection(helper.getLevel().registryAccess()), 1);
        sword.set(EnvenomDataComponents.COATINGS,
                List.of(new Coating(MobEffects.POISON, 0, 200, 10, CoatingSlot.BLADE, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        var target = helper.spawn(EntityType.WITHER, 1, 2, 1);
        player.setPos(target.getX(), target.getY(), target.getZ());
        float health = target.getHealth();
        setAttackStrengthTicker(player, 100);
        player.attack(target);
        helper.assertTrue(target.getHealth() == health, "coated sword must still deal no direct damage");
        helper.assertTrue(sword.get(EnvenomDataComponents.COATINGS).get(0).remainingUses() == 9,
                "one contact consumes one coating use");
        helper.runAfterDelay(3L, () -> {
            helper.assertTrue(target.hasEffect(MobEffects.POISON), "captured injection must deliver poison to an immune target");
            helper.succeed();
        });
    }

    public static void reagentSwordBlocksMobMelee(GameTestHelper helper) {
        var attacker = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        attacker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new ItemStack(com.terminalfoam.envenom.item.EnvenomItems.REAGENT_SWORD.get()));
        var cow = helper.spawn(EntityType.COW, 2, 2, 1);
        float health = cow.getHealth();
        cow.hurtServer(helper.getLevel(), cow.damageSources().mobAttack(attacker), 100.0F);
        helper.assertTrue(cow.getHealth() == health, "a mob holding the sword must not deal melee damage either");
        helper.succeed();
    }

    // ── helpers ──────────────────────────────────────────────
    private static Cow spawnCow(GameTestHelper helper, float health) {
        Cow cow = helper.spawn(EntityType.COW, 1, 2, 1);
        cow.setPersistenceRequired();
        cow.setHealth(health);
        return cow;
    }

    private static void setAttackStrengthTicker(Player player, int value) {
        try {
            // 字段在 LivingEntity 上（protected），不在 Player 上
            Field field = net.minecraft.world.entity.LivingEntity.class
                    .getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set attackStrengthTicker: " + e, e);
        }
    }
}

