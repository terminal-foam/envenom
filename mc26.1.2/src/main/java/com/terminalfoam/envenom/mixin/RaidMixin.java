package com.terminalfoam.envenom.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remove witches from both the fixed wave counts and randomized reinforcements. */
@Mixin(Raid.class)
public abstract class RaidMixin {
    // RaiderType is not public in both supported versions; Coerce keeps its enum name accessible.
    @Inject(method = "getDefaultNumSpawns", at = @At("HEAD"), cancellable = true)
    private void envenom$noWitchesInWave(@Coerce Object type, int wave, boolean bonusWave,
            CallbackInfoReturnable<Integer> cir) {
        if (((Enum<?>) type).name().equals("WITCH")) cir.setReturnValue(0);
    }

    @Inject(method = "getPotentialBonusSpawns", at = @At("HEAD"), cancellable = true)
    private void envenom$noBonusWitches(@Coerce Object type, RandomSource random, int wave,
            DifficultyInstance difficulty, boolean bonusWave, CallbackInfoReturnable<Integer> cir) {
        if (((Enum<?>) type).name().equals("WITCH")) cir.setReturnValue(0);
    }
}
