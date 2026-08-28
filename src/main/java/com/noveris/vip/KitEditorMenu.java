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
    private static final int CONTROL_FROM = 45;
    private static final int SAVE_SLOT = 49;
    private static final int CANCEL_SLOT = 53;
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
        for (int slot = CONTROL_FROM; slot < 54; slot++) {
            ItemStack pane = new ItemStack(slot < SAVE_SLOT ? Items.ORANGE_STAINED_GLASS_PANE
                    : Items.GRAY_STAINED_GLASS_PANE);
            pane.set(DataComponents.CUSTOM_NAME, Component.literal(slot < SAVE_SLOT
                    ? "Acima: temporários | linhas 4-5: permanentes" : "Controle").withStyle(ChatFormatting.GRAY));
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
        if (slotId >= CONTROL_FROM && slotId < 54) {
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
        for (int slot = 0; slot < CONTROL_FROM; slot++) {
            ItemStack stack = editor.removeItemNoUpdate(slot);
            if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
        }
    }
}
