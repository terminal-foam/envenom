package com.terminalfoam.envenom.advancement;

import com.terminalfoam.envenom.effect.EnvenomEffects;
import com.terminalfoam.envenom.item.EnvenomItems;
import com.terminalfoam.envenom.item.PotionPackItem;
import com.terminalfoam.envenom.potion.EnvenomPotions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/** Server-side achievement triggers for the {@code data/envenom/advancement} tab. */
public final class AdvancementHandler {
    private static final String COLLECTED_KEY = "envenom_collected_potions";
    private static final int SCAN_INTERVAL = 20;

    private static Set<String> requiredPotions;
    private static List<Holder<MobEffect>> allEffects;

    private AdvancementHandler() {
    }

    // ── 药水 / 反胃 ─────────────────────────────────────────

    @SubscribeEvent
    public static void onPotionFinished(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        ItemStack stack = event.getItem();
        if (!stack.is(Items.POTION)) return;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return;
        for (MobEffectInstance instance : contents.getAllEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                AdvancementHelper.award(player, "bad_potion");
                break;
            }
        }
        recordPotion(player, stack);
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        MobEffectInstance instance = event.getEffectInstance();
        Entity source = event.getEffectSource();
        ServerPlayer sourcePlayer = source instanceof ServerPlayer player ? player : null;

        if (entity instanceof ServerPlayer player && instance.getEffect().is(EnvenomEffects.NAUSEA)) {
            AdvancementHelper.award(player, "gain_nausea");
        }

        if (instance.getEffect().is(MobEffects.POISON)) {
            ServerPlayer player = sourcePlayer != null ? sourcePlayer
                    : (entity instanceof ServerPlayer serverPlayer ? serverPlayer : null);
            if (instance.getAmplifier() >= 9) AdvancementHelper.award(player, "poison_10");
            if (instance.getAmplifier() >= 99) AdvancementHelper.award(player, "poison_30");
        }

