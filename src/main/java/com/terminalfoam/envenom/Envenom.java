package com.terminalfoam.envenom;

import com.terminalfoam.envenom.coating.EnvenomDataComponents;
import com.terminalfoam.envenom.config.EnvenomConfig;
import com.terminalfoam.envenom.handler.CoatingApplicationHandler;
import com.terminalfoam.envenom.handler.CoatingEffectHandler;
import com.terminalfoam.envenom.handler.CoatingTooltipHandler;
import com.terminalfoam.envenom.handler.InjectionHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Envenom.MODID)
public class Envenom {
    public static final String MODID = "envenom";

    public Envenom(IEventBus modEventBus, ModContainer modContainer) {
        EnvenomDataComponents.REGISTRY.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, EnvenomConfig.SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, EnvenomConfig::onLoad);
        modEventBus.addListener(ModConfigEvent.Reloading.class, EnvenomConfig::onLoad);

        NeoForge.EVENT_BUS.register(CoatingApplicationHandler.class);
        NeoForge.EVENT_BUS.register(CoatingEffectHandler.class);
        NeoForge.EVENT_BUS.register(CoatingTooltipHandler.class);
        NeoForge.EVENT_BUS.register(InjectionHandler.class);
    }
}
