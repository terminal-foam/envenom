package com.terminalfoam.envenom;

import com.terminalfoam.envenom.coating.Coating;
import com.terminalfoam.envenom.coating.CoatingSlot;
import com.terminalfoam.envenom.coating.EnvenomDataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 把 Envenom 对原版效果的行为钉住，防止阈值/公式被无意改动。
 *
 * <p>这里调用的 {@code applyEffectTick} / {@code shouldApplyEffectTickThisTick}
 * 正是 mixin 的注入点，所以测试同时覆盖了「mixin 是否还在按预期接管」。</p>
 */
@GameTestHolder(Envenom.MODID)
@PrefixGameTestTemplate(false)
public final class EnvenomGameTests {
    private static final float EPS = 1.0E-3F;

    private EnvenomGameTests() {
    }

    // ── 触发间隔 ──────────────────────────────────────────────
    // I/II 级是原版语义（25>>amp），III 级起由本 mod 接管为固定 10 tick。
    @GameTest(template = "empty")
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

    @GameTest(template = "empty")
    public static void regenerationIntervals(GameTestHelper helper) {
        MobEffect regen = MobEffects.REGENERATION.value();
        helper.assertTrue(regen.shouldApplyEffectTickThisTick(50, 0), "regen I should tick on 50");
        helper.assertTrue(!regen.shouldApplyEffectTickThisTick(49, 0), "regen I should not tick on 49");
        // V 起每 tick 回血
        helper.assertTrue(regen.shouldApplyEffectTickThisTick(1, 4), "regen V should tick every tick");
        helper.succeed();
    }

    // ── 伤害/回复数值 ─────────────────────────────────────────
    @GameTest(template = "empty")
    public static void poisonDamageVanillaLevels(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 10.0F);
        MobEffects.POISON.value().applyEffectTick(cow, 0);
        helper.assertTrue(Math.abs(cow.getHealth() - 9.0F) < EPS,
                "poison I should deal 1.0, got " + cow.getHealth());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poisonDamageMidLevel(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 10.0F);
        // III 级（amp 2）：10 * (2-1)/6 = 1.6667
        MobEffects.POISON.value().applyEffectTick(cow, 2);
        helper.assertTrue(Math.abs(cow.getHealth() - (10.0F - 10.0F / 6.0F)) < EPS,
                "poison III should deal 1.6667, got delta " + (10.0F - cow.getHealth()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poisonDamageHighLevelIsClamped(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 10.0F);
        // V 级（amp 4）：10 点，但不致死 → 10 - min(10, 9) = 1
        MobEffects.POISON.value().applyEffectTick(cow, 4);
        helper.assertTrue(Math.abs(cow.getHealth() - 1.0F) < EPS,
                "poison V should clamp to 1 HP, got " + cow.getHealth());
        // 再打一次不应致死、也不应再扣
        MobEffects.POISON.value().applyEffectTick(cow, 4);
        helper.assertTrue(Math.abs(cow.getHealth() - 1.0F) < EPS,
                "poison V must never kill, got " + cow.getHealth());
        helper.assertTrue(cow.isAlive(), "cow should still be alive");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void regenerationHealsPerTick(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 5.0F);
        // V 级：2^4/50 = 0.32
        MobEffects.REGENERATION.value().applyEffectTick(cow, 4);
        helper.assertTrue(Math.abs(cow.getHealth() - 5.32F) < EPS,
                "regen V should heal 0.32, got " + cow.getHealth());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void regenerationDoesNotOverheal(GameTestHelper helper) {
        Cow cow = spawnCow(helper, 10.0F);
        MobEffects.REGENERATION.value().applyEffectTick(cow, 4);
        helper.assertTrue(Math.abs(cow.getHealth() - 10.0F) < EPS,
                "regen must not overheal, got " + cow.getHealth());
        helper.succeed();
    }

    // ── 涂层端到端：涂药 → 攻击 → 目标中效果 + 次数 -1 ─────────
    @GameTest(template = "empty")
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
        helper.assertTrue(cow.hurt(helper.getLevel().damageSources().playerAttack(attacker), 4.0F),
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

