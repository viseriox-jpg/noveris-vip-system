package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

final class KitPreviewMenu extends ChestMenu {
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREVIOUS_SLOT = 45, PAGE_SLOT = 46, MODE_SLOT = 47;
    private static final int NEXT_SLOT = 52, CLOSE_SLOT = 53;
    private final SimpleContainer display;
    private final Inventory inventory;
    private final List<VipStore.KitItem> temporary, permanent;
    private int page;
    private boolean temporaryMode = true;

    KitPreviewMenu(int id, Inventory inventory, SimpleContainer display, VipStore.Kit kit) {
        super(MenuType.GENERIC_9x6, id, inventory, display, 6);
        this.inventory = inventory;
        this.display = display;
        this.temporary = kit.items.stream().filter(VipStore.KitItem::temporary).toList();
        this.permanent = kit.items.stream().filter(item -> !item.temporary()).toList();
        rebuild();
    }

    private List<VipStore.KitItem> active() { return temporaryMode ? temporary : permanent; }
    private int pages() { return Math.max(1, (active().size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE); }

    private void rebuild() {
        display.clearContent();
        int offset = page * ITEMS_PER_PAGE;
        for (int slot = 0; slot < ITEMS_PER_PAGE && offset + slot < active().size(); slot++)
            display.setItem(slot, VipStore.decode(active().get(offset + slot).encodedStack(), inventory.player.registryAccess()));
        for (int slot = 45; slot < 54; slot++) control(slot, Items.BLACK_STAINED_GLASS_PANE, "Visualização do kit", ChatFormatting.GRAY);
        control(PREVIOUS_SLOT, Items.ARROW, "← ANTERIOR", ChatFormatting.AQUA);
        control(PAGE_SLOT, Items.PAPER, "PÁGINA " + (page + 1) + "/" + pages(), ChatFormatting.YELLOW);
        control(MODE_SLOT, temporaryMode ? Items.CLOCK : Items.DIAMOND,
                temporaryMode ? "ITENS TEMPORÁRIOS" : "ITENS PERMANENTES",
                temporaryMode ? ChatFormatting.GOLD : ChatFormatting.AQUA);
        control(NEXT_SLOT, Items.ARROW, "PRÓXIMA →", ChatFormatting.AQUA);
        control(CLOSE_SLOT, Items.BARRIER, "FECHAR", ChatFormatting.RED);
        broadcastChanges();
    }

    private void control(int slot, net.minecraft.world.item.Item item, String name, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD));
        display.setItem(slot, stack);
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (slotId == PREVIOUS_SLOT && page > 0) { page--; rebuild(); return; }
        if (slotId == NEXT_SLOT && page + 1 < pages()) { page++; rebuild(); return; }
        if (slotId == MODE_SLOT) { temporaryMode = !temporaryMode; page = 0; rebuild(); return; }
        if (slotId == CLOSE_SLOT) { serverPlayer.closeContainer(); return; }
        if (slotId >= 0 && slotId < 54) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void removed(Player player) { display.clearContent(); super.removed(player); }
}
