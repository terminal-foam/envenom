package com.terminalfoam.envenom.mixin;

import com.terminalfoam.envenom.effect.EnvenomEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 蛋糕和插蜡烛的蛋糕都走 {@link CakeBlock#eat}。
 * 只拦这一口，不取消整个右键，这样插蜡烛、点蜡烛、灭蜡烛仍然可用。
 */
@Mixin(CakeBlock.class)
public abstract class CakeEatMixin {
    @Inject(method = "eat", at = @At("HEAD"), cancellable = true)
    private static void envenom$blockCakeWhileNauseous(
            LevelAccessor level, BlockPos pos, BlockState state, Player player, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.hasEffect(EnvenomEffects.NAUSEA)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
