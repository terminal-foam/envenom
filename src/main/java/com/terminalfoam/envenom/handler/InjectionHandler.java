package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.enchantment.EnvenomEnchantments;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * 注射附魔（injection）：让效果能作用于"硬编码拒绝效果"的生物。
 *
 * <p><b>要解决的问题</b>：原版有两个生物（末影龙、凋灵）在自己的类里无条件覆写了
 * {@code addEffect(MobEffectInstance, Entity)} 让它 {@code return false}。
 * 这种拒绝<b>不来自免疫机制</b> —— 它们的 {@code canBeAffected} 仍返回 true，
 * 所以查免疫查不出来。模组侧唯一的绕法是 {@code forceAddEffect}（直接 put 进效果表），
 * 而 {@code forceAddEffect} 自己也要过 {@code CommonHooks.canMobEffectBeApplied} 这道门。
 *
 * <p><b>本类的做法</b>：监听 {@code MobEffectEvent.Applicable}，当目标是"硬编码拒绝"类生物
 * 且攻击者手持注射附魔武器时，把结果改成 {@code APPLY} —— 于是那道门被打开，
 * 随后的 {@code forceAddEffect} 得以生效。
 *
 * <p><b>为什么用反射</b>：判断"有没有覆写 addEffect"没有别的办法。
 * 免疫那类（僵尸/蜘蛛/银鱼/史莱姆）走的是 {@code canBeAffected} / 免疫标签，
 * 反射判据<b>不会</b>把它们误判成硬编码拒绝，这是刻意的。
 *
 * <p><b>为什么排除亡灵</b>：亡灵的通用弱点是"瞬间治疗造成伤害"（{@code HealOrHarmMobEffect}
 * 里 {@code isHarm != isInvertedHealAndHarm} 走 hurt 分支），凋灵正属此类 ——
 * 它<b>另有对策</b>（滞留型瞬间治疗很痛），不需要注射。所以这里直接排除亡灵，让注射专攻末影龙。
 *
 * <p><b>溯源</b>：本类原先只以 class 形式存在于成品 jar 内（本机任何源码树都没有）。
 * 此处按成品字节码逐指令重建，验证结论见 {@code docs/evidence/C2-验证结论.md}。
 */
public class InjectionHandler {

   public InjectionHandler() {
   }

   @SubscribeEvent
   public static void onEffectApplicable(MobEffectEvent.Applicable event) {
      if (!isFullyEffectImmune(event.getEntity())) {
         return;
      }

      Entity effectSource = event.getEffectSource();
      if (effectSource instanceof LivingEntity) {
         LivingEntity source = (LivingEntity)effectSource;
         ItemStack weapon = source.getMainHandItem();
         RegistryAccess ra = source.level().registryAccess();
         if (EnchantmentHelper.getItemEnchantmentLevel(EnvenomEnchantments.injection(ra), weapon) > 0) {
            event.setResult(MobEffectEvent.Applicable.Result.APPLY);
         }
      }
   }

   /**
    * 目标是不是"无条件拒绝一切效果"的生物？
    *
    * <p>判据：{@code addEffect(MobEffectInstance, Entity)} 的声明类不是 {@link LivingEntity}
    * —— 说明有人覆写了它。原版只有 {@code EnderDragon} 与 {@code WitherBoss} 这么做。
    *
    * <p>{@code isInvertedHealAndHarm()}（= {@code #minecraft:undead} 标签，含凋灵、不含末影龙）
    * 为真时直接返回 false：亡灵走"瞬间治疗造成伤害"那条路，不需要注射。
    */
   private static boolean isFullyEffectImmune(LivingEntity target) {
      if (target.isInvertedHealAndHarm()) {
         return false;
      }

      try {
         return target.getClass().getMethod("addEffect", MobEffectInstance.class, Entity.class).getDeclaringClass() != LivingEntity.class;
      } catch (NoSuchMethodException e) {
         return false;
      }
   }
}
