package com.terminalfoam.envenom.trade;

import com.terminalfoam.envenom.item.EnvenomItems;
import com.terminalfoam.envenom.potion.EnvenomPotions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Fixed prices; stock belongs to each witch, not to each customer. */
public final class WitchTrades {
    private WitchTrades() {}

    public static MerchantOffers create() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.NETHER_WART, 16), new ItemStack(Items.EMERALD), 8, 0, 0));
        offers.add(new MerchantOffer(new ItemCost(Items.GLASS_BOTTLE, 12), new ItemStack(Items.EMERALD), 8, 0, 0));
        sell(offers, 3, PotionContents.createItemStack(Items.POTION, Potions.HEALING), 8);
        sell(offers, 4, PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE), 8);
        sell(offers, 4, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WEAKNESS), 8);
        sell(offers, 6, PotionContents.createItemStack(Items.POTION, EnvenomPotions.SWIFT_AS_A_HARE), 4);
        pack(offers, 2, EnvenomItems.SWIFTNESS_PACK.get());
        pack(offers, 3, EnvenomItems.HEALING_PACK.get());
        pack(offers, 3, EnvenomItems.POISON_PACK.get());
        pack(offers, 4, EnvenomItems.STRENGTH_PACK.get());
        pack(offers, 5, EnvenomItems.REGENERATION_PACK.get());
        return offers;
    }

    private static void pack(MerchantOffers offers, int price, Item item) {
        sell(offers, price, new ItemStack(item), 8);
    }

    private static void sell(MerchantOffers offers, int price, ItemStack result, int stock) {
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), result, stock, 0, 0));
    }
}
