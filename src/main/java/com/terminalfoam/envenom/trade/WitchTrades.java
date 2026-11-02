package com.terminalfoam.envenom.trade;

import com.terminalfoam.envenom.item.EnvenomItems;
import com.terminalfoam.envenom.item.PotionPackItem;
import com.terminalfoam.envenom.potion.EnvenomPotions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Select eleven distinct offers once per witch, then persist the selection and stock. */
public final class WitchTrades {
    private WitchTrades() {}

    public static MerchantOffers create(RandomSource random) {
        MerchantOffers selected = new MerchantOffers();
        sample(selected, ingredientPool(), 2, random);
        sample(selected, potionPool(), 4, random);
        sample(selected, packPool(), 5, random);
        return selected;
    }

    /** Recognize the old fixed catalogue, preserving stock on any offers selected again. */
    public static MerchantOffers selectIfLegacy(MerchantOffers saved, RandomSource random) {
        MerchantOffers legacy = legacyCatalogue();
        if (saved.size() != legacy.size()) return saved;
        for (int i = 0; i < legacy.size(); i++) {
            if (!sameOffer(saved.get(i), legacy.get(i))) return saved;
        }
        MerchantOffers selected = create(random);
        for (int i = 0; i < selected.size(); i++) {
            for (MerchantOffer old : saved) {
                if (sameOffer(selected.get(i), old)) {
                    selected.set(i, old);
                    break;
                }
            }
        }
        return selected;
    }

    private static boolean sameOffer(MerchantOffer a, MerchantOffer b) {
        return ItemStack.matches(a.getBaseCostA(), b.getBaseCostA())
                && ItemStack.matches(a.getCostB(), b.getCostB())
                && ItemStack.matches(a.getResult(), b.getResult());
    }

    private static void sample(MerchantOffers selected, List<MerchantOffer> pool,
            int count, RandomSource random) {
        List<MerchantOffer> remaining = new ArrayList<>(pool);
        for (int i = 0; i < count; i++) {
            selected.add(remaining.remove(random.nextInt(remaining.size())));
        }
    }

    private static MerchantOffers ingredientPool() {
        MerchantOffers pool = new MerchantOffers();
        buy(pool, Items.NETHER_WART, 16);
        buy(pool, Items.GLASS_BOTTLE, 12);
        buy(pool, Items.SUGAR, 24);
        buy(pool, Items.SPIDER_EYE, 12);
        buy(pool, Items.REDSTONE, 24);
        buy(pool, Items.GLOWSTONE_DUST, 16);
        return pool;
    }

    private static MerchantOffers potionPool() {
        MerchantOffers pool = new MerchantOffers();
        sell(pool, 3, PotionContents.createItemStack(Items.POTION, Potions.HEALING), 8);
        sell(pool, 4, PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE), 8);
        sell(pool, 4, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WEAKNESS), 8);
        sell(pool, 6, PotionContents.createItemStack(Items.POTION, EnvenomPotions.SWIFT_AS_A_HARE), 4);
        sell(pool, 3, PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), 8);
        sell(pool, 4, PotionContents.createItemStack(Items.POTION, Potions.STRENGTH), 8);
        sell(pool, 5, PotionContents.createItemStack(Items.POTION, Potions.REGENERATION), 8);
        sell(pool, 4, PotionContents.createItemStack(Items.POTION, Potions.NIGHT_VISION), 8);
        sell(pool, 4, PotionContents.createItemStack(Items.POTION, Potions.WATER_BREATHING), 8);
        sell(pool, 5, PotionContents.createItemStack(Items.POTION, Potions.SLOW_FALLING), 8);
        sell(pool, 7, PotionContents.createItemStack(Items.POTION, EnvenomPotions.LONG_SWIFT_AS_A_HARE), 4);
        sell(pool, 8, PotionContents.createItemStack(Items.POTION, EnvenomPotions.STRONG_SWIFT_AS_A_HARE), 4);
        return pool;
    }

    private static MerchantOffers packPool() {
        MerchantOffers pool = new MerchantOffers();
        for (PotionPackItem pack : EnvenomItems.packs()) {
            String path = BuiltInRegistries.ITEM.getKey(pack).getPath();
            int extra = 0;
            for (String prefix : List.of("long_", "strong_", "splash_", "lingering_")) {
                if (path.startsWith(prefix)) {
                    path = path.substring(prefix.length());
                    extra = switch (prefix) {
                        case "long_" -> 1;
                        case "lingering_" -> 4;
                        default -> 2;
                    };
                    break;
                }
            }
            int price = switch (path) {
                case "swiftness_pack" -> 2;
                case "healing_pack", "poison_pack" -> 3;
                case "regeneration_pack" -> 5;
                default -> 4;
            };
            sell(pool, price + extra, new ItemStack(pack), 8);
        }
        return pool;
    }

    private static MerchantOffers legacyCatalogue() {
        MerchantOffers legacy = new MerchantOffers();
        legacy.addAll(ingredientPool().subList(0, 2));
        legacy.addAll(potionPool().subList(0, 4));
        sell(legacy, 2, new ItemStack(EnvenomItems.SWIFTNESS_PACK.get()), 8);
        sell(legacy, 3, new ItemStack(EnvenomItems.HEALING_PACK.get()), 8);
        sell(legacy, 3, new ItemStack(EnvenomItems.POISON_PACK.get()), 8);
        sell(legacy, 4, new ItemStack(EnvenomItems.STRENGTH_PACK.get()), 8);
        sell(legacy, 5, new ItemStack(EnvenomItems.REGENERATION_PACK.get()), 8);
        return legacy;
    }

    private static void buy(MerchantOffers offers, Item item, int count) {
        offers.add(new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD), 8, 0, 0));
    }

    private static void sell(MerchantOffers offers, int price, ItemStack result, int stock) {
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), result, stock, 0, 0));
    }
}
