package com.terminalfoam.envenom.client;

import com.terminalfoam.envenom.Envenom;
import com.terminalfoam.envenom.item.EnvenomItems;
import com.terminalfoam.envenom.item.PotionPackItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 药包封蜡用对应药水的颜色染色，纸身保持原色。 */
@EventBusSubscriber(modid = Envenom.MODID, value = Dist.CLIENT)
public final class EnvenomClient {
    private EnvenomClient() {
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        Item[] packs = EnvenomItems.packHolders().stream()
                .map(DeferredHolder::get)
                .toArray(Item[]::new);
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1 || !(stack.getItem() instanceof PotionPackItem pack)) {
                return 0xFFFFFF;
            }
            return PotionContents.getColor(pack.potion());
        }, packs);
    }
}
