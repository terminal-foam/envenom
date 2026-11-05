package com.terminalfoam.envenom.item;

import com.terminalfoam.envenom.handler.CoatingEffectHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.entity.PartEntity;

/** Delivers coatings without entering vanilla melee damage or fire-aspect processing. */
public final class ReagentSwordItem extends SwordItem {
    public ReagentSwordItem(Properties properties) {
        super(Tiers.WOOD, properties.attributes(SwordItem.createAttributes(Tiers.WOOD, -1, -2.4F)));
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity hit) {
        Entity target = hit instanceof PartEntity<?> part ? part.getParent() : hit;
        if (player instanceof ServerPlayer serverPlayer && !player.isSpectator()
                && target instanceof LivingEntity living && living != player
                && living.isAlive() && !living.isRemoved() && !living.isInvulnerable()
                && living.isAttackable() && !living.skipAttackInteraction(player)) {
            if (living instanceof Player other
                    && (!player.canHarmPlayer(other) || other.getAbilities().invulnerable)) return true;
            CoatingEffectHandler.applyReagentSwordCoatings(serverPlayer, living, stack);
            player.resetAttackStrengthTicker();
            stack.hurtAndBreak(1, serverPlayer, EquipmentSlot.MAINHAND);
        }
        return true;
    }
}
