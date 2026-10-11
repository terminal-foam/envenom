package com.terminalfoam.envenom.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 末影龙默认只接受"来源是玩家"或 ALWAYS_HURTS_ENDER_DRAGONS 标签的伤害,
 * 因此注射附魔施加的药水效果(中毒/瞬间伤害等魔法伤害,来源实体为 null)即使
 * 成功挂上也不造成伤害——伤害在 EnderDragon.hurt 里被直接吞掉。
 * 这里放行无实体的魔法/凋零伤害,让"注射"的伤害真正生效。
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonHurtMixin {

    @Shadow
    public abstract EnderDragonPhaseManager getPhaseManager();

    @Invoker("reallyHurt")
    protected abstract boolean envenom$reallyHurt(DamageSource source, float amount);

    @Inject(method = "hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), cancellable = true)
    private void envenom$allowEffectDamage(EnderDragonPart part, DamageSource source, float damage,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (this.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            return; // 死亡阶段保持原逻辑
        }
        // 无实体的魔法/凋零伤害(药水效果伤害)放行;仅在实际造成伤害时吞掉原逻辑
        if (source.getEntity() == null && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER))
                && this.envenom$reallyHurt(source, damage)) {
            cir.setReturnValue(true);
        }
    }
}