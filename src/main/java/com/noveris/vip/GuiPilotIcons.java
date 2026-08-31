package com.noveris.vip;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class GuiPilotIcons {
    static ItemStack fromLegacy(Item item, String name) {
        return new ItemStack(item);
    }

    static ItemStack confirm() { return new ItemStack(Items.EMERALD_BLOCK); }
    static ItemStack close() { return new ItemStack(Items.BARRIER); }
    static ItemStack choices() { return new ItemStack(Items.CHEST); }
    static ItemStack information() { return new ItemStack(Items.NETHER_STAR); }

    private GuiPilotIcons() {}
}
