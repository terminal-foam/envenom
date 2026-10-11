package com.terminalfoam.envenom.handler;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 判定编码：把"这次施加相对目标现状会怎样"编码成几个二进制位。
 *
 * <p><b>为什么要它</b>：原版 {@code MobEffectInstance.update} 的合并语义有五种结果
 * （提级 / 延长 / 建备胎 / 无变化 / …），而"要不要扣一次涂层次数"必须与它对上 ——
 * 否则就会出现"什么都没发生，但次数被扣了"的浪费（审计编号 F-a）。
 *
 * <p><b>为什么不需要覆写原版</b>：算这些位所需的资料**全在公开 API 上**
 * （{@code getAmplifier} / {@code getDuration} / {@code isInfiniteDuration}），
 * 而 {@code update} 被原版和所有模组共用，改它的返回值语义会破坏别人的代码。
 * 所以这里算一份**只读的视图**，零侵入。
 *
 * <p>判定口径直接照抄原版 {@code update} 的两个条件：
 * <pre>
 *   other.amplifier &gt; this.amplifier                        → 提级
 *   else if (this.isShorterDurationThan(other))  // 新时长更长
 *        if (other.amplifier == this.amplifier)             → 同级延长
 *        else                                                → 建备胎 / 递归更新备胎
 * </pre>
 *
 * <p><b>已知盲区</b>：{@code hiddenEffect}（备胎链）是 private 且无公开 getter，
 * 所以这里**看不到目标已有的备胎链**。影响范围见 {@link #WILL_CHANGE}。
 */
final class EffectOutcome {

   private EffectOutcome() {
   }

   /** 新等级严格更高 ⇒ 原版会提级（时长一并取新值）。 */
   static final int AMP_HIGHER = 0b0001;
   /** 新等级更低。 */
   static final int AMP_LOWER = 0b0010;
   /** 新时长更长（按原版的"更长"定义，正确处理无限时长）。 */
   static final int DUR_LONGER = 0b0100;
   /** 目标免疫这个效果（调用方已知时传入；未知传 false）。 */
   static final int IMMUNE = 0b1000;

   /**
    * 原版会不会改动目标身上的东西？
    *
    * <p>与 {@code update} 的返回值一一对应（{@code true} = 有变化）：
   * <pre>
    *   AMP_HIGHER                        → true   （提级，L144-153 置 flag）
    *   同级 + DUR_LONGER                  → true   （延长，L155-157 置 flag）
    *   AMP_LOWER + DUR_LONGER            → true   （建备胎，动了东西 ⇒ 算"有变化"）
    *   其余（含 AMP_LOWER 且时长不更长）   → false  （什么都不做）
    * </pre>
    *
    * <p><b>盲区 ①</b>：{@code AMP_LOWER + DUR_LONGER + 目标已有备胎链} 这一格，
    * 原版会递归更新备胎链但**返回 false**（见参考文档 §5.3.1）。
    * 这里判 {@code true} —— 方向是"更愿意扣次"，因为它确实动了东西。
    *
    * <p><b>盲区 ②（审计编号 R5）</b>：三个显示属性（{@code ambient} / {@code visible} /
    * {@code showIcon}）的差异**看不到**。原版 {@code update} 末尾三句会比较并覆盖它们
    * （那三项不同时它也返回 true），而这里只比等级与时长 ⇒ "仅显示属性不同"的那一格会被判成
    * "无变化" ⇒ F-a 不扣次、不入队。方向是"少扣"（玩家不吃亏），且属外观级差异，**接受**。
    * （方案书 §3.E.2 当初正是为避免这个盲区才要求用 {@code update} 当判据 —— 那会引入
    * 一次 {@code MobEffectInstance} 分配、且要处理 {@code -1} 哨兵。此处选择接受盲区。）
    */
   static final int WILL_CHANGE = AMP_HIGHER | DUR_LONGER;

   /**
    * 算出判定编码。
    *
    * @param existing 目标身上已有的同类效果；{@code null} 表示目标没有该效果
    * @param incoming 即将施加的效果
    * @param immune   目标是否免疫（调用方按自己的语义给出；拿不准就传 false）
    */
   static int classify(MobEffectInstance existing, MobEffectInstance incoming, boolean immune) {
      int bits = 0;
      if (existing == null) {
         // 目标没有同类效果 ⇒ 任何施加都是纯粹的新增，必然有变化
         return AMP_HIGHER | (immune ? IMMUNE : 0);
      }

      if (incoming.getAmplifier() > existing.getAmplifier()) {
         bits |= AMP_HIGHER;
      } else if (incoming.getAmplifier() < existing.getAmplifier()) {
         bits |= AMP_LOWER;
      }

      if (isLonger(incoming, existing)) {
         bits |= DUR_LONGER;
      }

      if (immune) {
         bits |= IMMUNE;
      }
      return bits;
   }

   /** 这次施加会不会真的改变目标身上的东西？（免疫时不算"变化"，见调用点说明） */
   static boolean willChange(int bits) {
      return (bits & WILL_CHANGE) != 0;
   }

   /**
    * 两个实例是不是"严格重复"——同一次攻击里第二张号票**完全不会带来新东西**？
    *
    * <p>判据就是 {@link MobEffectInstance#equals} 的六项口径：
    * 效果 + 等级 + 时长 + ambient + visible + showIcon。
    * 用 {@code equals} 而不是手写六项比较，是因为原版的实现恰好就是这六项
    * （{@code hiddenEffect} **不在其中**，这是已知盲区）。
    *
    * <p><b>⚠️ 瞬间治疗 / 瞬间伤害必须排除</b>：它们走 {@code applyEffect} 的专用分支，
    * 而且那里会手动清无敌帧（{@code target.invulnerableTime = 0}）再逐次结算 ——
    * 两张号票 = **两次全额伤害**。去重会把伤害直接砍半，是必须避免的误伤。
    * （原版 {@code MobEffects.HEAL} / {@code HARM} 都是 {@code InstantenousMobEffect}。）
    *
    * @param queued   本次攻击里已经入队过的实例
    * @param incoming 正在考虑入队的实例
    */
   static boolean isStrictDuplicate(MobEffectInstance queued, MobEffectInstance incoming) {
      if (isInstantHealOrHarm(incoming)) {
         return false;
      }
      return queued.equals(incoming);
   }

   /**
    * 是不是瞬间治疗 / 瞬间伤害（走专用结算分支、会清无敌帧的那两类）？
    *
    * <p>这两类**不能**用 {@link #willChange} 去挡：它们每次施放都是一次独立结算
    * （{@code applyEffect} 里手动 {@code target.invulnerableTime = 0} 后逐次 hurt/heal），
    * 而原版 {@code /effect give} 与模组自身的 HILT/ARMOR 路径都会在 {@code activeEffects} 里
    * 留下瞬间实例 ⇒ 目标身上"已有同类实例"完全可能，但那**不构成"这次施放没意义"**。
    */
   static boolean isInstantHealOrHarm(MobEffectInstance instance) {
      MobEffect effect = instance.getEffect().value();
      return effect.isInstantenous()
         && (instance.getEffect().is(MobEffects.HEAL) || instance.getEffect().is(MobEffects.HARM));
   }

   /**
    * 复刻原版 {@code MobEffectInstance.isShorterDurationThan} 的语义。
    *
    * <p><b>{@code -1} 是"无限"哨兵值，不能按数值比</b>：原版
    * {@code isShorterDurationThan(other) = !this.isInfiniteDuration() && (this.duration < other.duration || other.isInfiniteDuration())}。
    * 所以"a 比 b 更长"=
    * <ul>
    *   <li>a 无限、b 有限 → 更长</li>
    *   <li>b 无限 → 不可能更长（除非 a 也无限，但那算"相等"不是更长）</li>
    *   <li>都有限 → 直接比大小</li>
    * </ul>
    */
   static boolean isLonger(MobEffectInstance a, MobEffectInstance b) {
      if (a.isInfiniteDuration()) {
         return !b.isInfiniteDuration();
      }
      if (b.isInfiniteDuration()) {
         return false;
      }
      return a.getDuration() > b.getDuration();
   }
}
