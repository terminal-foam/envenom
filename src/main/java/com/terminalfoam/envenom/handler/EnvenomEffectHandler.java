package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.config.EnvenomConfig;
import com.terminalfoam.envenom.effect.EnvenomEffects;
import com.terminalfoam.envenom.effect.NauseaRules;
import com.terminalfoam.envenom.effect.VulnerabilityMath;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** 反胃的持续限制，以及易伤的受伤乘区。 */
public final class EnvenomEffectHandler {
    private EnvenomEffectHandler() {
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        // onEffectAdded 已经清过一次。这里再清，是为了覆盖「效果被刷新」的路径
        // （更强或同级覆盖不一定再调 MobEffect.onEffectAdded）。
        if (event.getEffectInstance().getEffect().value() != EnvenomEffects.NAUSEA.get()) {
            return;
        }
        NauseaRules.purgeOnApply(event.getEntity());
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        MobEffectInstance incoming = event.getEffectInstance();
        if (!incoming.getEffect().value().isBeneficial()) {
            return;
        }
        if (event.getEntity().hasEffect(EnvenomEffects.NAUSEA)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        blockIngestion(event.getEntity(), event.getItem(), event);
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        // 效果中途加上去时，打断已经开始的进食 / 饮用。
        // 不能只取消 Tick：1.21.1 里取消会把剩余时间写成 -1，紧接着的
        // updateUsingItem 把它当成「用完了」并调用 finishUsingItem，这一口照样吃下去。
        // 先 stopUsingItem 清掉正在使用的堆叠，随后的 completeUsingItem 对不上手里的物品，就不会消耗。
        if (!shouldBlock(event.getEntity(), event.getItem())) {
            return;
        }
        event.getEntity().stopUsingItem();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance vulnerability = entity.getEffect(EnvenomEffects.VULNERABILITY);
        if (vulnerability == null) {
            return;
        }
        float scaled = VulnerabilityMath.scaleDamage(
                event.getAmount(), vulnerability.getAmplifier(), EnvenomConfig.vulnerabilityPerLevel);
        event.setAmount(scaled);
    }

    private static void blockIngestion(LivingEntity entity, ItemStack stack, ICancellableEvent event) {
        if (shouldBlock(entity, stack)) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldBlock(LivingEntity entity, ItemStack stack) {
        return entity.hasEffect(EnvenomEffects.NAUSEA) && NauseaRules.isIngestion(entity, stack);
    }
}
