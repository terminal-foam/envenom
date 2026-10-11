package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.coating.Coating;
import com.terminalfoam.envenom.coating.CoatingSlot;
import com.terminalfoam.envenom.coating.EnvenomDataComponents;
import com.terminalfoam.envenom.enchantment.EnvenomEnchantments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Pre;
import org.jetbrains.annotations.Nullable;

public class CoatingEffectHandler {
   /** 默认触发间隔（30 秒）。 */
   private static final long TRIGGER_INTERVAL = 600L;
   /** 瞬间治疗/伤害涂层的护甲触发间隔（每秒 2 次）。 */
   private static final long INSTANT_ARMOR_INTERVAL = 10L;
   private static final List<Holder<MobEffect>> ALL_MOB_EFFECTS;
   private static final List<CoatingEffectHandler.PendingEffect> PENDING_EFFECTS = new ArrayList<>();

   public CoatingEffectHandler() {
   }

   private static Holder<MobEffect> randomEffect(RandomSource random) {
      return ALL_MOB_EFFECTS.get(random.nextInt(ALL_MOB_EFFECTS.size()));
   }

   /**
    * 冷却门：这个效果现在允许触发吗？
    *
    * <p>"现在时刻"（{@code now}）由事件传入，不在内部取，因为一次 tick 里三个调用点必须用同一个时刻。
    *
    * <p><b>为什么有 {@code last > now} 这个分支</b>：时间戳存在物品上，而 {@code Level.getGameTime()}
    * 是**存档持久化**的值（{@code PrimaryLevelData.gameTime}，写进存档 NBT 的 {@code Time}）。
    * 同一存档重启后它继续递增，所以时间戳可比。但物品可能被带到 {@code gameTime} **更小**的存档
    * —— 而且是**原版途径**：创造模式快捷栏存在客户端根目录的 {@code hotbar.nbt}
    * （{@code HotbarManager}，不在存档目录内），整份组件 NBT 原样存取，
    * 再经 {@code ServerboundSetCreativeModeSlotPacket} 送回服务端。世界 A → 世界 B、甚至服务器 A → 服务器 B
    * 都能把带旧时间戳的物品带过去。
    *
    * <p>此时 {@code now - last} 为负。**注意不能把负数夹到 0** —— {@code 0 >= interval} 仍然是
    * "冷却没走完"，涂层会永久失效（本档第一版就踩了这个坑：夹取是 no-op）。
    * 负数只可能来自"时间倒流"，而时间倒流意味着中间至少隔了一次存档切换，所以按
    * "**冷却早已结束**"处理既安全又符合直觉。放行之后 {@code mark} 会把该条目改写成当前时间，
    * 于是自动自愈 —— 这一步也依赖这里返回 true，否则永远走不到 {@code mark}。
    */
   private static boolean canTrigger(Map<String, Long> cooldowns, String key, long now, long interval) {
      long last = TriggerCooldown.last(cooldowns, key);
      if (last > now) {
         return true;
      }
      return now - last >= interval;
   }

   private static int calcAmpBonus(int level, RandomSource random) {
      if (level <= 0) {
         return 0;
      } else {
         int roll = random.nextInt(5);
         return roll < level ? roll + 1 : 0;
      }
   }

   /**
    * 叠层要给效果抬到的最短时长：30 秒地板，**但无限时长原样保留**。
    *
    * <p><b>{@code -1} 是原版的"无限"哨兵值，不能参与数值比较</b>：
    * 旧写法 {@code Math.max(600, -1)} 会得到 600 —— 也就是**把无限效果砍成 30 秒**，
    * 是"叠层一次反而变弱"的来源（审计编号 F-d）。
    */
   private static int stackingDuration(int current) {
      return current == -1 ? -1 : Math.max(600, current);
   }

