package com.terminalfoam.envenom.mixin;

import com.terminalfoam.envenom.potion.EnvenomPotions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AreaEffectCloud.class)
public abstract class MilkCloudMixin {
    @Shadow private PotionContents potionContents;
    @Shadow private int waitTime;
    @Shadow private int reapplicationDelay;
    @Shadow private float radiusOnUse;
    @Unique private final Map<UUID, Integer> envenom$milkVictims = new HashMap<>();

    @Inject(method = "tick", at = @At("TAIL"))
    private void envenom$clearEffects(CallbackInfo ci) {
        AreaEffectCloud cloud = (AreaEffectCloud) (Object) this;
        if (cloud.level().isClientSide() || cloud.isRemoved() || !potionContents.is(EnvenomPotions.MILK)
                || cloud.tickCount < waitTime || cloud.tickCount % 5 != 0) return;
        envenom$milkVictims.values().removeIf(expiry -> expiry <= cloud.tickCount);
        for (LivingEntity target : cloud.level().getEntitiesOfClass(LivingEntity.class, cloud.getBoundingBox())) {
            if (!target.isAffectedByPotions() || envenom$milkVictims.containsKey(target.getUUID())) continue;
            double dx = target.getX() - cloud.getX(), dz = target.getZ() - cloud.getZ();
            if (dx * dx + dz * dz > cloud.getRadius() * cloud.getRadius()) continue;
            target.removeAllEffects();
            envenom$milkVictims.put(target.getUUID(), cloud.tickCount + reapplicationDelay);
            float radius = cloud.getRadius() + radiusOnUse;
            if (radius < 0.5F) { cloud.discard(); return; }
            cloud.setRadius(radius);
        }
    }
}
