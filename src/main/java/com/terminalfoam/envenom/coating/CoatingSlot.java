package com.terminalfoam.envenom.coating;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum CoatingSlot implements StringRepresentable {
    BLADE("blade"),
    HILT("hilt"),
    ARMOR("armor");

    private final String name;

    CoatingSlot(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static final Codec<CoatingSlot> CODEC =
            StringRepresentable.fromEnum(CoatingSlot::values);

    public static final StreamCodec<ByteBuf, CoatingSlot> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(
                    ordinal -> {
                        // 解码方向边界校验:恶意客户端可伪造越界/负 ordinal,
                        // 直接 values()[ordinal] 会抛 ArrayIndexOutOfBoundsException 断连
                        CoatingSlot[] slots = CoatingSlot.values();
                        return ordinal >= 0 && ordinal < slots.length ? slots[ordinal] : CoatingSlot.BLADE;
                    },
                    CoatingSlot::ordinal
            );
}
