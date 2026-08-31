package com.noveris.vip;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

class NoverisVaultMenu extends AbstractContainerMenu {
    static final int VAULT_SIZE = 54;
    static final int VAULT_X = 58;
    static final int VAULT_Y = 28;
    static final int INVENTORY_X = 58;
    static final int INVENTORY_Y = 151;
    static final int HOTBAR_Y = 211;

    private final Container vault;

    NoverisVaultMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(VAULT_SIZE));
    }

    NoverisVaultMenu(int containerId, Inventory playerInventory, Container vault) {
        super(ModMenus.VAULT.get(), containerId);
        checkContainerSize(vault, VAULT_SIZE);
        this.vault = vault;
        vault.startOpen(playerInventory.player);

        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(vault, column + row * 9,
                        VAULT_X + column * 18, VAULT_Y + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column,
                    INVENTORY_X + column * 18, HOTBAR_Y));
        }
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
    public void removed(Player player) {
        super.removed(player);
        vault.stopOpen(player);
    }
}
