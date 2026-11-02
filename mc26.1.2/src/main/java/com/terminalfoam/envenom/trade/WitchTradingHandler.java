package com.terminalfoam.envenom.trade;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class WitchTradingHandler {
    private static final Map<Mob, WitchMerchant> SESSIONS = new IdentityHashMap<>();
    private WitchTradingHandler() {}

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        // Retain vanilla retaliation, but suppress unprovoked attacks on players (including raids).
        if (event.getEntity().getType() == EntityType.WITCH
                && event.getNewAboutToBeSetTarget() instanceof Player player
                && event.getEntity().getLastHurtByMob() != player) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget().getType() != EntityType.WITCH
                || !(event.getTarget() instanceof Mob witch)
                || event.getHand() != InteractionHand.MAIN_HAND
                || event.getEntity().isSpectator() || event.getEntity().isSecondaryUseActive()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        WitchMerchant existing = SESSIONS.get(witch);
        if (existing != null && existing.tick()) return; // Only one customer can own the stock at a time.
        SESSIONS.remove(witch);
        WitchMerchant merchant = new WitchMerchant(witch);
        if (!merchant.canTrade(player)) return;
        merchant.open(player);
        if (merchant.getTradingPlayer() != null) SESSIONS.put(witch, merchant);
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
