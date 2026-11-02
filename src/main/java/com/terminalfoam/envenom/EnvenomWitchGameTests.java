package com.terminalfoam.envenom;

import com.terminalfoam.envenom.trade.WitchMerchant;
import com.terminalfoam.envenom.trade.WitchTrades;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
        var offer = merchant.getOffers().get(0);
        for (int i = 0; i < offer.getMaxUses(); i++) merchant.notifyTrade(offer);
        helper.assertTrue(new WitchMerchant(witch).getOffers().get(0).isOutOfStock(),
                "closing and reopening must not replenish sold-out stock");
        witch.getPersistentData().putLong("envenom:witch_restock", witch.level().getGameTime() - 24000L);
        var restocked = new WitchMerchant(witch);
        helper.assertTrue(restocked.getOffers().get(0).getUses() == 0, "one day replenishes stock");
        helper.assertTrue(WitchTrades.create().size() == 11, "expected potion, pack, and ingredient offers");
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
        // First offer buys 16 nether wart for one emerald.
        menu.getSlot(0).set(new ItemStack(Items.NETHER_WART, 32));
        menu.slotsChanged(menu.getSlot(0).container);
        helper.assertTrue(menu.getSlot(2).getItem().is(Items.EMERALD), "valid payment produces emerald");
        menu.quickMoveStack(player, 2);
        helper.assertTrue(merchant.getOffers().get(0).getUses() > 0, "completed trade records stock use");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) > 0, "purchased result reaches inventory");
        helper.assertTrue(menu.getSlot(0).getItem().getCount() < 32, "trade consumes payment");
        player.setPos(witch.getX() + 10, witch.getY(), witch.getZ());
        helper.assertTrue(!menu.stillValid(player), "walking away invalidates menu");
        int spent = merchant.getOffers().get(0).getUses() * 16;
        merchant.close();
        helper.assertTrue(player.getInventory().countItem(Items.NETHER_WART) + spent == 32,
                "closing returns every unspent ingredient exactly once");
        helper.assertTrue(merchant.getTradingPlayer() == null, "closing releases the witch");
        helper.succeed();
    }
}
