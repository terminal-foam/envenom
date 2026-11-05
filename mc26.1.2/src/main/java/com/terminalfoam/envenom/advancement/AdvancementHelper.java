package com.terminalfoam.envenom.advancement;

import com.terminalfoam.envenom.Envenom;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Awards Envenom advancements by id. All criteria are {@code minecraft:impossible}. */
public final class AdvancementHelper {
    private AdvancementHelper() {
    }

    public static void award(@Nullable ServerPlayer player, String path) {
        if (player == null || player.level().getServer() == null) return;
        AdvancementHolder holder = player.level().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(Envenom.MODID, path));
        if (holder != null) {
            player.getAdvancements().award(holder, "code");
        }
    }
}
