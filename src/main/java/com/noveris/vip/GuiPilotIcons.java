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
    private static final DeferredItem<Item> NEXT = ITEMS.registerSimpleItem(
            "gui_next_pilot", new Item.Properties().stacksTo(1));
    private static final DeferredItem<Item> PAGE = ITEMS.registerSimpleItem(
            "gui_page_pilot", new Item.Properties().stacksTo(1));
    private static final DeferredItem<Item> BACK = ITEMS.registerSimpleItem(
            "gui_back_pilot", new Item.Properties().stacksTo(1));
    private static final DeferredItem<Item> TEMPORARY = ITEMS.registerSimpleItem(
            "gui_temporary_pilot", new Item.Properties().stacksTo(1));
    private static final DeferredItem<Item> PERMANENT = ITEMS.registerSimpleItem(
            "gui_permanent_pilot", new Item.Properties().stacksTo(1));
    private static final DeferredItem<Item> CHOICES = ITEMS.registerSimpleItem(
            "gui_choices_pilot", new Item.Properties().stacksTo(1));
    private static final DeferredItem<Item> INFORMATION = ITEMS.registerSimpleItem(
            "gui_information_pilot", new Item.Properties().stacksTo(1));
    private static final DeferredItem<Item> SELECTED = ITEMS.registerSimpleItem(
            "gui_selected_pilot", new Item.Properties().stacksTo(1));

    static void register(IEventBus bus) { ITEMS.register(bus); }

    static ItemStack fromLegacy(Item item, String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (item == Items.ARROW && upper.contains("ANTERIOR")) return new ItemStack(PREVIOUS.get());
        if (item == Items.ARROW) return new ItemStack(NEXT.get());
        if (item == Items.EMERALD_BLOCK) return new ItemStack(CONFIRM.get());
        if (item == Items.BARRIER) return new ItemStack(CLOSE.get());
        if (item == Items.PAPER) return new ItemStack(PAGE.get());
        if (item == Items.OAK_DOOR) return new ItemStack(BACK.get());
        if (item == Items.CLOCK) return new ItemStack(TEMPORARY.get());
        if (item == Items.DIAMOND) return new ItemStack(PERMANENT.get());
        if (item == Items.CHEST) return new ItemStack(CHOICES.get());
        if (item == Items.NETHER_STAR) return new ItemStack(INFORMATION.get());
        if (item == Items.LIME_DYE) return new ItemStack(SELECTED.get());
        return new ItemStack(item);
    }

    static ItemStack confirm() { return new ItemStack(CONFIRM.get()); }
    static ItemStack close() { return new ItemStack(CLOSE.get()); }
    static ItemStack choices() { return new ItemStack(CHOICES.get()); }
    static ItemStack information() { return new ItemStack(INFORMATION.get()); }

    private GuiPilotIcons() {}
}
