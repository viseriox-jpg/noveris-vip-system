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

final class ChoiceCategoryEditorMenu extends ChestMenu {
    static final int TEMPORARY_FROM = 0, TEMPORARY_TO = 26;
    static final int PERMANENT_FROM = 36, PERMANENT_TO = 53;
    private static final int SAVE_SLOT = 31, CANCEL_SLOT = 35;
    private final SimpleContainer editor;
    private final VipService service;
    private final String category;
    private final int limit;
    private boolean closedByButton;

    ChoiceCategoryEditorMenu(int id, Inventory inventory, SimpleContainer editor,
                             VipService service, String category, int limit) {
        super(MenuType.GENERIC_9x6, id, inventory, editor, 6);
        this.editor = editor;
        this.service = service;
        this.category = category;
        this.limit = limit;
        installControls();
    }

    private void installControls() {
        fillHeader(27, Items.ORANGE_STAINED_GLASS_PANE,
                "ACIMA: TEMPORÁRIOS • ABAIXO: PERMANENTES", ChatFormatting.GOLD);
        ItemStack save = new ItemStack(Items.EMERALD_BLOCK);
        save.set(DataComponents.CUSTOM_NAME, Component.literal("SALVAR CATÁLOGO • limite " + limit)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        editor.setItem(SAVE_SLOT, save);
        ItemStack cancel = new ItemStack(Items.BARRIER);
        cancel.set(DataComponents.CUSTOM_NAME, Component.literal("CANCELAR").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        editor.setItem(CANCEL_SLOT, cancel);
    }

    private void fillHeader(int from, net.minecraft.world.item.Item item, String label, ChatFormatting color) {
        for (int slot = from; slot < from + 9; slot++) {
            ItemStack pane = new ItemStack(item);
            pane.set(DataComponents.CUSTOM_NAME, Component.literal(label).withStyle(color, ChatFormatting.BOLD));
            editor.setItem(slot, pane);
        }
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isHeader(slotId)) {
            if (slotId == SAVE_SLOT && player instanceof ServerPlayer staff) {
                service.saveChoiceCategory(staff, category, limit, editor);
                closedByButton = true;
                returnInputs(staff);
                staff.closeContainer();
            } else if (slotId == CANCEL_SLOT && player instanceof ServerPlayer staff) {
                closedByButton = true;
                returnInputs(staff);
                staff.closeContainer();
                staff.sendSystemMessage(Component.literal("Edição do catálogo cancelada.").withStyle(ChatFormatting.RED));
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override public void removed(Player player) {
        if (!closedByButton && player instanceof ServerPlayer staff) returnInputs(staff);
        super.removed(player);
    }

    private void returnInputs(ServerPlayer player) {
        returnRange(player, TEMPORARY_FROM, TEMPORARY_TO);
        returnRange(player, PERMANENT_FROM, PERMANENT_TO);
    }

    private void returnRange(ServerPlayer player, int from, int to) {
        for (int slot = from; slot <= to; slot++) {
            ItemStack stack = editor.removeItemNoUpdate(slot);
            if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
        }
    }

    private boolean isHeader(int slot) { return slot >= 27 && slot < 36; }
}