        if (sourcePlayer != null && entity instanceof Enemy && !(entity instanceof EnderDragon)
                && hasAllEffects(entity)) {
            AdvancementHelper.award(sourcePlayer, "hostile_all_effects");
        }
        if (sourcePlayer != null && entity instanceof EnderDragon && hasAllEffects(entity)) {
            AdvancementHelper.award(sourcePlayer, "dragon_all_effects");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer player
                && event.getEffect().is(EnvenomEffects.NAUSEA)
                && !player.isDeadOrDying()) {
            AdvancementHelper.award(player, "cure_nausea");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer player
                && event.getEffectInstance().getEffect().is(EnvenomEffects.NAUSEA)
                && !player.isDeadOrDying()) {
            AdvancementHelper.award(player, "cure_nausea");
        }
    }

    // ── 合成 / 涂层 / 交易 ─────────────────────────────────

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getCrafting().getItem() instanceof PotionPackItem) {
            AdvancementHelper.award(player, "first_pack");
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Witch)) return;
        ServerPlayer player = potionKiller(event.getSource());
        if (player != null) {
            AdvancementHelper.award(player, "potion_kill_witch");
        }
    }

    // ── 背包扫描：牛奶瓶、喷溅/滞留牛奶、药剂收藏家 ─────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        if (player.tickCount % SCAN_INTERVAL != 0) return;

        boolean hasMilkBottle = false;
        boolean hasMilkPotion = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(EnvenomItems.MILK_BOTTLE.get())) {
                hasMilkBottle = true;
            }
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.is(EnvenomPotions.MILK)
                    && (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION))) {
                hasMilkPotion = true;
            }
            recordPotion(player, stack);
        }
        if (hasMilkBottle) AdvancementHelper.award(player, "milk_bottle");
        if (hasMilkPotion) AdvancementHelper.award(player, "milk_potion");
    }

    // ── 工具 ────────────────────────────────────────────────

    private static void recordPotion(ServerPlayer player, ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) return;
        String id = contents.potion().get().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(null);
        if (id == null || !requiredPotions().contains(id)) return;

        CompoundTag data = player.getPersistentData();
        ListTag list = data.getListOrEmpty(COLLECTED_KEY);
        Set<String> collected = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            collected.add(list.getStringOr(i, ""));
        }
        if (!collected.add(id)) return;

        ListTag updated = new ListTag();
        for (String value : collected) {
            updated.add(StringTag.valueOf(value));
        }
        data.put(COLLECTED_KEY, updated);

        if (collected.containsAll(requiredPotions())) {
            AdvancementHelper.award(player, "potion_collector");
        }
    }

    /** All vanilla potions that actually have effects (luck is creative-only, so excluded). */
    private static Set<String> requiredPotions() {
        Set<String> cached = requiredPotions;
        if (cached != null) return cached;
        Set<String> ids = new HashSet<>();
        for (Identifier id : BuiltInRegistries.POTION.keySet()) {
            if (!"minecraft".equals(id.getNamespace()) || "luck".equals(id.getPath())) continue;
            Potion potion = BuiltInRegistries.POTION.get(id).map(Holder.Reference::value).orElse(null);
            if (potion != null && !potion.getEffects().isEmpty()) {
                ids.add(id.toString());
            }
        }
        requiredPotions = Set.copyOf(ids);
        return requiredPotions;
    }

    /** The vanilla effects required by {@code minecraft:nether/all_effects}. */
    private static List<Holder<MobEffect>> allEffects() {
        List<Holder<MobEffect>> cached = allEffects;
        if (cached != null) return cached;
        List<Holder<MobEffect>> effects = new ArrayList<>(34);
        effects.add(MobEffects.SPEED);
        effects.add(MobEffects.SLOWNESS);
        effects.add(MobEffects.HASTE);
        effects.add(MobEffects.MINING_FATIGUE);
        effects.add(MobEffects.STRENGTH);
        effects.add(MobEffects.JUMP_BOOST);
        effects.add(MobEffects.NAUSEA);
        effects.add(MobEffects.REGENERATION);
        effects.add(MobEffects.RESISTANCE);
        effects.add(MobEffects.FIRE_RESISTANCE);
        effects.add(MobEffects.WATER_BREATHING);
        effects.add(MobEffects.INVISIBILITY);
        effects.add(MobEffects.BLINDNESS);
        effects.add(MobEffects.NIGHT_VISION);
        effects.add(MobEffects.HUNGER);
        effects.add(MobEffects.WEAKNESS);
        effects.add(MobEffects.POISON);
        effects.add(MobEffects.WITHER);
        effects.add(MobEffects.ABSORPTION);
        effects.add(MobEffects.GLOWING);
        effects.add(MobEffects.LEVITATION);
        effects.add(MobEffects.SLOW_FALLING);
        effects.add(MobEffects.CONDUIT_POWER);
        effects.add(MobEffects.DOLPHINS_GRACE);
        effects.add(MobEffects.BAD_OMEN);
        effects.add(MobEffects.HERO_OF_THE_VILLAGE);
        effects.add(MobEffects.DARKNESS);
        effects.add(MobEffects.TRIAL_OMEN);
        effects.add(MobEffects.RAID_OMEN);
        effects.add(MobEffects.WIND_CHARGED);
        effects.add(MobEffects.WEAVING);
        effects.add(MobEffects.OOZING);
        effects.add(MobEffects.INFESTED);
        effects.add(MobEffects.BREATH_OF_THE_NAUTILUS);
        allEffects = List.copyOf(effects);
        return allEffects;
    }

    private static boolean hasAllEffects(LivingEntity entity) {
        for (Holder<MobEffect> effect : allEffects()) {
            if (!entity.hasEffect(effect)) return false;
        }
        return true;
    }

    @Nullable
    private static ServerPlayer potionKiller(DamageSource source) {
        if (!source.is(DamageTypes.INDIRECT_MAGIC)) return null;
        ServerPlayer player = asPlayer(source.getEntity());
        if (player != null) return player;
        player = asPlayer(source.getDirectEntity());
        if (player != null) return player;
        player = ownerPlayer(source.getEntity());
        if (player != null) return player;
        return ownerPlayer(source.getDirectEntity());
    }

    @Nullable
    private static ServerPlayer asPlayer(@Nullable Entity entity) {
        return entity instanceof ServerPlayer player ? player : null;
    }

    @Nullable
    private static ServerPlayer ownerPlayer(@Nullable Entity entity) {
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        if (entity instanceof AreaEffectCloud cloud && cloud.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
