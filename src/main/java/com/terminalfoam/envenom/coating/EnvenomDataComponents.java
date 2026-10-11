package com.terminalfoam.envenom.coating;

import com.mojang.serialization.Codec;
import com.terminalfoam.envenom.Envenom;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnvenomDataComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Envenom.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Coating>>> COATINGS =
            REGISTRY.register("coatings", () -> DataComponentType.<List<Coating>>builder()
                    .persistent(Coating.CODEC.listOf())
                    .networkSynchronized(Coating.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());

    /**
     * 触发冷却：效果 id → 上次触发时刻。
     *
     * <p><b>为什么放在物品上而不是静态表</b>：冷却的作用域本来就是"这一件物品"——
     * 剑柄与盔甲两条路径都在持有该物品的实体上判定。存成静态 Map 会带来三个问题：
     * 服务器重启即丢失、联机/单机切换串味、以及"区块卸载→条目被清→重新加载立刻多触发一次"。
     * 挂到物品上则生命周期跟着物品走，这三个问题一起消失。
     *
     * <p><b>键不含槽位</b>：一件物品只可能被一种槽位使用（剑柄看主/副手，盔甲看装备槽），
     * 所以同一件物品上的键只需区分效果。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, Long>>> TRIGGER_COOLDOWNS =
            REGISTRY.register("trigger_cooldowns", () -> DataComponentType.<Map<String, Long>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG))
                    .networkSynchronized(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG))
                    .build());
}
