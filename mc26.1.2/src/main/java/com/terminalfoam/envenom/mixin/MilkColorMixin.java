package com.terminalfoam.envenom.mixin;

import com.terminalfoam.envenom.potion.EnvenomPotions;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep milk white even though its potion has no status effects. */
@Mixin(PotionContents.class)
public abstract class MilkColorMixin {
    @Inject(method = "getColor()I", at = @At("HEAD"), cancellable = true)
    private void envenom$milkColor(CallbackInfoReturnable<Integer> cir) {
        if (((PotionContents) (Object) this).is(EnvenomPotions.MILK)) cir.setReturnValue(0xFFFFFFFF);
    }
}