   @SubscribeEvent
   public static void onServerTick(Pre event) {
      List<CoatingEffectHandler.PendingEffect> batch = new ArrayList<>(PENDING_EFFECTS);
      PENDING_EFFECTS.clear();

      for (CoatingEffectHandler.PendingEffect pending : batch) {
         Level entity = pending.level;
         if (entity instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)entity;
            if (serverLevel.getEntity(pending.targetId) instanceof LivingEntity target) {
               applyEffect(target, pending, pending.multiplier);
            }
         }
      }
   }

   private static void applyEffect(LivingEntity target, CoatingEffectHandler.PendingEffect pending, int multiplier) {
      MobEffect mob = (MobEffect)pending.effect.value();
      boolean isHeal = pending.effect.is(MobEffects.HEAL);
      boolean isHarm = pending.effect.is(MobEffects.HARM);
      if (mob.isInstantenous() && (isHeal || isHarm)) {
         float amount = 3.0F * (float)(1 << pending.amplifier);
         boolean undead = target.isInvertedHealAndHarm();
         boolean dealDamage = isHeal && undead || isHarm && !undead;

         for (int i = 0; i < multiplier; i++) {
            if (dealDamage) {
               target.invulnerableTime = 0;
               target.hurt(target.damageSources().magic(), amount);
            } else {
               target.heal(amount);
            }
         }
      } else if (multiplier > 1 && mob.isInstantenous()) {
         addEffectOrInject(target, new MobEffectInstance(pending.effect, pending.durationTicks, pending.amplifier), pending.source, pending.forceInjection);

         for (int ix = 1; ix < multiplier; ix++) {
            mob.applyEffectTick(target, pending.amplifier);
         }
      } else if (multiplier > 1) {
         addEffectOrInject(
            target, new MobEffectInstance(pending.effect, pending.durationTicks * multiplier, pending.amplifier), pending.source, pending.forceInjection
         );
      } else {
         addEffectOrInject(target, new MobEffectInstance(pending.effect, pending.durationTicks, pending.amplifier), pending.source, pending.forceInjection);
      }
   }

   /**
    * 施加效果；正门办不到时，若开了注射则用 {@code forceAddEffect} 绕过硬编码拒绝。
    *
    * <p><b>⚠️ force 必须配"不得降级"判据 —— 这一条曾经被我错误地去掉过。</b>
    *
    * <p>{@code addEffect} 返回 false 有<b>三个</b>互相独立的原因：
    * <ol>
    *   <li><b>免疫</b> —— {@code canMobEffectBeApplied} / {@code canBeAffected} 为假</li>
    *   <li><b>{@code update} 认为无需变更</b> —— 包括"等级更低但时长更长"那一格：
    *       原版会**建好备胎链然后返回 false**（动了东西却报 false）</li>
    *   <li><b>实体硬编码拒绝</b> —— 末影龙 / 凋灵无条件覆写 {@code addEffect} 返回 false</li>
    * </ol>
    *
    * <p>原因 ② 在<b>普通生物上完全可达</b>。而 {@code forceAddEffect} 是
    * {@code activeEffects.put(...)} —— <b>整体替换，连 {@code hiddenEffect} 一起丢</b>。
    * 所以无条件 force 会造成：
    * <pre>
    *   目标：中毒 II 432t   ←  涂层：中毒 I 900t（带注射）
    *   addEffect → update 建备胎链、返回 false
    *   → forceAddEffect →【目标变成中毒 I 900t，刚建的备胎链一起没了】
    *   而原本应当是：保留中毒 II，把中毒 I 存进备胎链（E 档的设计目的）
    * </pre>
    *
    * <p><b>判据：只在"正门办不到，且新的确实更好或目标压根没有"时才强制。</b>
    * 三种情况下顺其自然 ——
    * <ul>
    *   <li>目标没有该效果 ⇒ 强制（这正是注射要打通的场景：末影龙 / 凋灵）</li>
    *   <li>新的等级更高，或同级但时长更长 ⇒ 强制（确实是升级）</li>
    *   <li>否则（新的更弱）⇒ <b>什么都不做</b>，让 {@code update} 已经建好的备胎链留着</li>
    * </ul>
    *
    * <p><b>⚠️ 切勿</b>改成 {@code && target.canBeAffected(instance)}：那个方法对末影龙恒返回
    * true（它不在任何免疫标签内，拒绝来自硬编码覆写的 {@code addEffect}），
    * 加上去会让 {@code forceAddEffect} 永不执行、<b>注射附魔彻底失效</b>。
    */
   private static void addEffectOrInject(LivingEntity target, MobEffectInstance instance, @Nullable Entity source, boolean forceInjection) {
      if (target.addEffect(instance, source)) {
         return;
      }
      if (!forceInjection) {
         return;
      }

      MobEffectInstance existing = target.getEffect(instance.getEffect());
      if (existing == null
            || instance.getAmplifier() > existing.getAmplifier()
            || (instance.getAmplifier() == existing.getAmplifier()
                && EffectOutcome.isLonger(instance, existing))) {
         target.forceAddEffect(instance, source);
      }
      // else：新的更弱 ⇒ 不动。update 已经处理过备胎链（若该建的话），替换只会降级 + 毁链。
   }

   /**
    * 剑刃涂层的触发入口。
    *
    * <p><b>⚠️ 已知未修的缺陷："攻击被判为不发生"时门会被绕过</b>（方案书 §10.6 F-c）。
    *
    * <p>攻击蓄力门（下面的 {@code getAttackStrengthScale(0.5F)} 检查）依赖"每次真正落地的攻击
    * 都会在原版 {@code Player.attack()} 的 :1414 复位 ticker"。但有四条早退路径会跳过那次复位：
    * ① 事件被取消 ② {@code Item.onLeftClickEntity} 返回 true ③ {@code isAttackable()} 为 false
    * ④ {@code skipAttackInteraction()} 为 true。此时门会卡在放行状态，可被连点刷掉涂层。
    *
    * <p><b>为什么不在本方法里加 {@code if (event.isCanceled()) return;}</b>：
    * 那个检查是<b>死代码</b>。NeoForge 的事件总线在 {@code @SubscribeEvent.receiveCanceled()}
    * 为 false（默认值）时，<b>根本不会把已取消的事件派发给本监听器</b>
    * （{@code SubscribeEventListener.invoke} 先查 {@code receiveCanceled()}，为假再看
    * {@code event.isCanceled()} 决定是否跳过）。所以写在这里永远不会执行。
    * 想真正生效得改成 {@code @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)} ——
    * 但那会让"取消事件但仍扣涂层"变成真实行为，属于**功能变更**而非重构，故不做。
    *
    * <p>按生态调研（408 个模组，命中率 0.5%），作者已决定**不修**这条。
    */
   @SubscribeEvent
   public static void onAttackEntity(AttackEntityEvent event) {
      Player player = event.getEntity();
      // 仅服务端处理：客户端收不到 ServerTickEvent，延迟队列里的效果永远不会被消费，
      // 于是每砍一刀就堆积一条 PendingEffect 并持有 Level 强引用（内存泄漏）；
      // 同时该早退也保证 PENDING_EFFECTS 只在服务端线程读写，ArrayList 无需并发容器。
      if (player.level().isClientSide()) {
         return;
      }
      ItemStack weapon = player.getMainHandItem();
      if (!weapon.isEmpty()) {
         // D 档：这一句是【刻意设计】，不是随手写的防抖 —— 别当冗余删掉。
         //
         // 语义：剑刃涂层只在【武器攻击冷却结束后】才允许施加效果。等价写法是
         // getAttackStrengthScale(0.5F) >= 1.0F，也就是蓄力 ticker >= 12（剑的 delay 是 12.5）。
         //
         // 为什么必须与 getAttackStrengthScale 用同一个 0.5F：原版 Player.attack() 的伤害与
         // 暴击判定（:1230 f2 = getAttackStrengthScale(0.5F)、:1241 f2 > 0.9F）用的就是这个值。
         // 用别的值会让"涂层放行"与"伤害真的打出"错开一档。
         //
         // ⚠️ 连点攻击的语义边界：门的 iflt 直跳方法末尾的 return，所以冷却未满时
         //    【既不施加效果、也不扣次数、也不写回物品】。
         //    但原版只在"攻击真的落到目标身上"时才复位 ticker（:1414，位于
         //    isAttackable() ∩ !skipAttackInteraction() 之内）—— 事件被取消等情况不会复位。
         //    那条路径已由 F-b 的 isCanceled 检查堵住；其余早退路径见方案书 §10.6 F-c（决定不修）。
         if (!(player.getAttackStrengthScale(0.5F) < 1.0F)) {
            List<Coating> coatings = (List<Coating>)weapon.getOrDefault(EnvenomDataComponents.COATINGS, List.of());
            Entity target = event.getTarget();
            if (target instanceof EnderDragonPart dragonPart) {
               target = dragonPart.parentMob;
            }

            if (target instanceof LivingEntity livingTarget) {
               RegistryAccess ra = target.level().registryAccess();
               int ampLevel = EnchantmentHelper.getItemEnchantmentLevel(EnvenomEnchantments.amplification(ra), weapon);
               int stickyLevel = EnchantmentHelper.getItemEnchantmentLevel(EnvenomEnchantments.sticky(ra), weapon);
               int chaosLevel = EnchantmentHelper.getItemEnchantmentLevel(EnvenomEnchantments.schrodingersBlessing(ra), weapon);
               int stackingLevel = EnchantmentHelper.getItemEnchantmentLevel(EnvenomEnchantments.stacking(ra), weapon);
               int injectionLevel = EnchantmentHelper.getItemEnchantmentLevel(EnvenomEnchantments.injection(ra), weapon);
               if (stackingLevel > 0) {
                  for (MobEffectInstance activeEffect : new ArrayList<>(livingTarget.getActiveEffects())) {
                     if (((MobEffect)activeEffect.getEffect().value()).getCategory() == MobEffectCategory.HARMFUL) {
                        int amp = activeEffect.getAmplifier();
                        boolean triggered = player.getRandom().nextFloat() < (float)stackingLevel / (float)(stackingLevel + amp);
                        int boost = triggered ? 1 + player.getRandom().nextInt(stackingLevel) : 0;

                        // F.2：原先这里【无条件】走 forceAddEffect（= 直接 put 替换），后果有两个 ——
                        //   ① 每次叠层都把目标身上的【备胎链 hiddenEffect】整个丢掉，
                        //      与 E 档刚恢复的备胎能力正好互相抵消；
                        //   ② 掷骰失败（boost == 0）时也照样替换一遍，纯属白清一次链。
                        // 现在先走正门：addEffect → update 会"提级"且【保留备胎链】。
                        if (boost > 0) {
                           int newAmp = Math.min(255, amp + boost);
                           if (newAmp > amp) {
                              MobEffectInstance boosted = new MobEffectInstance(
                                 activeEffect.getEffect(),
                                 stackingDuration(activeEffect.getDuration()),
                                 newAmp,
                                 activeEffect.isAmbient(),
                                 activeEffect.isVisible(),
                                 activeEffect.showIcon()
                              );
                              if (!livingTarget.addEffect(boosted, player)) {
                                 // 正门被挡（末影龙 / 凋灵的硬编码拒绝，或 update 认为无需变更）
                                 // ⇒ 回退到强制施加。
                                 livingTarget.forceAddEffect(boosted, player);
                              }
                           }
                        }
                     }
                  }
               }

               List<Coating> newCoatings = new ArrayList<>();
               boolean changed = false;
                // G 档：本刀已经入队过的效果实例。用于剔除"同 tick 内完全重复"的号票。
                List<MobEffectInstance> queuedThisSwing = new ArrayList<>();

               for (Coating coating : coatings) {
                  if (coating.slot() != CoatingSlot.BLADE) {
                     newCoatings.add(coating);
                  } else {
                     int amplifier = coating.amplifier() + calcAmpBonus(ampLevel, player.getRandom());
                     Holder<MobEffect> effect = coating.effect();
                     if (chaosLevel > 0) {
                        effect = randomEffect(player.getRandom());
                     }

                     // F-a：只有"这次施加真的会改变目标身上的东西"才扣次数。判据与原版 update 的
                     // 合并语义对齐 —— 提级 / 同级延长 / 建备胎 都算有变化；"等级更低且时长不更长"
                     // 什么都不做 ⇒ 不该扣（否则就是白烧次数）。
                     //
                     // ⚠️ 这是【事件侧预判】，距实际施加（下一 tick 的 applyEffect）差一 tick，
                     //    期间目标状态可能变化 ⇒ 判据可能过时。方向保守（宁可少扣）。彻底的版本是
                     //    "应用侧确认后退还次数"，留作后备（方案书 §3.H.5）。
                     //
                     // 注意：免疫目标仍然照扣（作者有意设计，§10.1）⇒ 这里不传 immune 位。
                      // G 档：同一次攻击里若已入队过"完全一样"的号票，这一张就不必再入队、也不必再扣
                      // 一次 —— 原版 update 对完全相同的实例什么都不做，第二张纯属浪费。判据是
                      // 【严格六项】（效果+等级+时长+ambient+visible+showIcon），与 equals 口径一致。
                      // ⚠️ 瞬间治疗/伤害必须排除在去重之外：它们走 applyEffect 的专用分支、会手动
                      //    清无敌帧（invulnerableTime = 0）⇒ 两张号票 = 两次全额结算，去重会砍半伤害。
                      //    isStrictDuplicate 内部已处理这一点。
                     // R2/R3 修正：判据必须用【实际施放时长】。滞留涂层存的是单份时长、实际施放 3 份，
                      // 直接用 coating.durationTicks() 会误判两种情形：
                      //   R2：存 900t / 实放 2700t，目标还有 1500t 中毒 I 时按 900 判 ⇒ "无变化"
                      //       ⇒ 真实的延长被静默丢弃（baseline 会延长到 2700 并扣次）。
                      //   R3：同一把剑上"普通中毒 900"与"滞留中毒 900"按 (effect, duration, amp)
                      //       完全相等 ⇒ 排在前的非滞留票把后面的滞留票当"重复"吞掉。
                      int multiplier = coating.lingering() ? 3 : 1;
                      MobEffectInstance incoming =
                         new MobEffectInstance(effect, coating.durationTicks() * multiplier, amplifier);
                     int outcome = EffectOutcome.classify(livingTarget.getEffect(effect), incoming, false);

                      // R4 修正：瞬间治疗/伤害【不能】被 willChange 挡。它们在 applyEffect 里是
                      // 逐次独立结算（先 target.invulnerableTime = 0 再 hurt/heal），而且原版
                      // /effect give 与模组自身的 HILT/ARMOR 路径都会在 activeEffects 留下瞬间实例
                      // ⇒ 目标"已有同类实例"完全可能，但那不构成"这次施放没意义"。
                      boolean worthApplying = EffectOutcome.isInstantHealOrHarm(incoming)
                         || EffectOutcome.willChange(outcome);
                      if (worthApplying
                            && !queuedThisSwing.stream().anyMatch(m -> EffectOutcome.isStrictDuplicate(m, incoming))) {
                        PENDING_EFFECTS.add(
                           new CoatingEffectHandler.PendingEffect(
                              livingTarget.level(),
                              livingTarget.getUUID(),
                              effect,
                              amplifier,
                              coating.durationTicks(),
                               multiplier,
                              player,
                              injectionLevel > 0
                           )
                        );
                        queuedThisSwing.add(incoming);
                        int deduct = multiplier;
                        Coating updated = coating;

                        for (int u = 0; u < deduct && updated != null; u++) {
                           if (stickyLevel <= 0 || !(player.getRandom().nextFloat() < (float)stickyLevel / ((float)stickyLevel + 1.0F))) {
                              updated = updated.decrementUses();
                           }
                        }

                        if (updated != null) {
                           newCoatings.add(updated);
                        }

                        changed = true;
                     } else {
                        // 不会产生变化 ⇒ 入队也是白费（下一 tick 会被 addEffect 拒绝）
                        // ⇒ 不扣次、不入队、涂层原样保留。
                        newCoatings.add(coating);
                     }
                   }
               }

               if (changed) {
                  weapon.set(EnvenomDataComponents.COATINGS, newCoatings.isEmpty() ? null : newCoatings);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onEntityTick(Post event) {
      Entity entity = event.getEntity();
      if (entity instanceof LivingEntity livingEntity) {
         if (!entity.level().isClientSide()) {
            long now = entity.level().getGameTime();
            checkEquipment(livingEntity, InteractionHand.MAIN_HAND, now);
            checkEquipment(livingEntity, InteractionHand.OFF_HAND, now);

            for (EquipmentSlot slot : EquipmentSlot.values()) {
               if (slot.getType() == Type.HUMANOID_ARMOR) {
                  ItemStack armor = livingEntity.getItemBySlot(slot);
                  if (!armor.isEmpty()) {
            checkArmorSlot(livingEntity, armor, now);
                  }
               }
            }
         }
      }
   }

   private static void checkEquipment(LivingEntity entity, InteractionHand hand, long now) {
      ItemStack stack = entity.getItemInHand(hand);
      if (!stack.isEmpty()) {
         List<Coating> coatings = (List<Coating>)stack.getOrDefault(EnvenomDataComponents.COATINGS, List.of());
         // 早退：没有本路径关心的 HILT 涂层就直接返回。只挡 isEmpty() 不够 —— 只带 BLADE 涂层的武器
         // （最常见）也会走到这里，而下面 3 次附魔查询（要翻注册表）与两次分配对它毫无意义。
         if (coatings.stream().noneMatch(c -> c.slot() == CoatingSlot.HILT)) {
            return;
         }
         RegistryAccess ra = entity.level().registryAccess();
         int ampLevel = EnchantmentHelper.getItemEnchantmentLevel(EnvenomEnchantments.amplification(ra), stack);
         int stickyLevel = EnchantmentHelper.getItemEnchantmentLevel(EnvenomEnchantments.sticky(ra), stack);
         int chaosLevel = EnchantmentHelper.getItemEnchantmentLevel(EnvenomEnchantments.schrodingersBlessing(ra), stack);
         List<Coating> newCoatings = new ArrayList<>();
         boolean changed = false;
          // B 档：冷却随物品走。取一次可变副本，触发时改它，最后连同 COATINGS 一起写回。
          Map<String, Long> cooldowns = TriggerCooldown.read(stack);

         for (int i = 0; i < coatings.size(); i++) {
            Coating coating = coatings.get(i);
            if (coating.slot() != CoatingSlot.HILT) {
               newCoatings.add(coating);
            } else {
               String key = TriggerCooldown.key(coating);
               if (canTrigger(cooldowns, key, now, TRIGGER_INTERVAL)) {
                  int amplifier = coating.amplifier() + calcAmpBonus(ampLevel, entity.getRandom());
                  Holder<MobEffect> effect = coating.effect();
                  if (chaosLevel > 0) {
                     effect = randomEffect(entity.getRandom());
                  }

                  entity.addEffect(new MobEffectInstance(effect, coating.durationTicks(), amplifier));
                  TriggerCooldown.mark(cooldowns, key, now);
                  boolean consumed = true;
                  if (stickyLevel > 0 && entity.getRandom().nextFloat() < (float)stickyLevel / ((float)stickyLevel + 1.0F)) {
                     consumed = false;
                  }

                  if (consumed) {
                     Coating updated = coating.decrementUses();
                     if (updated != null) {
                        newCoatings.add(updated);
                     }
                  } else {
                     newCoatings.add(coating);
                  }

                  changed = true;
               } else {
                  newCoatings.add(coating);
               }
            }
         }

          if (changed) {
             TriggerCooldown.write(stack, cooldowns);
             stack.set(EnvenomDataComponents.COATINGS, newCoatings.isEmpty() ? null : newCoatings);
          }
      }
   }

   private static void checkArmorSlot(LivingEntity entity, ItemStack armor, long now) {
      List<Coating> coatings = (List<Coating>)armor.getOrDefault(EnvenomDataComponents.COATINGS, List.of());
      // 早退：没有本路径关心的 ARMOR 涂层就直接返回。只挡 isEmpty() 不够 —— 只带 BLADE 涂层的物品
      // 也会走到这里，而下面的附魔查询与分配对它毫无意义。
      if (coatings.stream().noneMatch(c -> c.slot() == CoatingSlot.ARMOR)) {
         return;
      }
      List<Coating> newCoatings = new ArrayList<>();
      boolean changed = false;
      // B 档：冷却随物品走。取一次可变副本，触发时改它，最后连同 COATINGS 一起写回。
      Map<String, Long> cooldowns = TriggerCooldown.read(armor);

      for (int i = 0; i < coatings.size(); i++) {
         Coating coating = coatings.get(i);
         if (coating.slot() != CoatingSlot.ARMOR) {
            newCoatings.add(coating);
         } else {
            boolean isInstantHealHarm = ((MobEffect)coating.effect().value()).isInstantenous()
               && (coating.effect().is(MobEffects.HEAL) || coating.effect().is(MobEffects.HARM));
            long interval = isInstantHealHarm ? INSTANT_ARMOR_INTERVAL : TRIGGER_INTERVAL;
            String key = TriggerCooldown.key(coating);

            // F.1【已回退】：这两个跳过判断必须留在 canTrigger【之外】。
            //
            // 曾一度把它们移进门内、并在跳过时也 TriggerCooldown.mark(...)，想省掉"每 tick
            // 重新评估"。**那是错的方向**：mark 会真的改时间戳 ⇒ 组件内容变了 ⇒
            // ServerPlayer.tick → containerMenu.broadcastChanges → synchronizeSlotToRemote 的
            // ItemStack.matches 判定为"槽位变了" ⇒ 每 0.5 秒发一次 slot 重同步包 + NBT 变更。
            // 也就是【拿每 tick 零写，换来了每 0.5 秒一次真实 I/O】，纯亏。
            //
            // 而"每 tick 重新评估"的实际代价只是这一段：两次 MobEffects.HEAL/HARM 比较 +
            // getHealth/getMaxHealth/isAlive 三个 getter 比较 —— 都是内存读，且
            // A0 的早退已保证"没有 ARMOR 涂层时根本走不到这里"。
            //
            // 另外：跳过条件是【永久状态】（满血就是满血、死了就是死了），所以"先判跳过再判冷却"
            // 不会因为省掉冷却记账而多付任何东西 —— 门内门外都只多两个 getter。
            if (isInstantHealHarm) {
               if (coating.effect().is(MobEffects.HEAL) && entity.getHealth() >= entity.getMaxHealth()) {
                  newCoatings.add(coating);
                  continue;
               }

               if (coating.effect().is(MobEffects.HARM) && !entity.isAlive()) {
                  newCoatings.add(coating);
                  continue;
               }
            }

            if (canTrigger(cooldowns, key, now, interval)) {
               entity.addEffect(coating.toEffectInstance());
               TriggerCooldown.mark(cooldowns, key, now);
               Coating updated = coating.decrementUses();
               if (updated != null) {
                  newCoatings.add(updated);
               }

               changed = true;
            } else {
               newCoatings.add(coating);
            }
         }
      }

      if (changed) {
         TriggerCooldown.write(armor, cooldowns);
         armor.set(EnvenomDataComponents.COATINGS, newCoatings.isEmpty() ? null : newCoatings);
      }
   }

   static {
      List<Holder<MobEffect>> list = new ArrayList<>();
      BuiltInRegistries.MOB_EFFECT.holders().forEach(list::add);
      ALL_MOB_EFFECTS = List.copyOf(list);
   }

   private static record PendingEffect(
      Level level,
      UUID targetId,
      Holder<MobEffect> effect,
      int amplifier,
      int durationTicks,
      /** 实际施放次数：滞留涂层为 3，其余为 1。也用于判断"是不是滞留"。 */
      int multiplier,
      @Nullable Entity source,
      boolean forceInjection
   ) {
   }
}
