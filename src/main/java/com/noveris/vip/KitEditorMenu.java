package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class KitEditorMenu extends ChestMenu {
    static final int TEMPORARY_FROM = 9;
    static final int TEMPORARY_TO = 26;
    static final int PERMANENT_FROM = 36;
    static final int PERMANENT_TO = 53;
    private static final int SAVE_SLOT = 31;
    private static final int CANCEL_SLOT = 35;
    private final SimpleContainer editor;
    private final VipService service;
    private final String kitName;
    private final VipPlan plan;
    private boolean closedByButton;

    KitEditorMenu(int containerId, Inventory inventory, SimpleContainer editor,
                  VipService service, String kitName, VipPlan plan) {
        super(MenuType.GENERIC_9x6, containerId, inventory, editor, 6);
        this.editor = editor;
        this.service = service;
        this.kitName = kitName;
        this.plan = plan;
        installControls();
    }

    private void installControls() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack pane = new ItemStack(Items.ORANGE_STAINED_GLASS_PANE);
            pane.set(DataComponents.CUSTOM_NAME, Component.literal("ITENS TEMPORÁRIOS — expiram com o VIP")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            editor.setItem(slot, pane);
        }
        for (int slot = 27; slot < 36; slot++) {
            ItemStack pane = new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
            pane.set(DataComponents.CUSTOM_NAME, Component.literal("ITENS PERMANENTES — não expiram")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            editor.setItem(slot, pane);
        }
        ItemStack save = new ItemStack(Items.EMERALD_BLOCK);
        save.set(DataComponents.CUSTOM_NAME, Component.literal("SALVAR KIT").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        editor.setItem(SAVE_SLOT, save);
        ItemStack cancel = new ItemStack(Items.BARRIER);
        cancel.set(DataComponents.CUSTOM_NAME, Component.literal("CANCELAR").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        editor.setItem(CANCEL_SLOT, cancel);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isHeader(slotId)) {
            if (slotId == SAVE_SLOT && player instanceof ServerPlayer serverPlayer) {
                service.saveKit(serverPlayer, kitName, plan, editor);
                closedByButton = true;
                returnInputItems(serverPlayer);
                serverPlayer.closeContainer();
            } else if (slotId == CANCEL_SLOT && player instanceof ServerPlayer serverPlayer) {
                closedByButton = true;
                returnInputItems(serverPlayer);
                serverPlayer.closeContainer();
                serverPlayer.sendSystemMessage(Component.literal("Edição cancelada.").withStyle(ChatFormatting.RED));
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        if (!closedByButton && player instanceof ServerPlayer serverPlayer) returnInputItems(serverPlayer);
        super.removed(player);
    }

    private void returnInputItems(ServerPlayer player) {
        returnRange(player, TEMPORARY_FROM, TEMPORARY_TO);
        returnRange(player, PERMANENT_FROM, PERMANENT_TO);
    }

    private void returnRange(ServerPlayer player, int from, int to) {
        for (int slot = from; slot <= to; slot++) {
            ItemStack stack = editor.removeItemNoUpdate(slot);
            if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
        }
    }

    private boolean isHeader(int slot) { return slot >= 0 && slot < 9 || slot >= 27 && slot < 36; }
}
