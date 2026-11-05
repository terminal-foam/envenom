package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.Envenom;
import com.terminalfoam.envenom.config.EnvenomConfig;
import com.terminalfoam.envenom.effect.EnvenomEffects;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 有害食物过量：短时间内吃太多 {@code envenom:harmful_foods} 里的东西会触发反胃。
 *
 * <p>用物品标签而不是写死列表，整合包可以用数据包往标签里加自己的“恶心食物”。
 * 记录只存在服务端内存里（每名玩家一个最近食用时间队列），退出时清掉。
 */
public final class HarmfulFoodHandler {
    public static final TagKey<Item> HARMFUL_FOODS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Envenom.MODID, "harmful_foods"));

    private static final Map<UUID, Deque<Long>> RECENT = new HashMap<>();

    @SubscribeEvent
    public static void onFinish(LivingEntityUseItemEvent.Finish event) {
        if (!EnvenomConfig.harmfulFoodEnabled) return;
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Player player)) return;
        if (!event.getItem().is(HARMFUL_FOODS)) return;

        long now = player.level().getGameTime();
        Deque<Long> times = RECENT.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        long cutoff = now - EnvenomConfig.harmfulFoodWindowTicks;
        while (!times.isEmpty() && times.peekFirst() <= cutoff) {
            times.pollFirst();
        }
        times.addLast(now);

        if (times.size() >= EnvenomConfig.harmfulFoodThreshold) {
            times.clear();
            player.addEffect(new MobEffectInstance(
                    EnvenomEffects.NAUSEA, EnvenomConfig.harmfulFoodNauseaTicks, 0));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RECENT.remove(event.getEntity().getUUID());
    }

    /** 测试与调试用：清掉某位玩家的记录。 */
    public static void clear(Player player) {
        RECENT.remove(player.getUUID());
    }
}
