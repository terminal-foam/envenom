package com.terminalfoam.envenom.trade;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class WitchTradingHandler {
    private static final Map<Mob, WitchMerchant> SESSIONS = new IdentityHashMap<>();
    private WitchTradingHandler() {}

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity().getType() == EntityType.IRON_GOLEM
                && event.getNewAboutToBeSetTarget() != null
                && event.getNewAboutToBeSetTarget().getType() == EntityType.WITCH) {
            event.setNewAboutToBeSetTarget(null);
            return;
        }
        // Retain vanilla retaliation, but suppress unprovoked attacks on players (including raids).
        if (event.getEntity().getType() == EntityType.WITCH
                && event.getNewAboutToBeSetTarget() instanceof Player player
                && event.getEntity().getLastHurtByMob() != player) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        excludeFromRaids(event.getEntity());
    }

    @SubscribeEvent
    public static void beforeTick(EntityTickEvent.Pre event) {
        // Run before raider AI can recruit nearby mobs or act on a raid loaded from a save.
        excludeFromRaids(event.getEntity());
    }

    private static void excludeFromRaids(Entity entity) {
        if (entity.level().isClientSide() || entity.getType() != EntityType.WITCH
                || !(entity instanceof Raider witch)) return;
        witch.setCanJoinRaid(false);
        var raid = witch.getCurrentRaid();
        if (raid != null) {
            // Keep the witch alive, but remove its contribution to the raid health/count.
            raid.removeFromRaid(witch, true);
            witch.setCurrentRaid(null);
            witch.setWave(0);
            witch.setTarget(null);
            witch.getNavigation().stop();
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget().getType() != EntityType.WITCH
                || !(event.getTarget() instanceof Mob witch)
                || event.getHand() != InteractionHand.MAIN_HAND
                || event.getEntity().isSpectator() || event.getEntity().isSecondaryUseActive()) return;
        // 命名牌 / 栓绳保留原版实体交互，不被交易拦截。
        if (event.getItemStack().is(Items.NAME_TAG) || event.getItemStack().is(Items.LEAD)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            // 客户端也消费这次右键：否则拿喷溅药水右键女巫会“开店的同时把药水扔出去”。
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        WitchMerchant existing = SESSIONS.get(witch);
        if (existing != null && existing.tick()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return; // Only one customer can own the stock at a time.
        }
        SESSIONS.remove(witch);
        if (!WitchMerchant.canTrade(witch, player)) return; // angry/hurt: leave vanilla interaction untouched
        WitchMerchant merchant = new WitchMerchant(witch);
        merchant.open(player);
        if (merchant.getTradingPlayer() != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            SESSIONS.put(witch, merchant);
        }
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()) {
            WitchMerchant merchant = SESSIONS.get(mob);
            if (merchant != null && !merchant.tick()) SESSIONS.remove(mob);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        WitchMerchant merchant = SESSIONS.remove(event.getEntity());
        if (merchant != null) merchant.close();
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) { SESSIONS.clear(); }
}
