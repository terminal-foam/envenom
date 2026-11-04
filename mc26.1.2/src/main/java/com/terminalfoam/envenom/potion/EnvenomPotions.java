package com.terminalfoam.envenom.potion;

import com.terminalfoam.envenom.Envenom;
import com.terminalfoam.envenom.effect.EnvenomEffects;
import com.terminalfoam.envenom.item.EnvenomItems;
import com.terminalfoam.envenom.item.PotionPackItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 动若脱兔：神龟大师的对偶。
 *
 * <p>神龟大师是缓慢 + 抗性，这一瓶是速度 + 易伤，时长和等级对齐：
 * <ul>
 *   <li>普通：速度 IV + 易伤 III，20 秒（400 tick）</li>
 *   <li>延长：同样等级，40 秒（800 tick）</li>
 *   <li>强化：速度 VI + 易伤 IV，20 秒</li>
 * </ul>
 * 喷溅、滞留、药箭沿用原版容器配方，时长由原版按瓶型缩放。
 */
public final class EnvenomPotions {
    /** 与原版神龟大师普通瓶一致。 */
    public static final int BASE_DURATION = 400;
    /** 与原版神龟大师延长瓶一致。 */
    public static final int LONG_DURATION = 800;

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, Envenom.MODID);

    public static final DeferredHolder<Potion, Potion> SWIFT_AS_A_HARE = POTIONS.register(
            "swift_as_a_hare",
            () -> new Potion(
                    "swift_as_a_hare",
                    new MobEffectInstance(MobEffects.SPEED, BASE_DURATION, 3),
                    new MobEffectInstance(EnvenomEffects.VULNERABILITY, BASE_DURATION, 2)));

    public static final DeferredHolder<Potion, Potion> LONG_SWIFT_AS_A_HARE = POTIONS.register(
            "long_swift_as_a_hare",
            () -> new Potion(
                    "long_swift_as_a_hare",
                    new MobEffectInstance(MobEffects.SPEED, LONG_DURATION, 3),
                    new MobEffectInstance(EnvenomEffects.VULNERABILITY, LONG_DURATION, 2)));

    public static final DeferredHolder<Potion, Potion> STRONG_SWIFT_AS_A_HARE = POTIONS.register(
            "strong_swift_as_a_hare",
            () -> new Potion(
                    "strong_swift_as_a_hare",
                    new MobEffectInstance(MobEffects.SPEED, BASE_DURATION, 5),
                    new MobEffectInstance(EnvenomEffects.VULNERABILITY, BASE_DURATION, 3)));

    /** 牛奶瓶：瞬间清除全部效果。喷溅 / 滞留型由原版火药 / 龙息容器配方自动派生。 */
    public static final DeferredHolder<Potion, Potion> MILK = POTIONS.register(
            "milk",
            () -> new Potion("milk", new MobEffectInstance(EnvenomEffects.MILK, 1, 0)));

    /** 基岩版「衰变」：凋零 II，40 秒（喷溅同长；滞留云按原版缩为 1/4，即 10 秒）。 */
    public static final DeferredHolder<Potion, Potion> DECAY = POTIONS.register(
            "decay",
            () -> new Potion("decay", new MobEffectInstance(MobEffects.WITHER, 800, 1)));

    private EnvenomPotions() {
    }

    @SubscribeEvent
    public static void registerBrewing(RegisterBrewingRecipesEvent event) {
        var builder = event.getBuilder();

        // 腐化：神龟大师 + 发酵蜘蛛眼。延长 / 强化瓶各自腐化，和夜视 → 隐身同一惯例。
        builder.addMix(Potions.TURTLE_MASTER, Items.FERMENTED_SPIDER_EYE, SWIFT_AS_A_HARE);
        builder.addMix(Potions.LONG_TURTLE_MASTER, Items.FERMENTED_SPIDER_EYE, LONG_SWIFT_AS_A_HARE);
        builder.addMix(Potions.STRONG_TURTLE_MASTER, Items.FERMENTED_SPIDER_EYE, STRONG_SWIFT_AS_A_HARE);

        // 腐化之后仍可按原版路径延长 / 强化。
        builder.addMix(SWIFT_AS_A_HARE, Items.REDSTONE, LONG_SWIFT_AS_A_HARE);
        builder.addMix(SWIFT_AS_A_HARE, Items.GLOWSTONE_DUST, STRONG_SWIFT_AS_A_HARE);

        // 基岩版衰变：粗制药水 + 凋零玫瑰 → 凋零 II。喷溅 / 滞留 / 药箭沿用原版派生。
        builder.addMix(Potions.AWKWARD, Items.WITHER_ROSE, DECAY);

        // 牛奶瓶 + 火药 → 喷溅型牛奶瓶；滞留型再由原版「喷溅 + 龙息」派生。
        builder.addRecipe(new MilkSplashRecipe());

        // 水瓶 + 药包 → 对应药水（基础 / 延长 / 强化）；喷溅 / 滞留包走自定义容器配方。
        for (PotionPackItem pack : EnvenomItems.packs()) {
            if (pack.container() == PotionPackItem.Container.DRINKABLE) {
                builder.addMix(Potions.WATER, pack, pack.potion());
            } else {
                builder.addRecipe(new ContainerPackRecipe(pack));
            }
        }
    }

    /** 水瓶 + 喷溅 / 滞留药包 → 该药水的对应瓶型。 */
    private static final class ContainerPackRecipe implements IBrewingRecipe {
        private final PotionPackItem pack;

        private ContainerPackRecipe(PotionPackItem pack) {
            this.pack = pack;
        }

        @Override
        public boolean isInput(ItemStack input) {
            if (!input.is(Items.POTION)) {
                return false;
            }
            PotionContents contents = input.get(DataComponents.POTION_CONTENTS);
            return contents == null
                    || contents.potion().isEmpty()
                    || contents.potion().get().value() == Potions.WATER.value();
        }

        @Override
        public boolean isIngredient(ItemStack ingredient) {
            return ingredient.is(this.pack);
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            if (this.isInput(input) && this.isIngredient(ingredient)) {
                Item bottle = this.pack.splash() ? Items.SPLASH_POTION : Items.LINGERING_POTION;
                return PotionContents.createItemStack(bottle, this.pack.potion());
            }
            return ItemStack.EMPTY;
        }
    }

    /** 牛奶瓶 + 火药 → 喷溅型牛奶瓶。原版容器配方只认药水瓶，这里补上自定义瓶子。 */
    private static final class MilkSplashRecipe implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack input) {
            return input.is(EnvenomItems.MILK_BOTTLE.get());
        }

        @Override
        public boolean isIngredient(ItemStack ingredient) {
            return ingredient.is(Items.GUNPOWDER);
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            if (this.isInput(input) && this.isIngredient(ingredient)) {
                return PotionContents.createItemStack(Items.SPLASH_POTION, MILK);
            }
            return ItemStack.EMPTY;
        }
    }
}
