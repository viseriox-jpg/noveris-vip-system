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
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ChoiceSelectionMenu extends ChestMenu {
    private static final int PREVIOUS_SLOT = 45, PAGE_SLOT = 46, SELECTED_SLOT = 47;
    private static final int CONFIRM_SLOT = 49, NEXT_SLOT = 52, CANCEL_SLOT = 53, OPTIONS_PER_PAGE = 45;
    private final SimpleContainer display;
    private final VipService service;
    private final String categoryName;
    private final VipStore.ChoiceCategory category;
    private final List<VipStore.ChoiceItem> options;
    private final HolderLookup.Provider registries;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private int page;

    ChoiceSelectionMenu(int id, Inventory inventory, SimpleContainer display, VipService service,
                        String categoryName, VipStore.ChoiceCategory category) {
        super(MenuType.GENERIC_9x6, id, inventory, display, 6);
        this.display = display;
        this.service = service;
        this.categoryName = categoryName;
        this.category = category;
        this.options = List.copyOf(category.items);
        this.registries = inventory.player.registryAccess();
        rebuild();
    }

    private void rebuild() {
        for (int slot = 0; slot < OPTIONS_PER_PAGE; slot++) display.setItem(slot, ItemStack.EMPTY);
        int offset = page * OPTIONS_PER_PAGE;
        for (int slot = 0; slot < OPTIONS_PER_PAGE && offset + slot < options.size(); slot++) {
            int optionIndex = offset + slot;
            VipStore.ChoiceItem option = options.get(optionIndex);
            ItemStack stack = VipStore.decode(option.encodedStack(), registries).copy();
            List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
            lore.add(Component.empty());
            lore.add(Component.literal(option.temporary() ? "⌛ Temporário" : "◆ Permanente")
                    .withStyle(option.temporary() ? ChatFormatting.GOLD : ChatFormatting.AQUA, ChatFormatting.BOLD));
            lore.add(Component.literal(selected.contains(optionIndex) ? "✔ SELECIONADO" : "Clique para selecionar")
                    .withStyle(selected.contains(optionIndex) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            if (selected.contains(optionIndex)) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            display.setItem(slot, stack);
        }
        for (int slot = 45; slot < 54; slot++) {
            ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            pane.set(DataComponents.CUSTOM_NAME, Component.literal("Escolha " + required() + " opção(ões)")
                    .withStyle(ChatFormatting.GRAY));
            display.setItem(slot, pane);
        }
        control(PREVIOUS_SLOT, Items.ARROW, "← ANTERIOR", ChatFormatting.AQUA);
        control(PAGE_SLOT, Items.PAPER, "PÁGINA " + (page + 1) + "/" + pageCount(), ChatFormatting.YELLOW);
        control(SELECTED_SLOT, Items.LIME_DYE, "SELECIONADOS: " + selected.size() + "/" + required(), ChatFormatting.GREEN);
        ItemStack confirm = new ItemStack(Items.EMERALD_BLOCK);
        confirm.set(DataComponents.CUSTOM_NAME, Component.literal("CONFIRMAR • " + selected.size() + "/" + required())
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        display.setItem(CONFIRM_SLOT, confirm);
        control(NEXT_SLOT, Items.ARROW, "PRÓXIMA →", ChatFormatting.AQUA);
        ItemStack cancel = new ItemStack(Items.BARRIER);
        cancel.set(DataComponents.CUSTOM_NAME, Component.literal("FECHAR E ESCOLHER DEPOIS")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        display.setItem(CANCEL_SLOT, cancel);
        broadcastChanges();
    }

    private void control(int slot, net.minecraft.world.item.Item item, String name, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD));
        display.setItem(slot, stack);
    }

    private int required() { return Math.min(category.limit, options.size()); }
    private int pageCount() { return Math.max(1, (options.size() + OPTIONS_PER_PAGE - 1) / OPTIONS_PER_PAGE); }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (slotId >= 0 && slotId < OPTIONS_PER_PAGE) {
            int optionIndex = page * OPTIONS_PER_PAGE + slotId;
            if (optionIndex >= options.size()) return;
            if (!selected.remove(optionIndex)) {
                if (selected.size() >= required()) {
                    serverPlayer.sendSystemMessage(Component.literal("Você já selecionou o limite desta categoria.")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                selected.add(optionIndex);
            }
            rebuild();
            return;
        }
        if (slotId == PREVIOUS_SLOT && page > 0) { page--; rebuild(); return; }
        if (slotId == NEXT_SLOT && page + 1 < pageCount()) { page++; rebuild(); return; }
        if (slotId == CONFIRM_SLOT) {
            if (selected.size() != required()) {
                serverPlayer.sendSystemMessage(Component.literal("Selecione exatamente " + required() + " opção(ões).")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }
            List<Integer> chosen = List.copyOf(selected);
            if (!service.completeChoice(serverPlayer, categoryName, chosen)) {
                serverPlayer.sendSystemMessage(Component.literal("Esta escolha não está mais disponível.")
                        .withStyle(ChatFormatting.RED));
                serverPlayer.closeContainer();
                return;
            }
            serverPlayer.closeContainer();
            serverPlayer.getServer().execute(() -> service.openChoices(serverPlayer));
            return;
        }
        if (slotId == CANCEL_SLOT) { serverPlayer.closeContainer(); return; }
        if (slotId >= 0 && slotId < 54) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public void removed(Player player) {
        display.clearContent();
        super.removed(player);
    }
}
