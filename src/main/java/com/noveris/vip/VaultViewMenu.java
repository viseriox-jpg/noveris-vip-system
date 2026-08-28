package com.noveris.vip;

import net.minecraft.world.ClickType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

final class VaultViewMenu extends ChestMenu {
    VaultViewMenu(int id, Inventory inventory, SimpleContainer vault) {
        super(MenuType.GENERIC_9x6, id, inventory, vault, 6);
    }
    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < 54) return;
        super.clicked(slotId, button, clickType, player);
    }
}
