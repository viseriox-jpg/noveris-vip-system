package com.noveris.vip;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

class NoverisVaultMenu extends AbstractContainerMenu {
    static final int VAULT_SIZE = 54;
    static final int VAULT_X = 59;
    static final int VAULT_Y = 49;
    static final int SLOT_STEP = 23;

    private final Container vault;

    NoverisVaultMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(VAULT_SIZE));
    }

    NoverisVaultMenu(int containerId, Inventory playerInventory, Container vault) {
        super(ModMenus.VAULT.get(), containerId);
        checkContainerSize(vault, VAULT_SIZE);
        this.vault = vault;
        vault.startOpen(playerInventory.player);

        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(vault, column + row * 9,
                        VAULT_X + column * SLOT_STEP, VAULT_Y + row * SLOT_STEP));
            }
        }
        for (int slot = 45; slot < VAULT_SIZE; slot++)
            addSlot(new Slot(vault, slot, -1000, -1000));
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 45 && slotId < VAULT_SIZE)
            return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        vault.stopOpen(player);
    }
}
