package com.terminalfoam.envenom.coating;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

public record Coating(
        Holder<MobEffect> effect,
        int amplifier,
        int durationTicks,
        int remainingUses,
        CoatingSlot slot,
        boolean lingering
) {
    /**
     * 序列化字段名 "duration" 对应 record 字段 durationTicks(历史命名),
     * 勿改名,以免破坏已存在存档中涂层数据的解析。
     */
    public static final Codec<Coating> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MobEffect.CODEC.fieldOf("effect").forGetter(Coating::effect),
            Codec.INT.fieldOf("amplifier").forGetter(Coating::amplifier),
            Codec.INT.fieldOf("duration").forGetter(Coating::durationTicks),
            Codec.INT.fieldOf("remaining_uses").forGetter(Coating::remainingUses),
            CoatingSlot.CODEC.fieldOf("slot").forGetter(Coating::slot),
            Codec.BOOL.fieldOf("lingering").forGetter(Coating::lingering)
    ).apply(instance, Coating::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Coating> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.MOB_EFFECT),
            Coating::effect,
            ByteBufCodecs.VAR_INT,
            Coating::amplifier,
            ByteBufCodecs.VAR_INT,
            Coating::durationTicks,
            ByteBufCodecs.VAR_INT,
            Coating::remainingUses,
            CoatingSlot.STREAM_CODEC,
            Coating::slot,
            ByteBufCodecs.BOOL,
            Coating::lingering,
            Coating::new
    );

    public MobEffectInstance toEffectInstance() {
        return new MobEffectInstance(effect, durationTicks, amplifier);
    }

    public Coating withRemainingUses(int uses) {
        return new Coating(effect, amplifier, durationTicks, uses, slot, lingering);
    }

    /**
     * 扣 {@code amount} 次涂层次数。
     *
     * @return 扣完后的涂层；{@code null} 表示用尽（剩余 ≤ 0），调用方应丢弃这张涂层
     */
    public Coating decrementUses(int amount) {
        int newUses = remainingUses - amount;
        return newUses <= 0 ? null : new Coating(effect, amplifier, durationTicks, newUses, slot, lingering);
    }

    public Coating decrementUses() {
        return decrementUses(1);
    }
}
