package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.coating.Coating;
import com.terminalfoam.envenom.coating.CoatingSlot;
import com.terminalfoam.envenom.coating.EnvenomDataComponents;
import com.terminalfoam.envenom.config.EnvenomConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

public class CoatingApplicationHandler {

    // ── 清洗：水瓶去一层，牛奶去全部，蜂蜜去负面 ──
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        // 清洗逻辑（优先级高于涂药）
        ItemStack cleaner = null;
        ItemStack cleanTarget = null;
        InteractionHand cleanerHand = null;

        if (isCleaner(off) && hasCoatings(main)) {
            cleaner = off;
            cleanTarget = main;
            cleanerHand = InteractionHand.OFF_HAND;
        } else if (isCleaner(main) && hasCoatings(off)) {
            cleaner = main;
            cleanTarget = off;
            cleanerHand = InteractionHand.MAIN_HAND;
        }

        if (cleaner != null) {
            event.setCanceled(true);
            if (cleaner.is(Items.MILK_BUCKET)) {
                removeAllCoatings(cleanTarget);
            } else if (cleaner.is(Items.HONEY_BOTTLE)) {
                removeHarmfulCoatings(cleanTarget);
            } else if (isWaterBottle(cleaner)) {
                removeOneCoating(cleanTarget);
            }
            // 消耗清洗物品（牛奶桶不消耗）
            // ⚠️ 不能整手替换成一个玻璃瓶：蜂蜜瓶在原版是 stacksTo(16) 的，
            //    整栈替换会把同栈里剩下的最多 15 瓶一并销毁。见 consumeOneAndReturnBottle。
            if (!cleaner.is(Items.MILK_BUCKET)) {
                consumeOneAndReturnBottle(player, cleanerHand);
            }
            return;
        }

        boolean mainIsPotion = isPotionItem(main);
        boolean offIsPotion = isPotionItem(off);

        CoatingSlot slot = null;
        ItemStack target;
        ItemStack potion;
        InteractionHand potionHand;

        if (offIsPotion && determineSlot(main) != null && hasPotionEffects(off)) {
            slot = determineSlot(main);
            target = main;
            potion = off;
            potionHand = InteractionHand.OFF_HAND;
        } else if (mainIsPotion && isCoatingWeapon(off) && hasPotionEffects(main)) {
            slot = CoatingSlot.HILT;
            target = off;
            potion = main;
            potionHand = InteractionHand.MAIN_HAND;
        } else {
            return;
        }

        event.setCanceled(true);
        applyFromPotion(player, target, potion, slot);

