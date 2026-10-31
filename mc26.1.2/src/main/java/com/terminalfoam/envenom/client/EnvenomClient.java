package com.terminalfoam.envenom.client;

import com.terminalfoam.envenom.Envenom;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** 药包封蜡用对应药水的颜色染色，纸身保持原色。 */
@EventBusSubscriber(modid = Envenom.MODID, value = Dist.CLIENT)
public final class EnvenomClient {
    private EnvenomClient() {
    }

    @SubscribeEvent
    public static void onItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(Envenom.MODID, "potion_pack"), PotionPackTint.MAP_CODEC);
    }
}
