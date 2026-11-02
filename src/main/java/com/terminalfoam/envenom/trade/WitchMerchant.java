package com.terminalfoam.envenom.trade;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;

/** Server-side merchant bound to a real witch; vanilla handles payments and slot synchronization. */
public final class WitchMerchant implements Merchant {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String OFFERS = "envenom:witch_offers";
    private static final String RESTOCK = "envenom:witch_restock";
    private final Mob witch;
    private final MerchantOffers offers;
    private Player customer;
    private MerchantMenu menu;

    public WitchMerchant(Mob witch) {
        this.witch = witch;
        var data = witch.getPersistentData();
        var ops = RegistryOps.create(NbtOps.INSTANCE, witch.registryAccess());
        MerchantOffers parsed;
        try {
            parsed = data.contains(OFFERS)
                    ? MerchantOffers.CODEC.parse(ops, data.get(OFFERS)).getOrThrow()
                    : WitchTrades.create();
        } catch (RuntimeException e) {
            LOGGER.warn("Envenom: discarding unreadable witch offers on {}", witch, e);
            parsed = WitchTrades.create();
        }
        offers = parsed;
        long now = witch.level().getGameTime();
        if (!data.contains(RESTOCK) || now - data.getLong(RESTOCK) >= 24000L) {
            offers.forEach(MerchantOffer::resetUses);
            data.putLong(RESTOCK, now);
        }
        save();
    }

    public static boolean canTrade(Mob witch, Player player) {
        return witch.isAlive() && !witch.isRemoved() && player.isAlive() && !player.isSpectator()
                && witch.level() == player.level() && witch.distanceToSqr(player) <= 16.0D
                && witch.getTarget() == null && witch.hurtTime == 0;
    }

    public boolean canTrade(Player player) {
        return canTrade(this.witch, player);
    }

    public void open(ServerPlayer player) {
        setTradingPlayer(player);
        var id = player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) -> {
            menu = createMenu(containerId, inventory);
            return menu;
        }, Component.translatable("merchant.envenom.witch")));
        if (id.isPresent()) {
            witch.setPersistenceRequired();
            player.sendMerchantOffers(id.getAsInt(), offers, 1, 0, false, true);
        } else {
            setTradingPlayer(null);
        }
    }

    /**
     * 原版 MerchantMenu.playTradeSound() 会把 trader 强转成 Entity；本模组的商人挂在女巫身上、
     * 不是实体，所以快捷交易（Shift 点击结果槽）会 ClassCastException。这里接管结果槽的快捷移动，
     * 跳过那段强转；音效已经由 notifyTrade() 里的 witch.playSound 播放。
     */
    public MerchantMenu createMenu(int containerId, Inventory inventory) {
        return new MerchantMenu(containerId, inventory, this) {
            @Override
            public boolean stillValid(Player viewer) {
                return customer == viewer && canTrade(viewer);
            }

            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                if (index != 2) {
                    return super.quickMoveStack(player, index);
                }
                Slot slot = this.slots.get(2);
                if (!slot.hasItem()) {
                    return ItemStack.EMPTY;
                }
                ItemStack stack = slot.getItem();
                ItemStack copy = stack.copy();
                if (!this.moveItemStackTo(stack, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, copy);
                if (stack.isEmpty()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
                if (stack.getCount() == copy.getCount()) {
                    return ItemStack.EMPTY;
                }
                slot.onTake(player, stack);
                return copy;
            }
        };
    }

    public boolean tick() {
        if (customer == null) return false;
        if (customer.containerMenu != menu || !canTrade(customer)) {
            close();
            return false;
        }
        witch.getNavigation().stop();
        witch.getLookControl().setLookAt(customer, 30, 30);
        return true;
    }

    public void close() {
        Player previous = customer;
        customer = null;
        if (previous != null && previous.containerMenu == menu) previous.closeContainer();
    }

    private void save() {
        try {
            witch.getPersistentData().put(OFFERS, MerchantOffers.CODEC
                    .encodeStart(RegistryOps.create(NbtOps.INSTANCE, witch.registryAccess()), offers).getOrThrow());
        } catch (RuntimeException e) {
            LOGGER.warn("Envenom: failed to save witch offers on {}", witch, e);
        }
    }

    @Override public void setTradingPlayer(Player player) { customer = player; }
    @Override public Player getTradingPlayer() { return customer; }
    @Override public MerchantOffers getOffers() { return offers; }
    @Override public void overrideOffers(MerchantOffers ignored) { }
    @Override public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        save();
        witch.playSound(SoundEvents.WITCH_CELEBRATE, 1, 1);
    }
    @Override public void notifyTradeUpdated(ItemStack stack) { }
    @Override public int getVillagerXp() { return 0; }
    @Override public void overrideXp(int xp) { }
    @Override public boolean showProgressBar() { return false; }
    @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.WITCH_CELEBRATE; }
    @Override public boolean isClientSide() { return witch.level().isClientSide(); }
}