        if (potion.is(Items.POTION)) {
            player.setItemInHand(potionHand, new ItemStack(Items.GLASS_BOTTLE));
        } else {
            potion.shrink(1);
        }
    }

    private static boolean isPotionItem(ItemStack stack) {
        return stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION);
    }

    /** 药水是否带有效果。普通水瓶等无效果药水不能用于涂药,避免被白白消耗。 */
    private static boolean hasPotionEffects(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return contents.getAllEffects().iterator().hasNext();
    }

    private static CoatingSlot determineSlot(ItemStack stack) {
        if (isCoatingWeapon(stack)) return CoatingSlot.BLADE;
        if (isCoatingArmor(stack)) return CoatingSlot.ARMOR;
        return null;
    }

    public static boolean isCoatingWeapon(ItemStack stack) {
        Item item = stack.getItem();
        // 排除弓、弩、重锤
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof MaceItem) return false;
        // 原版武器/工具基类 → 自动支持
        if (item instanceof SwordItem || item instanceof TridentItem || item instanceof DiggerItem) return true;
        // MOD 物品：通过原版标签识别
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES);
    }

    /**
     * 这件物品能不能作为「盔甲涂层」的载体？
     *
     * <p><b>C6：判定必须与触发侧的扫描范围同源。</b>
     * {@code CoatingEffectHandler.onEntityTick} 只扫 {@code EquipmentSlot.Type.HUMANOID_ARMOR}
     * 的槽位，所以能被涂的东西也必须是"穿在人形盔甲槽里的"。
     *
     * <p>旧的 {@code instanceof ArmorItem} 有个漏洞：{@code AnimalArmorItem extends ArmorItem}
     * （马铠、狼铠都是），于是动物铠甲也能被涂上涂层 —— 但它永远穿不到人形盔甲槽上，
     * <b>涂了永不触发</b>，纯属白费玩家的药水。用 {@code getEquipmentSlot().getType()}
     * 直接不问"是不是 ArmorItem"，而问"穿在哪个槽"。
     *
     * <p>顺带覆盖：{@code ArmorItem.Type.BODY}（动物铠甲用的）映射到 {@code EquipmentSlot.BODY}，
     * 其 Type 不是 HUMANOID_ARMOR ⇒ 被排除；而鞘翅与雕刻南瓜是原版特例（它们能穿在胸甲/头盔槽
     * 却不继承 ArmorItem），仍然显式放行。
     */
    public static boolean isCoatingArmor(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getEquipmentSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        }
        return stack.is(Items.ELYTRA) || stack.is(Items.CARVED_PUMPKIN);
    }

    private static void applyFromPotion(Player player, ItemStack target, ItemStack potionSource, CoatingSlot slot) {
        PotionContents contents = potionSource.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        boolean isSplash = potionSource.is(Items.SPLASH_POTION);
        boolean isLingering = potionSource.is(Items.LINGERING_POTION);
        int defaultUses;
        if (isLingering) {
            defaultUses = EnvenomConfig.defaultUsesLingering;
        } else if (isSplash) {
            defaultUses = EnvenomConfig.defaultUsesSplash;
        } else {
            defaultUses = EnvenomConfig.defaultUsesNormal;
        }
        applyCoatingsFromContents(player, target, contents, isLingering, defaultUses, slot);
    }

    private static void applyCoatingsFromContents(Player player, ItemStack target, PotionContents contents,
                                                   boolean isLingering, int defaultUses, CoatingSlot slot) {
        List<Coating> coatings = new ArrayList<>(
                target.getOrDefault(EnvenomDataComponents.COATINGS, List.of())
        );

        for (MobEffectInstance effectInstance : contents.getAllEffects()) {
            Coating newCoating = new Coating(
                    effectInstance.getEffect(),
                    effectInstance.getAmplifier(),
                    effectInstance.getDuration(),
                    defaultUses,
                    slot,
                    isLingering
            );

            boolean merged = false;
            for (int i = 0; i < coatings.size(); i++) {
                Coating existing = coatings.get(i);
                if (existing.effect().equals(newCoating.effect())
                        && existing.slot() == newCoating.slot()
                        && existing.lingering() == newCoating.lingering()) {
                    if (newCoating.amplifier() > existing.amplifier()) {
                        coatings.set(i, newCoating);
                    } else if (newCoating.amplifier() == existing.amplifier()) {
                        coatings.set(i, existing.withRemainingUses(defaultUses));
                    }
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                coatings.add(newCoating);
            }
        }

        target.set(EnvenomDataComponents.COATINGS, coatings);
    }

    // ── 清洗辅助 ──

    /**
     * 消耗手里 1 个清洗物，并按原版「喝完返瓶」的语义把玻璃瓶还回去。
     *
     * <p><b>为什么不能直接</b> {@code setItemInHand(hand, new ItemStack(GLASS_BOTTLE))}：
     * 蜂蜜瓶在原版是 {@code stacksTo(16)}（{@code Items.HONEY_BOTTLE} 注册时的
     * {@code Properties.stacksTo(16)}）。整手替换会把同一栈里剩下的 <b>最多 15 瓶</b>一起销毁
     * —— 玩家的实际损失，而且创造模式也一样。
     *
     * <p>水瓶 / 喷溅 / 滞留是 {@code stacksTo(1)}，那条路径上「整手替换」恰好等价于
     * 「扣 1 + 返瓶」，所以缺陷只暴露在蜂蜜瓶上；但修法对任何可堆叠情形都成立。
     *
     * <p><b>为什么只在服务端执行</b>：{@code PlayerInteractEvent.RightClickItem} 在
     * {@code MultiPlayerGameMode:391}（客户端）与 {@code ServerPlayerGameMode:312}（服务端）
     * 各发一次。若在客户端也 {@code getInventory().add(...)}，会造出服务端并不存在的幽灵物品
     * （服务端的槽位同步不一定覆盖那个槽，幽灵会残留）。服务端改完同步下去即可。
     */
    private static void consumeOneAndReturnBottle(Player player, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        stack.shrink(1);
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty()) {
            // 水/药水是 stacksTo(1)：扣完就空，直接把空瓶放回这只手
            player.setItemInHand(hand, bottle);
            return;
        }
        // 栈里还有剩余（蜂蜜瓶）⇒ 空瓶进背包；背包满就掉在脚下
        if (!player.getInventory().add(bottle)) {
            player.drop(bottle, false);
        }
    }

    private static boolean hasCoatings(ItemStack stack) {
        List<Coating> coatings = stack.getOrDefault(EnvenomDataComponents.COATINGS, List.of());
        return !coatings.isEmpty();
    }

    private static boolean isCleaner(ItemStack stack) {
        return isWaterBottle(stack) || stack.is(Items.MILK_BUCKET) || stack.is(Items.HONEY_BOTTLE);
    }

    private static boolean isWaterBottle(ItemStack stack) {
        if (!stack.is(Items.POTION)) return false;
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return contents.potion().isPresent() && contents.potion().get().is(Potions.WATER);
    }

    private static void removeOneCoating(ItemStack stack) {
        List<Coating> coatings = new ArrayList<>(
                stack.getOrDefault(EnvenomDataComponents.COATINGS, List.of())
        );
        if (!coatings.isEmpty()) {
            coatings.removeLast();
            stack.set(EnvenomDataComponents.COATINGS, coatings.isEmpty() ? null : coatings);
        }
    }

    private static void removeAllCoatings(ItemStack stack) {
        stack.remove(EnvenomDataComponents.COATINGS);
    }

    private static void removeHarmfulCoatings(ItemStack stack) {
        List<Coating> coatings = stack.getOrDefault(EnvenomDataComponents.COATINGS, List.of());
        List<Coating> kept = new ArrayList<>();
        for (Coating coating : coatings) {
            if (coating.effect().value().getCategory() != MobEffectCategory.HARMFUL) {
                kept.add(coating);
            }
        }
        stack.set(EnvenomDataComponents.COATINGS, kept.isEmpty() ? null : kept);
    }
}
