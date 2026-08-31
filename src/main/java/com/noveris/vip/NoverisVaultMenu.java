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
    static final int VAULT_X = 15;
    static final int VAULT_Y = 35;
    static final int FOOTER_Y = 134;

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
                        VAULT_X + column * 18, VAULT_Y + row * 18));
            }
        }
        for (int slot = 45; slot < VAULT_SIZE; slot++) {
            int x = switch (slot) {
                case 45 -> VAULT_X;
                case 52 -> VAULT_X + 8 * 18;
                case 53 -> 167;
                default -> -1000;
            };
            int y = slot == 53 ? 7 : FOOTER_Y;
            addSlot(new Slot(vault, slot, x, y));
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
