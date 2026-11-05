package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.coating.Coating;
import com.terminalfoam.envenom.coating.CoatingSlot;
import com.terminalfoam.envenom.coating.EnvenomDataComponents;
import com.terminalfoam.envenom.config.EnvenomConfig;
import com.terminalfoam.envenom.effect.EnvenomEffects;
import com.terminalfoam.envenom.enchantment.EnvenomEnchantments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
      // 防御：列表为空时 random.nextInt(0) 会抛 IllegalArgumentException —— 直接崩游戏。
      // 正常时序下静态块在【首次事件派发】时才跑（EventBus.register(Class) 只做
      // getDeclaredMethods()，不实例化、不触发类初始化），那时所有模组早已注册完、
      // 注册表也已冻结，池子不会是空的。但"会不会崩"不该依赖这个时序假设。
      if (ALL_MOB_EFFECTS.isEmpty()) {
         return MobEffects.POISON;
      }
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
      if (!cooldowns.containsKey(key)) {
         return true;
      }
      long last = TriggerCooldown.last(cooldowns, key);
      if (last > now) {
         return true;
      }
      return now - last >= interval;
   }

   /**
    * 「药水增幅」附魔：按等级给一个随机的等级加成。
    *
    * <p>{@code roll = rand(0..4)}；{@code roll < level} 时给 {@code roll + 1}，否则给 0。
    * 也就是等级 N 时加成均匀分布在 {@code +1 .. +min(N,5)}，概率 {@code min(N,5)/5}。
    * 等级超过 5 时自然封顶（{@code roll} 的上界就是 4），无需额外处理。
    */
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
      applyCoatingEffect(target, pending.effect, pending.amplifier, pending.durationTicks, pending.source, pending.forceInjection, multiplier, pending.power);
   }

   /**
    * 把一张"涂层票"真正施加到目标身上 —— <b>三个槽位的唯一施加原语</b>。
    *
    * <p>{@code multiplier} 是这张票要结算几次：滞留涂层为 3，其余为 1。
    * 三种分支的行为：
    * <ul>
    *   <li><b>瞬间治疗 / 瞬间伤害</b>：循环 {@code multiplier} 次，每次 {@code 3 * 2^amp}，
    *       并且每次先把 {@code invulnerableTime} 清零（否则第 2、3 次会被无敌帧吞掉）。
    *       方向按"亡灵反之"翻转：治疗打亡灵造成伤害，伤害打亡灵则治疗。</li>
    *   <li><b>其它瞬间效果</b>（模组自定义的 {@code isInstantenous()}）：先 {@code addEffect} 一次，
    *       再用 {@code applyEffectTick} 补 {@code multiplier-1} 次。</li>
    *   <li><b>状态类效果</b>：时长直接乘 {@code multiplier}。</li>
    * </ul>
    *
    * <p><b>为什么抽成一个方法</b>：剑刃（{@code onLivingDamagePre}）本来就有这套 ×N 语义，
    * 而剑柄（{@code checkEquipment}）与盔甲（{@code checkArmorSlot}）此前各自只做了一次
    * {@code addEffect}，让滞留涂层在被动槽位上拿不到 ×3（REPORT.md §3.7）。
    * 三处共用同一个原语，才不会再次跑偏。
    *
    * @param source         效果来源实体；被动槽位（自己给自己）传 {@code null}
    * @param forceInjection 是否走通用注射入口（仅命中时带注射附魔的剑刃涂层会开）
    * @param power          剑刃的承诺度强度（0.25~1）；剑柄/盔甲固定 1.0
    */
   private static void applyCoatingEffect(
         LivingEntity target,
         Holder<MobEffect> effect,
         int amplifier,
         int durationTicks,
         @Nullable Entity source,
         boolean forceInjection,
         int multiplier,
         float power
   ) {
      MobEffect mob = effect.value();
      boolean isHeal = effect.value() == MobEffects.INSTANT_HEALTH.value();
      boolean isHarm = effect.value() == MobEffects.INSTANT_DAMAGE.value();
      if (mob.isInstantenous() && (isHeal || isHarm)) {
         float amount = CoatingMath.instantAmount(amplifier, power);
         boolean undead = target.isInvertedHealAndHarm();
         boolean dealDamage = isHeal && undead || isHarm && !undead;

         for (int i = 0; i < multiplier; i++) {
            if (dealDamage) {
               target.invulnerableTime = 0;
               if (target.level() instanceof ServerLevel serverLevel) {
                  target.hurtServer(serverLevel, target.damageSources().magic(), amount);
               }
            } else if (!target.hasEffect(EnvenomEffects.NAUSEA)) {
               target.heal(amount);
            }
         }
      } else if (multiplier > 1 && mob.isInstantenous()) {
         addEffectOrInject(target, new MobEffectInstance(effect, durationTicks, amplifier), source, forceInjection);

         for (int ix = 1; ix < multiplier; ix++) {
            if (target.level() instanceof ServerLevel serverLevel) {
               mob.applyEffectTick(serverLevel, target, amplifier);
            }
         }
      } else if (multiplier > 1) {
         addEffectOrInject(
            target, new MobEffectInstance(effect, CoatingMath.multiplyDuration(durationTicks, multiplier), amplifier), source, forceInjection
         );
      } else {
         addEffectOrInject(target, new MobEffectInstance(effect, durationTicks, amplifier), source, forceInjection);
      }
   }

   /** Only the injection hit uses the immunity-bypassing insertion path. */
   private static void addEffectOrInject(LivingEntity target, MobEffectInstance instance, @Nullable Entity source, boolean forceInjection) {
      if (forceInjection) {
         InjectionHandler.apply(target, instance, source);
      } else {
         target.addEffect(instance, source);
      }
   }

   /**
    * 剑刃涂层的触发入口（M3 回移版：1.21.1 也改为伤害事件）。
    *
    * <p>原实现挂在 {@code AttackEntityEvent} 上，而该事件只在 {@code Player.attack()} 头部触发一次，
    * 于是横扫的次要目标（{@code Player.doSweepAttack} 直接 {@code hurtServer}，不走
    * {@code Player.attack()}）完全不会触发涂层。改挂 {@code LivingDamageEvent.Pre} 后，
    * 普通近战与横扫的每个目标都按"真正受伤"结算。
    *
    * <p>1.21.1 没有矛，因此这里没有 26.1.2 分支的突刺/冲刺（KINETIC_WEAPON）逻辑。
    *
    * <p><b>顺带修掉旧缺陷 F-c</b>：旧门依赖 {@code Player.attack()} 复位攻击 ticker，而
    * "攻击被判为不发生"（被取消 / {@code onLeftClickEntity} 返回 true / 不可攻击 / 跳过交互）
    * 时不会复位，门会卡在放行状态。伤害事件只在伤害真正落地时触发，这个口子自然消失。
    *
    * <p><b>结算粒度 = 每个真正受伤的目标一次</b>：横扫打中 5 只就结算 5 次、扣 5 次涂层，
    * 这是刻意的（一轮 AoE 换 5 份药效）。同一次挥击（同 tick）对同一个目标只结算一次：
    * 多部件 Boss 的多个部件会解包到同一本体，由 {@link #markSwingHit} 去重。
    */
   @SubscribeEvent
   public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
      DamageSource source = event.getSource();
      // 只处理玩家近战：投掷物/箭矢的 directEntity 是弹射物，直接排除
      if (!(source.getEntity() instanceof ServerPlayer player)) {
         return;
      }
      if (source.getDirectEntity() != player) {
         return;
      }
      LivingEntity livingTarget = event.getEntity();
      if (livingTarget == player) {
         return; // 自伤
      }
      if (player.level().isClientSide()) {
         return;
      }

      ItemStack weapon = source.getWeaponItem();
      if (weapon.isEmpty()) {
         weapon = player.getMainHandItem();
      }
      if (weapon.isEmpty()) {
         return;
      }
      List<Coating> coatings = weapon.getOrDefault(EnvenomDataComponents.COATINGS, List.of());
      if (coatings.stream().noneMatch(coating -> coating.slot() == CoatingSlot.BLADE)) {
         return; // 没有剑刃涂层：连附魔查询都不做
      }

      // 同一次挥击（同 tick）对同一本体只结算一次：横扫/贯穿/多部件都会重复命中
      if (!markSwingHit(player.getUUID(), livingTarget.level().getGameTime(), livingTarget.getUUID())) {
         return;
      }

      // 承诺度 → 效果强度（1.21.1 只有攻击冷却条这一个维度）
      float power = coatingPower(player, weapon);

      RegistryAccess ra = livingTarget.level().registryAccess();
      int ampLevel = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(EnvenomEnchantments.amplification(ra));
      int stickyLevel = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(EnvenomEnchantments.sticky(ra));
      int chaosLevel = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(EnvenomEnchantments.schrodingersBlessing(ra));
      int stackingLevel = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(EnvenomEnchantments.stacking(ra));
      int injectionLevel = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(EnvenomEnchantments.injection(ra));
      if (stackingLevel > 0) {
         for (MobEffectInstance activeEffect : new ArrayList<>(livingTarget.getActiveEffects())) {
            if (activeEffect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
               int amp = activeEffect.getAmplifier();
               boolean triggered = player.getRandom().nextFloat()
                     < (float) stackingLevel / ((float) stackingLevel + amp * (float) EnvenomConfig.stackingResistance);
               int boost = triggered ? 1 + player.getRandom().nextInt(stackingLevel) : 0;

               // F.2：先走正门：addEffect → update 会"提级"且【保留备胎链】。
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

            // F-a：只有"这次施加真的会改变目标身上的东西"才扣次数。
            int multiplier = coating.lingering() ? 3 : 1;
            int scaledDuration = scaleDuration(coating.durationTicks(), power);
            MobEffectInstance incoming = new MobEffectInstance(effect, CoatingMath.multiplyDuration(scaledDuration, multiplier), amplifier);
            int outcome = EffectOutcome.classify(livingTarget.getEffect(effect), incoming, false);

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
                     scaledDuration,
                     multiplier,
                     player,
                     injectionLevel > 0,
                     power
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
               newCoatings.add(coating);
            }
         }
      }

      if (changed) {
         weapon.set(EnvenomDataComponents.COATINGS, newCoatings.isEmpty() ? null : newCoatings);
      }
   }

   /**
    * 0..1 的"承诺度"：这一次命中投入了多少蓄力。
    *
    * <ul>
    *   <li><b>举矛冲刺</b>：{@code (已举 tick - 前摇) / 有效冲刺窗口}。
    *       {@code delayTicks} 之前不产生撞击伤害，窗口上限是
    *       {@code getUseDuration()}（= delayTicks + damageCondition.maxDuration()）。</li>
    *   <li><b>近战 / 突刺</b>：攻击冷却条，与原版伤害、暴击使用同一个 0.5F 取样点。</li>
    * </ul>
    */
   private static float commitment(ServerPlayer player, ItemStack weapon) {
      KineticWeapon kinetic = weapon.get(DataComponents.KINETIC_WEAPON);
      if (kinetic != null && player.isUsingItem() && player.getUseItem() == weapon) {
         int used = player.getTicksUsingItem();
         int total = weapon.getUseDuration(player);
         int delay = kinetic.delayTicks();
         int span = Math.max(1, total - delay);
         return Mth.clamp((used - delay) / (float)span, 0.0F, 1.0F);
      }
      return Mth.clamp(player.getAttackStrengthScale(0.5F), 0.0F, 1.0F);
   }

   /**
    * 承诺度 → 效果强度：{@code sqrt} 曲线让半蓄力也有像样的效果，下限 0.25 避免"看不见的效果"。
    */
   private static float coatingPower(ServerPlayer player, ItemStack weapon) {
      float raw = commitment(player, weapon);
      return Mth.clamp((float)Math.sqrt(raw), 0.25F, 1.0F);
   }

   /** 按强度缩放时长；下限 20 tick。 */
   private static int scaleDuration(int baseTicks, float power) {
      return CoatingMath.scaleDuration(baseTicks, power);
   }

   /** 同一次挥击（同 tick）对同一目标只结算一次。 */
   private static final Map<UUID, SwingRecord> SWING_HITS = new HashMap<>();

   private static boolean markSwingHit(UUID playerId, long tick, UUID targetId) {
      SwingRecord record = SWING_HITS.get(playerId);
      if (record == null || record.tick != tick) {
         record = new SwingRecord(tick);
         SWING_HITS.put(playerId, record);
      }
      return record.targets.add(targetId);
   }

   private static final class SwingRecord {
      private final long tick;
      private final Set<UUID> targets = new HashSet<>();

      private SwingRecord(long tick) {
         this.tick = tick;
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
         List<Coating> coatings = stack.getOrDefault(EnvenomDataComponents.COATINGS, List.of());
         // 早退：没有本路径关心的 HILT 涂层就直接返回。只挡 isEmpty() 不够 —— 只带 BLADE 涂层的武器
         // （最常见）也会走到这里，而下面 3 次附魔查询（要翻注册表）与两次分配对它毫无意义。
         if (coatings.stream().noneMatch(c -> c.slot() == CoatingSlot.HILT)) {
            return;
         }
         RegistryAccess ra = entity.level().registryAccess();
         int ampLevel = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(EnvenomEnchantments.amplification(ra));
         int stickyLevel = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(EnvenomEnchantments.sticky(ra));
         int chaosLevel = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(EnvenomEnchantments.schrodingersBlessing(ra));
         List<Coating> newCoatings = new ArrayList<>();
         boolean changed = false;
          // B 档：冷却随物品走。取一次可变副本，触发时改它，最后连同 COATINGS 一起写回。
          Map<String, Long> cooldowns = TriggerCooldown.read(stack);
          Map<String, Long> appliedUntil = TriggerCooldown.readApplied(stack);

         for (int i = 0; i < coatings.size(); i++) {
            Coating coating = coatings.get(i);
            if (coating.slot() != CoatingSlot.HILT) {
               newCoatings.add(coating);
            } else {
               String key = TriggerCooldown.key(coating);
               boolean instant = coating.effect().value().isInstantenous();
               // 混沌祝福会把实际效果换成随机效果，无法按原效果追踪剩余时长，不参与补扣。
               boolean trackable = !instant && chaosLevel == 0;
               long appliedEnd = appliedUntil.getOrDefault(key, 0L);
               boolean vanished = trackable && appliedEnd > 0L && entity.isAlive() && !entity.hasEffect(coating.effect());
               boolean due = canTrigger(cooldowns, key, now, TRIGGER_INTERVAL);
               if (due || vanished) {
                  int amplifier = coating.amplifier() + calcAmpBonus(ampLevel, entity.getRandom());
                  Holder<MobEffect> effect = coating.effect();
                  if (chaosLevel > 0) {
                     effect = randomEffect(entity.getRandom());
                  }

                  // 滞留涂层按 ×N 施加（时长 / 瞬间结算次数），并相应扣 N 次 —— 与剑刃同源。
                  // （此前这里只做了一次 addEffect、只扣 1 次，滞留涂在剑柄上拿不到 ×3，REPORT.md §3.7）
                  int multiplier = coating.lingering() ? 3 : 1;
                  applyCoatingEffect(entity, effect, amplifier, coating.durationTicks(), null, false, multiplier, 1.0F);
                  if (due) {
                     TriggerCooldown.mark(cooldowns, key, now);
                  }
                  if (trackable && entity.hasEffect(coating.effect())) {
                     appliedUntil.put(key, coating.durationTicks() == -1 ? Long.MAX_VALUE
                           : now + (long) CoatingMath.multiplyDuration(coating.durationTicks(), multiplier));
                  } else {
                     appliedUntil.remove(key);
                  }

                  // 扣次：每次扣独立掷粘滞骰，与剑刃的扣次循环一致。
                  Coating updated = coating;
                  for (int u = 0; u < multiplier && updated != null; u++) {
                     if (stickyLevel <= 0 || !(entity.getRandom().nextFloat() < (float)stickyLevel / ((float)stickyLevel + 1.0F))) {
                        updated = updated.decrementUses();
                     }
                  }
                  if (updated != null) {
                     newCoatings.add(updated);
                  } else {
                     appliedUntil.remove(key);
                  }

                  changed = true;
               } else {
                  newCoatings.add(coating);
               }
            }
         }

          if (changed) {
             TriggerCooldown.write(stack, cooldowns);
             TriggerCooldown.writeApplied(stack, appliedUntil);
             stack.set(EnvenomDataComponents.COATINGS, newCoatings.isEmpty() ? null : newCoatings);
          }
      }
   }

   private static void checkArmorSlot(LivingEntity entity, ItemStack armor, long now) {
      List<Coating> coatings = armor.getOrDefault(EnvenomDataComponents.COATINGS, List.of());
      // 早退：没有本路径关心的 ARMOR 涂层就直接返回。只挡 isEmpty() 不够 —— 只带 BLADE 涂层的物品
      // 也会走到这里，而下面的附魔查询与分配对它毫无意义。
      if (coatings.stream().noneMatch(c -> c.slot() == CoatingSlot.ARMOR)) {
         return;
      }
      List<Coating> newCoatings = new ArrayList<>();
      boolean changed = false;
      // B 档：冷却随物品走。取一次可变副本，触发时改它，最后连同 COATINGS 一起写回。
      Map<String, Long> cooldowns = TriggerCooldown.read(armor);
      Map<String, Long> appliedUntil = TriggerCooldown.readApplied(armor);

      for (int i = 0; i < coatings.size(); i++) {
         Coating coating = coatings.get(i);
         if (coating.slot() != CoatingSlot.ARMOR) {
            newCoatings.add(coating);
         } else {
            boolean isInstantHealHarm = coating.effect().value().isInstantenous()
               && (coating.effect().value() == MobEffects.INSTANT_HEALTH.value() || coating.effect().value() == MobEffects.INSTANT_DAMAGE.value());
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
               boolean heals = (coating.effect().value() == MobEffects.INSTANT_HEALTH.value())
                     != entity.isInvertedHealAndHarm();
               if (heals && entity.getHealth() >= entity.getMaxHealth()) {
                  newCoatings.add(coating);
                  continue;
               }

               if (!entity.isAlive()) {
                  newCoatings.add(coating);
                  continue;
               }
            }

            boolean instant = coating.effect().value().isInstantenous();
            long appliedEnd = appliedUntil.getOrDefault(key, 0L);
            boolean vanished = !instant && appliedEnd > 0L && entity.isAlive() && !entity.hasEffect(coating.effect());
            boolean due = canTrigger(cooldowns, key, now, interval);
            if (due || vanished) {
               // 滞留涂层按 ×N 施加（时长 / 瞬间结算次数），并相应扣 N 次 —— 与剑刃同源。
               // （此前这里只做了一次 addEffect、只扣 1 次，滞留涂在盔甲上拿不到 ×3，REPORT.md §3.7）
               // 盔甲槽不读任何附魔，所以这里没有粘滞骰，直接扣 N 次。
               int multiplier = coating.lingering() ? 3 : 1;
               applyCoatingEffect(entity, coating.effect(), coating.amplifier(), coating.durationTicks(), null, false, multiplier, 1.0F);
               if (due) {
                  TriggerCooldown.mark(cooldowns, key, now);
               }
               if (!instant && entity.hasEffect(coating.effect())) {
                  appliedUntil.put(key, coating.durationTicks() == -1 ? Long.MAX_VALUE
                           : now + (long) CoatingMath.multiplyDuration(coating.durationTicks(), multiplier));
               } else {
                  appliedUntil.remove(key);
               }
               Coating updated = coating.decrementUses(multiplier);
               if (updated != null) {
                  newCoatings.add(updated);
               } else {
                  appliedUntil.remove(key);
               }

               changed = true;
            } else {
               newCoatings.add(coating);
            }
         }
      }

      if (changed) {
         TriggerCooldown.write(armor, cooldowns);
         TriggerCooldown.writeApplied(armor, appliedUntil);
         armor.set(EnvenomDataComponents.COATINGS, newCoatings.isEmpty() ? null : newCoatings);
      }
   }

   static {
      List<Holder<MobEffect>> list = new ArrayList<>();
      BuiltInRegistries.MOB_EFFECT.listElements().forEach(list::add);
      ALL_MOB_EFFECTS = List.copyOf(list);
   }

   private static record PendingEffect(
      Level level,
      UUID targetId,
      Holder<MobEffect> effect,
      int amplifier,
      int durationTicks,
      // 实际施放次数：滞留涂层为 3，其余为 1。也用于判断"是不是滞留"。
      int multiplier,
      @Nullable Entity source,
      boolean forceInjection,
      float power
   ) {
   }
}






