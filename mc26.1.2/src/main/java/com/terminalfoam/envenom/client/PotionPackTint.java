package com.terminalfoam.envenom.client;

import com.mojang.serialization.MapCodec;
import com.terminalfoam.envenom.item.PotionPackItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jspecify.annotations.Nullable;

/** 药包第二层纹理染成药水颜色。纸身那一层用常量白色。只从客户端注册。 */
public record PotionPackTint() implements net.minecraft.client.color.item.ItemTintSource {
    public static final PotionPackTint INSTANCE = new PotionPackTint();
    public static final MapCodec<PotionPackTint> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        if (!(stack.getItem() instanceof PotionPackItem pack)) {
            return 0xFFFFFFFF;
        }
        int rgb = PotionContents.getColorOptional(pack.potion().value().getEffects()).orElse(0xFFFFFF);
        return ARGB.opaque(rgb);
    }

    @Override
    public MapCodec<PotionPackTint> type() {
        return MAP_CODEC;
    }
}
