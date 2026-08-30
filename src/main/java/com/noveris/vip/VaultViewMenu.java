package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class VaultViewMenu extends ChestMenu {
    private static final int PREVIOUS = 45, PAGE = 46, NEXT = 52, CLOSE = 53;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private final SimpleContainer display;
    private final Inventory inventory;
    private final List<VipStore.VaultEntry> entries;
    private int page;

    VaultViewMenu(int id, Inventory inventory, SimpleContainer display, List<VipStore.VaultEntry> entries) {
        super(MenuType.GENERIC_9x6, id, inventory, display, 6);
        this.display = display;
        this.inventory = inventory;
        this.entries = entries;
        rebuild();
    }

    private int pages() { return Math.max(1, (entries.size() + 44) / 45); }

    private void rebuild() {
        display.clearContent();
        int offset = page * 45;
        for (int slot = 0; slot < 45 && offset + slot < entries.size(); slot++) {
            int global = offset + slot;
            VipStore.VaultEntry entry = entries.get(global);
            ItemStack stack = VipStore.decode(entry.encodedStack(), inventory.player.registryAccess());
            List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
            lore.add(Component.empty());
            lore.add(Component.literal("Slot global: " + (global + 1))
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            lore.add(Component.literal("Kit: " + entry.kit()).withStyle(ChatFormatting.AQUA));
            lore.add(Component.literal("Arquivado: " + TIME.format(Instant.ofEpochMilli(entry.archivedAt())))
                    .withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Disponível até: " + TIME.format(Instant.ofEpochMilli(entry.deleteAt())))
                    .withStyle(ChatFormatting.GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            display.setItem(slot, stack);
        }
        for (int slot = 45; slot < 54; slot++)
            control(slot, Items.BLACK_STAINED_GLASS_PANE, "Cofre VIP", ChatFormatting.GRAY);
        control(PREVIOUS, page > 0 ? Items.ARROW : Items.GRAY_DYE,
                page > 0 ? "← ANTERIOR" : "SEM PÁGINA ANTERIOR", ChatFormatting.AQUA);
        control(PAGE, Items.PAPER, "PÁGINA " + (page + 1) + "/" + pages()
                + " • " + entries.size() + " itens", ChatFormatting.YELLOW);
        control(NEXT, page + 1 < pages() ? Items.ARROW : Items.GRAY_DYE,
                page + 1 < pages() ? "PRÓXIMA →" : "SEM PRÓXIMA PÁGINA", ChatFormatting.AQUA);
        control(CLOSE, Items.BARRIER, "FECHAR", ChatFormatting.RED);
        broadcastChanges();
    }

    private void control(int slot, net.minecraft.world.item.Item item, String name, ChatFormatting color) {
        ItemStack stack = GuiIcons.fromLegacy(item, name);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD));
        display.setItem(slot, stack);
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == PREVIOUS && page > 0) { page--; rebuild(); return; }
        if (slotId == NEXT && page + 1 < pages()) { page++; rebuild(); return; }
        if (slotId == CLOSE) { player.closeContainer(); return; }
        if (slotId >= 0 && slotId < 54) return;
        super.clicked(slotId, button, clickType, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void removed(Player player) { display.clearContent(); super.removed(player); }
}
