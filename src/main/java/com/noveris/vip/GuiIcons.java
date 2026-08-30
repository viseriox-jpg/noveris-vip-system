package com.noveris.vip;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

final class GuiIcons {
    enum Role { PREVIOUS, NEXT, PAGE, BACK, CLOSE, CONFIRM, TEMPORARY, PERMANENT,
        CHOICES, INFORMATION, SELECTED, BLOCKED }
    enum State { AVAILABLE, UNAVAILABLE, SELECTED, CONFIRM, DANGEROUS }

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NoverisVipSystem.MOD_ID);
    private static final Map<Role, Map<State, DeferredItem<Item>>> ICONS = new EnumMap<>(Role.class);

    static {
        for (Role role : Role.values()) {
            Map<State, DeferredItem<Item>> states = new EnumMap<>(State.class);
            for (State state : State.values()) {
                String id = "gui_" + role.name().toLowerCase(Locale.ROOT) + "_"
                        + state.name().toLowerCase(Locale.ROOT);
                states.put(state, ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(1)));
            }
            ICONS.put(role, states);
        }
    }

    static void register(IEventBus bus) { ITEMS.register(bus); }

    static ItemStack stack(Role role, State state) {
        return new ItemStack(ICONS.get(role).get(state).get());
    }

    static ItemStack fromLegacy(Item item, String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        State state = item == Items.GRAY_DYE ? State.UNAVAILABLE
                : item == Items.BARRIER || item == Items.REDSTONE_BLOCK ? State.DANGEROUS
                : item == Items.EMERALD_BLOCK || item == Items.LIME_DYE ? State.CONFIRM
                : upper.startsWith("✔") ? State.SELECTED : State.AVAILABLE;
        Role role;
        if (item == Items.ARROW || item == Items.GRAY_DYE) {
            role = upper.contains("ANTERIOR") ? Role.PREVIOUS : Role.NEXT;
        } else if (item == Items.PAPER) role = Role.PAGE;
        else if (item == Items.OAK_DOOR) role = Role.BACK;
        else if (item == Items.BARRIER) role = Role.CLOSE;
        else if (item == Items.EMERALD_BLOCK || item == Items.REDSTONE_BLOCK) role = Role.CONFIRM;
        else if (item == Items.CLOCK) role = Role.TEMPORARY;
        else if (item == Items.DIAMOND) role = Role.PERMANENT;
        else if (item == Items.CHEST) role = Role.CHOICES;
        else if (item == Items.NETHER_STAR) role = Role.INFORMATION;
        else if (item == Items.LIME_DYE) role = Role.SELECTED;
        else role = Role.BLOCKED;
        return stack(role, state);
    }

    private GuiIcons() {}
}
