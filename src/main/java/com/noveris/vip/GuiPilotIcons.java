package com.noveris.vip;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;

final class GuiPilotIcons {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NoverisVipSystem.MOD_ID);
    private static final DeferredItem<Item> PREVIOUS = ITEMS.registerSimpleItem(
            "gui_previous_pilot", new Item.Properties().stacksTo(1));
    private static final DeferredItem<Item> CONFIRM = ITEMS.registerSimpleItem(
            "gui_confirm_pilot", new Item.Properties().stacksTo(1));
    private static final DeferredItem<Item> CLOSE = ITEMS.registerSimpleItem(
            "gui_close_pilot", new Item.Properties().stacksTo(1));

    static void register(IEventBus bus) { ITEMS.register(bus); }

    static ItemStack fromLegacy(Item item, String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (item == Items.ARROW && upper.contains("ANTERIOR")) return new ItemStack(PREVIOUS.get());
        if (item == Items.EMERALD_BLOCK) return new ItemStack(CONFIRM.get());
        if (item == Items.BARRIER) return new ItemStack(CLOSE.get());
        return new ItemStack(item);
    }

    static ItemStack confirm() { return new ItemStack(CONFIRM.get()); }
    static ItemStack close() { return new ItemStack(CLOSE.get()); }

    private GuiPilotIcons() {}
}
