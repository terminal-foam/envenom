package com.terminalfoam.envenom.item;

import com.terminalfoam.envenom.Envenom;
import com.terminalfoam.envenom.potion.EnvenomPotions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 每种「能酿出有效果药水」的原材料对应一个药包。
 *
 * <p>故意不做药包的原材料：下界疣（粗制）、红石（平凡 / 仅延长）、荧石粉（厚重 / 仅强化）、
 * 火药和龙息（只改瓶型）。平凡、浑浊、粗制、厚重都没有战斗效果。
 */
@EventBusSubscriber(modid = Envenom.MODID)
public final class EnvenomItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Envenom.MODID);

    public static final DeferredItem<ReagentSwordItem> REAGENT_SWORD =
            ITEMS.registerItem("reagent_sword", ReagentSwordItem::new, new Item.Properties());

    private static final List<DeferredItem<PotionPackItem>> PACKS = new ArrayList<>();

    /** 牛奶瓶：牛奶桶 + 3 玻璃瓶 → 3 个，喝完返还玻璃瓶。 */
    public static final DeferredItem<MilkBottleItem> MILK_BOTTLE =
            ITEMS.registerItem("milk_bottle", MilkBottleItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredItem<PotionPackItem> SWIFTNESS_PACK =
            pack("swiftness_pack", Potions.SWIFTNESS);
    public static final DeferredItem<PotionPackItem> LEAPING_PACK =
            pack("leaping_pack", Potions.LEAPING);
    public static final DeferredItem<PotionPackItem> STRENGTH_PACK =
            pack("strength_pack", Potions.STRENGTH);
    public static final DeferredItem<PotionPackItem> HEALING_PACK =
            pack("healing_pack", Potions.HEALING);
    public static final DeferredItem<PotionPackItem> POISON_PACK =
            pack("poison_pack", Potions.POISON);
    public static final DeferredItem<PotionPackItem> REGENERATION_PACK =
            pack("regeneration_pack", Potions.REGENERATION);
    public static final DeferredItem<PotionPackItem> FIRE_RESISTANCE_PACK =
            pack("fire_resistance_pack", Potions.FIRE_RESISTANCE);
    public static final DeferredItem<PotionPackItem> WATER_BREATHING_PACK =
            pack("water_breathing_pack", Potions.WATER_BREATHING);
    public static final DeferredItem<PotionPackItem> NIGHT_VISION_PACK =
            pack("night_vision_pack", Potions.NIGHT_VISION);
    public static final DeferredItem<PotionPackItem> TURTLE_MASTER_PACK =
            pack("turtle_master_pack", Potions.TURTLE_MASTER);
    public static final DeferredItem<PotionPackItem> SLOW_FALLING_PACK =
            pack("slow_falling_pack", Potions.SLOW_FALLING);
    public static final DeferredItem<PotionPackItem> WEAKNESS_PACK =
            pack("weakness_pack", Potions.WEAKNESS);
    public static final DeferredItem<PotionPackItem> WIND_CHARGED_PACK =
            pack("wind_charged_pack", Potions.WIND_CHARGED);
    public static final DeferredItem<PotionPackItem> WEAVING_PACK =
            pack("weaving_pack", Potions.WEAVING);
    public static final DeferredItem<PotionPackItem> OOZING_PACK =
            pack("oozing_pack", Potions.OOZING);
    public static final DeferredItem<PotionPackItem> INFESTED_PACK =
            pack("infested_pack", Potions.INFESTED);

    /**
     * 变体药包：延长（红石）、强化（荧石粉）、喷溅（火药）。
     *
     * <p>用基础药包 + 修正材料合成；酿造时与基础药包走同一入口，
     * 喷溅包由自定义酿造配方直接产出喷溅药水。
     */
    static {
        variant("swiftness", Potions.SWIFTNESS, Potions.LONG_SWIFTNESS, Potions.STRONG_SWIFTNESS);
        variant("leaping", Potions.LEAPING, Potions.LONG_LEAPING, Potions.STRONG_LEAPING);
        variant("strength", Potions.STRENGTH, Potions.LONG_STRENGTH, Potions.STRONG_STRENGTH);
        variant("healing", Potions.HEALING, null, Potions.STRONG_HEALING);
        variant("poison", Potions.POISON, Potions.LONG_POISON, Potions.STRONG_POISON);
        variant("regeneration", Potions.REGENERATION, Potions.LONG_REGENERATION, Potions.STRONG_REGENERATION);
        variant("fire_resistance", Potions.FIRE_RESISTANCE, Potions.LONG_FIRE_RESISTANCE, null);
        variant("water_breathing", Potions.WATER_BREATHING, Potions.LONG_WATER_BREATHING, null);
        variant("night_vision", Potions.NIGHT_VISION, Potions.LONG_NIGHT_VISION, null);
        variant("turtle_master", Potions.TURTLE_MASTER, Potions.LONG_TURTLE_MASTER, Potions.STRONG_TURTLE_MASTER);
        variant("slow_falling", Potions.SLOW_FALLING, Potions.LONG_SLOW_FALLING, null);
        variant("weakness", Potions.WEAKNESS, Potions.LONG_WEAKNESS, null);
        variant("wind_charged", Potions.WIND_CHARGED, null, null);
        variant("weaving", Potions.WEAVING, null, null);
        variant("oozing", Potions.OOZING, null, null);
        variant("infested", Potions.INFESTED, null, null);
    }

    private static void variant(String base,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> basePotion,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> longPotion,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> strongPotion) {
        if (longPotion != null) {
            pack("long_" + base + "_pack", longPotion);
        }
        if (strongPotion != null) {
            pack("strong_" + base + "_pack", strongPotion);
        }
        DeferredItem<PotionPackItem> splash = ITEMS.register(
                "splash_" + base + "_pack", () -> new PotionPackItem(new Item.Properties(), basePotion, true));
        PACKS.add(splash);
        DeferredItem<PotionPackItem> lingering = ITEMS.register(
                "lingering_" + base + "_pack",
                () -> new PotionPackItem(new Item.Properties(), basePotion, PotionPackItem.Container.LINGERING));
        PACKS.add(lingering);
    }

    private EnvenomItems() {
    }
    private static DeferredItem<PotionPackItem> pack(String name, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        DeferredItem<PotionPackItem> item = ITEMS.register(name, () -> new PotionPackItem(new Item.Properties(), potion));
        PACKS.add(item);
        return item;
    }

    public static List<DeferredItem<PotionPackItem>> packHolders() {
        return Collections.unmodifiableList(PACKS);
    }

    public static List<PotionPackItem> packs() {
        List<PotionPackItem> items = new ArrayList<>(PACKS.size());
        for (DeferredHolder<Item, PotionPackItem> holder : PACKS) {
            items.add(holder.get());
        }
        return items;
    }

    @SubscribeEvent
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        // 只需添加药包；三种瓶型里的自制药水由原版 CreativeModeTabs 自动遍历药水注册表添加，
        // 这里再加一遍会在创造模式栏/搜索里出现重复条目。
        if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(REAGENT_SWORD);
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(MILK_BOTTLE);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (DeferredItem<PotionPackItem> pack : PACKS) {
                event.accept(pack);
            }
        }
    }
}

