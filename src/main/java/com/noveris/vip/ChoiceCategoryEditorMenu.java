package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
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

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

final class ChoiceCategoryEditorMenu extends ChestMenu {
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREVIOUS_SLOT = 45, PAGE_SLOT = 46, MODE_SLOT = 47;
    private static final int SAVE_SLOT = 49, NEXT_SLOT = 52, CANCEL_SLOT = 53;
    private final SimpleContainer display;
    private final VipService service;
    private final String category;
    private final int limit;
    private final List<ItemStack> temporary = new ArrayList<>();
    private final List<ItemStack> permanent = new ArrayList<>();
    private final Map<String, Integer> originalCounts = new HashMap<>();
    private final HolderLookup.Provider registries;
    private int page;
    private boolean temporaryMode = true;
    private boolean closedByButton;

    ChoiceCategoryEditorMenu(int id, Inventory inventory, SimpleContainer display, VipService service,
                             String category, int limit, VipStore.ChoiceCategory existing) {
        super(MenuType.GENERIC_9x6, id, inventory, display, 6);
        this.display = display;
        this.service = service;
        this.category = category;
        this.limit = limit;
        this.registries = inventory.player.registryAccess();
        if (existing != null) for (VipStore.ChoiceItem option : existing.items) {
            ItemStack stack = VipStore.decode(option.encodedStack(), registries).copy();
            (option.temporary() ? temporary : permanent).add(stack);
            originalCounts.merge((option.temporary() ? "T:" : "P:") + option.encodedStack(), 1, Integer::sum);
        }
        showPage();
    }

    private List<ItemStack> active() { return temporaryMode ? temporary : permanent; }

    private void capturePage() {
        List<ItemStack> active = active();
        int offset = page * ITEMS_PER_PAGE;
        while (active.size() < offset + ITEMS_PER_PAGE) active.add(ItemStack.EMPTY);
        for (int slot = 0; slot < ITEMS_PER_PAGE; slot++)
            active.set(offset + slot, display.removeItemNoUpdate(slot));
        while (!active.isEmpty() && active.getLast().isEmpty()) active.removeLast();
    }

    private void showPage() {
        for (int slot = 0; slot < ITEMS_PER_PAGE; slot++) display.setItem(slot, ItemStack.EMPTY);
        List<ItemStack> active = active();
        int offset = page * ITEMS_PER_PAGE;
        for (int slot = 0; slot < ITEMS_PER_PAGE && offset + slot < active.size(); slot++)
            display.setItem(slot, active.get(offset + slot));
        installControls();
        broadcastChanges();
    }

    private void installControls() {
        for (int slot = 45; slot < 54; slot++) {
            ItemStack pane = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
            pane.set(DataComponents.CUSTOM_NAME, Component.literal("Editor de opções VIP").withStyle(ChatFormatting.GRAY));
            display.setItem(slot, pane);
        }
        control(PREVIOUS_SLOT, Items.ARROW, "← PÁGINA ANTERIOR", ChatFormatting.AQUA);
        control(PAGE_SLOT, Items.PAPER, "PÁGINA " + (page + 1) + " • " + active().size() + " opções", ChatFormatting.YELLOW);
        control(MODE_SLOT, temporaryMode ? Items.CLOCK : Items.DIAMOND,
                temporaryMode ? "MODO: TEMPORÁRIOS" : "MODO: PERMANENTES",
                temporaryMode ? ChatFormatting.GOLD : ChatFormatting.AQUA);
        control(SAVE_SLOT, Items.EMERALD_BLOCK, "SALVAR CATÁLOGO • escolhe " + limit, ChatFormatting.GREEN);
        control(NEXT_SLOT, Items.ARROW, "PRÓXIMA PÁGINA →", ChatFormatting.AQUA);
        control(CANCEL_SLOT, Items.BARRIER, "CANCELAR", ChatFormatting.RED);
    }

    private void control(int slot, net.minecraft.world.item.Item item, String name, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD));
        display.setItem(slot, stack);
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer staff)) return;
        if (slotId < 45 || slotId >= 54) { super.clicked(slotId, button, clickType, player); return; }
        if (slotId == PREVIOUS_SLOT && page > 0) { capturePage(); page--; showPage(); return; }
        if (slotId == NEXT_SLOT) { capturePage(); page++; showPage(); return; }
        if (slotId == MODE_SLOT) { capturePage(); temporaryMode = !temporaryMode; page = 0; showPage(); return; }
        if (slotId == SAVE_SLOT) {
            capturePage();
            service.saveChoiceCategory(staff, category, limit, temporary, permanent);
            closedByButton = true;
            returnAll(staff);
            staff.closeContainer();
            return;
        }
        if (slotId == CANCEL_SLOT) {
            capturePage();
            closedByButton = true;
            returnAll(staff);
            staff.closeContainer();
            staff.sendSystemMessage(Component.literal("Edição do catálogo cancelada.").withStyle(ChatFormatting.RED));
        }
    }

    @Override public void removed(Player player) {
        if (!closedByButton && player instanceof ServerPlayer staff) { capturePage(); returnAll(staff); }
        display.clearContent();
        super.removed(player);
    }

    private void returnAll(ServerPlayer player) {
        returnNewInputs(player, temporary, true);
        returnNewInputs(player, permanent, false);
        temporary.clear();
        permanent.clear();
    }

    private void returnNewInputs(ServerPlayer player, List<ItemStack> stacks, boolean temporaryType) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            String signature = (temporaryType ? "T:" : "P:") + VipStore.encode(stack, registries);
            int originals = originalCounts.getOrDefault(signature, 0);
            if (originals > 0) originalCounts.put(signature, originals - 1);
            else player.getInventory().placeItemBackInInventory(stack);
        }
    }
}
