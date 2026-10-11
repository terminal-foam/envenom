package com.terminalfoam.envenom.enchantment;

import com.terminalfoam.envenom.Envenom;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Envenom 模组自定义附魔的引用。
 *
 * <p>附魔本身通过 datapack JSON（{@code data/envenom/enchantment/*.json}）注册，
 * {@code Holder} 在运行时通过 {@link RegistryAccess} 查找。
 *
 * <p><b>C4：五个同构 getter 收敛成一次查表。</b> 旧写法是每个附魔各写一个
 * {@code Xxx(ra)} 方法，五行一模一样的 {@code ra.lookupOrThrow(...).getOrThrow(KEY)}；
 * 新增一个附魔要同时改三处（常量、getter、调用方）。
 * 现在只维护一张 {@link #KEYS} 表，取值走 {@link #holder}。
 *
 * <p>键是 {@code data/envenom/enchantment/} 下的文件名，必须一一对应 ——
 * 写错会在 {@code getOrThrow} 处抛异常而不是静默失败，这是有意的。
 */
public class EnvenomEnchantments {

    /** 附魔路径名 → ResourceKey。路径名即 datapack json 的文件名。 */
    private static final Map<String, ResourceKey<Enchantment>> KEYS = Map.of(
            "sticky", key("sticky"),
            "amplification", key("amplification"),
            "schrodingers_blessing", key("schrodingers_blessing"),
            "stacking", key("stacking"),
            "injection", key("injection")
    );

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(Envenom.MODID, path));
    }

    /**
     * 按路径名取附魔 Holder。
     *
     * @throws IllegalArgumentException 名字不在 {@link #KEYS} 里（拼错名字属于编程错误，早炸早好）
     * @throws IllegalStateException    附魔没被 datapack 注册（同样是配置错误）
     */
    public static Holder<Enchantment> holder(RegistryAccess ra, String name) {
        ResourceKey<Enchantment> k = KEYS.get(name);
        if (k == null) {
            throw new IllegalArgumentException("Envenom: unknown enchantment '" + name + "'");
        }
        return ra.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(k);
    }

    public static Holder<Enchantment> sticky(RegistryAccess ra) {
        return holder(ra, "sticky");
    }

    public static Holder<Enchantment> amplification(RegistryAccess ra) {
        return holder(ra, "amplification");
    }

    public static Holder<Enchantment> schrodingersBlessing(RegistryAccess ra) {
        return holder(ra, "schrodingers_blessing");
    }

    public static Holder<Enchantment> stacking(RegistryAccess ra) {
        return holder(ra, "stacking");
    }

    public static Holder<Enchantment> injection(RegistryAccess ra) {
        return holder(ra, "injection");
    }
}
