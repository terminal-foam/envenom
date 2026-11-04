package com.terminalfoam.envenom.handler;

import com.terminalfoam.envenom.coating.Coating;
import com.terminalfoam.envenom.coating.EnvenomDataComponents;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/**
 * 触发冷却的读写与键构造。
 *
 * <p><b>本类不持有任何状态</b> —— 冷却数据存在物品自己的
 * {@link EnvenomDataComponents#TRIGGER_COOLDOWNS} 组件里。
 * 调用方用 {@link #read} 取出可变副本，触发时改它，最后用 {@link #write} 写回。
 *
 * <p>键 = 效果 id + 涂层类型（普通/滞留）：一件物品只可能被一种槽位使用
 * （剑柄看主/副手，盔甲看装备槽），所以同一物品上的键只需区分效果和类型。
 *
 * <p><b>旧实现的问题</b>（本档之前）：冷却存在 {@code Map<UUID, Map<String, Long>>} 静态表里，
 * 键是"槽位 + 效果 id"。那套写法会因服务器重启丢失、联机/单机切换串味
 * （静态表跨存档共享），且需要兜底清扫与死亡清理两个补丁。
 * 搬进物品组件后这些补丁全部删除。
 */
final class TriggerCooldown {

   private TriggerCooldown() {
   }

   /**
    * 触发键 = 效果 id。取不到注册键时退回效果描述 id（仍保证同一效果键稳定）。
    *
    * <p>不用列表下标：涂层被消耗移除后下标会前移，冷却就会记到别的涂层头上。
    */
   static String key(Coating coating) {
      String effect = coating.effect().unwrapKey().map(k -> k.identifier().toString()).orElseGet(() -> coating.effect().value().getDescriptionId());
      // 普通与滞留涂层分开记账，否则同一效果的两层会互相顶掉冷却。
      return effect + (coating.lingering() ? "|lingering" : "|normal");
   }

   /**
    * 取出该物品的冷却表**可变副本**（组件里存的是不可变的）。
    *
    * <p>没有记录时返回空表；冷却判定先检查键是否存在，让新涂层立即触发。
    * 时间戳 0 是有效的首次触发时间，不能同时当作“没有记录”的标记。
    */
   static Map<String, Long> read(ItemStack stack) {
      return new HashMap<>(stack.getOrDefault(EnvenomDataComponents.TRIGGER_COOLDOWNS, Map.of()));
   }

   /** 上次触发时间；没有记录时返回 0。 */
   static long last(Map<String, Long> cooldowns, String key) {
      return cooldowns.getOrDefault(key, 0L);
   }

   /** 记账。 */
   static void mark(Map<String, Long> cooldowns, String key, long now) {
      cooldowns.put(key, now);
   }

   /**
    * 写回。空表时**移除组件**而不是存一个空 Map —— 与 {@code COATINGS} 的处理一致，
    * 避免物品上挂一堆空组件，也让"没有冷却"的物品在组件层面干净。
    */
   static void write(ItemStack stack, Map<String, Long> cooldowns) {
      stack.set(EnvenomDataComponents.TRIGGER_COOLDOWNS, cooldowns.isEmpty() ? null : cooldowns);
   }

   /** 取出"效果预期结束时刻"的可变副本。 */
   static Map<String, Long> readApplied(ItemStack stack) {
      return new HashMap<>(stack.getOrDefault(EnvenomDataComponents.APPLIED_UNTIL, Map.of()));
   }

   /** 写回；空表时移除组件。 */
   static void writeApplied(ItemStack stack, Map<String, Long> appliedUntil) {
      stack.set(EnvenomDataComponents.APPLIED_UNTIL, appliedUntil.isEmpty() ? null : appliedUntil);
   }
}
