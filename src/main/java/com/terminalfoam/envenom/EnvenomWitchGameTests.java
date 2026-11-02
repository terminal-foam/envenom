package com.terminalfoam.envenom;

import com.terminalfoam.envenom.trade.WitchMerchant;
import com.terminalfoam.envenom.trade.WitchTrades;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.raid.Raid;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import com.terminalfoam.envenom.item.PotionPackItem;
import com.terminalfoam.envenom.item.EnvenomItems;
import com.terminalfoam.envenom.potion.EnvenomPotions;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Envenom.MODID)
@PrefixGameTestTemplate(false)
public final class EnvenomWitchGameTests {
    private EnvenomWitchGameTests() {}

    @GameTest(template = "empty")
    public static void witchNeutralAndRetaliates(GameTestHelper helper) {
        var witch = helper.spawn(EntityType.WITCH, 1, 2, 1);
        var player = helper.makeMockServerPlayerInLevel();
        witch.setTarget(player);
        helper.assertTrue(witch.getTarget() == null, "witch must reject unprovoked player targets");
        witch.setLastHurtByMob(player);
        witch.setTarget(player);
        helper.assertTrue(witch.getTarget() == player, "witch must retain retaliation");
        helper.assertTrue(!new WitchMerchant(witch).canTrade(player), "angry witches cannot trade");
        var other = helper.makeMockServerPlayerInLevel();
        witch.setTarget(other);
        helper.assertTrue(witch.getTarget() != other, "retaliation must not spread to innocent players");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void witchStockPersistsAndRestocks(GameTestHelper helper) {
        var witch = helper.spawn(EntityType.WITCH, 1, 2, 1);
        var merchant = new WitchMerchant(witch);
        var original = merchant.getOffers();
        var offer = original.get(0);
        for (int i = 0; i < offer.getMaxUses(); i++) merchant.notifyTrade(offer);
        helper.assertTrue(new WitchMerchant(witch).getOffers().get(0).isOutOfStock(),
                "closing and reopening must not replenish sold-out stock");
        witch.getPersistentData().putLong("envenom:witch_restock", witch.level().getGameTime() - 24000L);
        var restocked = new WitchMerchant(witch);
        helper.assertTrue(restocked.getOffers().get(0).getUses() == 0, "one day replenishes stock");
        assertSameOffers(helper, original, new WitchMerchant(witch).getOffers());
        assertSameOffers(helper, original, restocked.getOffers());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void witchMenuChargesAndReturnsPayment(GameTestHelper helper) {
        var witch = helper.spawn(EntityType.WITCH, 1, 2, 1);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(witch.getX(), witch.getY(), witch.getZ());
        var merchant = new WitchMerchant(witch);
        merchant.open(player);
        helper.assertTrue(player.containerMenu instanceof MerchantMenu, "opens vanilla trade menu");
        var menu = (MerchantMenu) player.containerMenu;
        // The randomly selected first offer buys ingredients for one emerald.
        ItemStack payment = merchant.getOffers().get(0).getBaseCostA().copy();
        var paymentItem = payment.getItem();
        int perTrade = payment.getCount();
        int total = perTrade * 2;
        payment.setCount(total);
        menu.getSlot(0).set(payment);
        menu.slotsChanged(menu.getSlot(0).container);
        helper.assertTrue(menu.getSlot(2).getItem().is(Items.EMERALD), "valid payment produces emerald");
        menu.quickMoveStack(player, 2);
        helper.assertTrue(merchant.getOffers().get(0).getUses() > 0, "completed trade records stock use");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) > 0, "purchased result reaches inventory");
        helper.assertTrue(menu.getSlot(0).getItem().getCount() < total, "trade consumes payment");
        player.setPos(witch.getX() + 10, witch.getY(), witch.getZ());
        helper.assertTrue(!menu.stillValid(player), "walking away invalidates menu");
        int spent = merchant.getOffers().get(0).getUses() * perTrade;
        merchant.close();
        helper.assertTrue(player.getInventory().countItem(paymentItem) + spent == total,
                "closing returns every unspent ingredient exactly once");
        helper.assertTrue(merchant.getTradingPlayer() == null, "closing releases the witch");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void witchOffersAreVaried(GameTestHelper helper) {
        Set<String> catalogues = new HashSet<>();
        MerchantOffers union = new MerchantOffers();
        for (int seed = 0; seed < 256; seed++) {
            MerchantOffers offers = WitchTrades.create(RandomSource.create(seed));
            helper.assertTrue(offers.size() == 11, "each witch must have eleven offers");
            int purchases = 0, potions = 0, packs = 0;
            StringBuilder signature = new StringBuilder();
            for (int i = 0; i < offers.size(); i++) {
                MerchantOffer offer = offers.get(i);
                ItemStack result = offer.getResult();
                if (result.is(Items.EMERALD)) purchases++;
                else if (result.getItem() instanceof PotionPackItem) packs++;
                else if (result.is(Items.POTION) || result.is(Items.SPLASH_POTION)) potions++;
                for (int j = 0; j < i; j++) {
                    helper.assertTrue(!sameOffer(offer, offers.get(j)), "no duplicate offers");
                }
                if (union.stream().noneMatch(other -> sameOffer(offer, other))) union.add(offer);
                signature.append(offer.getBaseCostA()).append("->").append(result).append(';');
            }
            helper.assertTrue(purchases == 2 && potions == 4 && packs == 5,
                    "selection must contain two purchases, four potions, and five packs");
            assertSameOffers(helper, offers, WitchTrades.create(RandomSource.create(seed)));
            catalogues.add(signature.toString());
        }
        helper.assertTrue(catalogues.size() > 1, "different seeds must produce different catalogues");
        helper.assertTrue(union.size() == 84, "all 84 pool entries should remain reachable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void witchLegacyCatalogueMigratesOnce(GameTestHelper helper) {
        MerchantOffers legacy = legacyFixture();
        // Every old offer has already been used twice before the update.
        legacy.forEach(offer -> { offer.increaseUses(); offer.increaseUses(); });
        var witch = helper.spawn(EntityType.WITCH, 1, 2, 1);
        var data = witch.getPersistentData();
        var ops = RegistryOps.create(NbtOps.INSTANCE, witch.registryAccess());
        data.put("envenom:witch_offers", MerchantOffers.CODEC.encodeStart(ops, legacy).getOrThrow());
        data.putLong("envenom:witch_restock", witch.level().getGameTime());
        var migrated = new WitchMerchant(witch).getOffers();
        helper.assertTrue(migrated.size() == 11, "legacy migration retains eleven offers");
        // Five pack choices from the original five would be possible; test stock on all matches.
        for (MerchantOffer offer : migrated) {
            boolean existed = legacy.stream().anyMatch(old -> sameOffer(old, offer));
            helper.assertTrue(offer.getUses() == (existed ? 2 : 0), "migration must preserve used stock");
        }
        helper.assertTrue(data.contains("envenom:witch_random_offers"), "migration marker is persisted");
        var reopened = new WitchMerchant(witch).getOffers();
        assertSameOffers(helper, migrated, reopened);
        for (int i = 0; i < migrated.size(); i++) {
            helper.assertTrue(migrated.get(i).getUses() == reopened.get(i).getUses(), "reopen preserves uses");
        }
        // A save already marked as randomized is authoritative, even if it happens to match the old catalogue.
        data.put("envenom:witch_offers", MerchantOffers.CODEC.encodeStart(ops, legacy).getOrThrow());
        assertSameOffers(helper, legacy, new WitchMerchant(witch).getOffers());
        helper.succeed();
    }

    private static MerchantOffers legacyFixture() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.NETHER_WART, 16), new ItemStack(Items.EMERALD), 8, 0, 0));
        offers.add(new MerchantOffer(new ItemCost(Items.GLASS_BOTTLE, 12), new ItemStack(Items.EMERALD), 8, 0, 0));
        ItemStack[] results = {
                PotionContents.createItemStack(Items.POTION, Potions.HEALING),
                PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE),
                PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WEAKNESS),
                PotionContents.createItemStack(Items.POTION, EnvenomPotions.SWIFT_AS_A_HARE),
                new ItemStack(EnvenomItems.SWIFTNESS_PACK.get()), new ItemStack(EnvenomItems.HEALING_PACK.get()),
                new ItemStack(EnvenomItems.POISON_PACK.get()), new ItemStack(EnvenomItems.STRENGTH_PACK.get()),
                new ItemStack(EnvenomItems.REGENERATION_PACK.get())};
        int[] prices = {3, 4, 4, 6, 2, 3, 3, 4, 5};
        for (int i = 0; i < results.length; i++) {
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, prices[i]), results[i], i == 3 ? 4 : 8, 0, 0));
        }
        return offers;
    }

    @GameTest(template = "empty")
    public static void golemsIgnoreWitches(GameTestHelper helper) {
        var witch = helper.spawn(EntityType.WITCH, 1, 2, 1);
        var golem = helper.spawn(EntityType.IRON_GOLEM, 3, 2, 1);
        golem.setTarget(witch);
        helper.assertTrue(golem.getTarget() == null, "golems must not target witches");
        golem.setLastHurtByMob(witch);
        golem.setTarget(witch);
        helper.assertTrue(golem.getTarget() == null, "retaliation must not override witch protection");
        var zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 3);
        golem.setTarget(zombie);
        helper.assertTrue(golem.getTarget() == zombie, "other hostile mobs remain valid targets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void witchesStayOutsideRaids(GameTestHelper helper) {
        var witch = helper.spawn(EntityType.WITCH, 1, 2, 1);
        helper.assertTrue(!witch.canJoinRaid(), "new witches cannot join nearby raids");
        var raid = new Raid(1, helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)));
        raid.joinRaid(1, witch, null, true);
        helper.assertTrue(witch.getCurrentRaid() == raid, "fixture must begin in a raid");
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(witch));
        helper.assertTrue(witch.getCurrentRaid() == null && !witch.canJoinRaid(), "existing raid witches must leave");
        helper.assertTrue(raid.getTotalRaidersAlive() == 0, "removed witches must not block wave completion");
        helper.assertTrue(witch.isAlive() && !witch.isRemoved(), "ordinary witch remains alive in the world");
        var pillager = helper.spawn(EntityType.PILLAGER, 3, 2, 1);
        pillager.setCanJoinRaid(true);
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(pillager));
        helper.assertTrue(pillager.canJoinRaid(), "other raid participants remain eligible");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void raidWavesNeverSpawnWitches(GameTestHelper helper) {
        var raid = new Raid(1, helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)));
        try {
            Class<?> type = Class.forName("net.minecraft.world.entity.raid.Raid$RaiderType");
            Object witch = null, pillager = null;
            for (Object member : type.getEnumConstants()) {
                if (((Enum<?>) member).name().equals("WITCH")) witch = member;
                if (((Enum<?>) member).name().equals("PILLAGER")) pillager = member;
            }
            helper.assertTrue(witch != null && pillager != null, "raid types must exist");
            var regular = Raid.class.getDeclaredMethod("getDefaultNumSpawns", type, int.class, boolean.class);
            var bonus = Raid.class.getDeclaredMethod("getPotentialBonusSpawns", type, RandomSource.class,
                    int.class, DifficultyInstance.class, boolean.class);
            regular.setAccessible(true);
            bonus.setAccessible(true);
            for (Difficulty difficulty : new Difficulty[]{Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}) {
                var instance = new DifficultyInstance(difficulty, 72000L, 0L, 1.0F);
                for (int wave = 1; wave <= 7; wave++) {
                    for (boolean extra : new boolean[]{false, true}) {
                        helper.assertTrue((int) regular.invoke(raid, witch, wave, extra) == 0,
                                "fixed wave must contain no witches");
                        for (int seed = 0; seed < 16; seed++) {
                            helper.assertTrue((int) bonus.invoke(raid, witch, RandomSource.create(seed), wave, instance, extra) == 0,
                                    "bonus wave must contain no witches");
                        }
                    }
                }
            }
            helper.assertTrue((int) regular.invoke(raid, pillager, 1, false) > 0,
                    "pillager waves must remain unchanged");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Raid methods must match the registered mixin", e);
        }
        helper.succeed();
    }

    private static void assertSameOffers(GameTestHelper helper, MerchantOffers expected, MerchantOffers actual) {
        helper.assertTrue(expected.size() == actual.size(), "offer count must persist");
        for (int i = 0; i < expected.size(); i++) {
            helper.assertTrue(sameOffer(expected.get(i), actual.get(i)), "offers must not reroll on reopen or restock");
        }
    }

    private static boolean sameOffer(MerchantOffer a, MerchantOffer b) {
        return ItemStack.matches(a.getBaseCostA(), b.getBaseCostA())
                && ItemStack.matches(a.getCostB(), b.getCostB())
                && ItemStack.matches(a.getResult(), b.getResult());
    }
}
