package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.potion.EnvenomPotions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** Milk clears effects directly; it is not itself a status effect. */
public final class MilkHandler {
    private MilkHandler() {}

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractThrownPotion bottle) || bottle.level().isClientSide()) return;
        var item = bottle.getItem();
        var contents = item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (!contents.is(EnvenomPotions.MILK)) return;
        event.setCanceled(true);
        if (item.is(Items.LINGERING_POTION)) {
            var cloud = new AreaEffectCloud(bottle.level(), bottle.getX(), bottle.getY(), bottle.getZ());
            if (bottle.getOwner() instanceof LivingEntity owner) cloud.setOwner(owner);
            cloud.setRadius(3.0F);
            cloud.setRadiusOnUse(-0.5F);
            cloud.setWaitTime(10);
            cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
            cloud.applyComponentsFromItemStack(item);
            bottle.level().addFreshEntity(cloud);
        } else {
            for (var target : bottle.level().getEntitiesOfClass(LivingEntity.class,
                    bottle.getBoundingBox().inflate(4.0, 2.0, 4.0))) {
                if (target.isAffectedByPotions() && bottle.distanceToSqr(target) < 16.0) target.removeAllEffects();
            }
        }
        bottle.level().levelEvent(2007, bottle.blockPosition(), 0xFFFFFF);
        bottle.discard();
    }

    @SubscribeEvent
    public static void onDrink(LivingEntityUseItemEvent.Finish event) {
        if (!event.getEntity().level().isClientSide()
                && event.getItem().is(Items.POTION)
                && event.getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(EnvenomPotions.MILK)) {
            event.getEntity().removeAllEffects();
        }
    }
}
