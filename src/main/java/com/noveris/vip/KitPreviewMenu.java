package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

final class KitPreviewMenu extends ChestMenu {
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREVIOUS_SLOT = 45, PAGE_SLOT = 46, MODE_SLOT = 47, BACK_SLOT = 48;
    private static final int NEXT_SLOT = 52, CLOSE_SLOT = 53;
    private final SimpleContainer display;
    private final Inventory inventory;
    private final VipStore store;
    private final VipStore.Kit kit;
    private final List<VipStore.KitItem> temporary, permanent;
    private final List<String> categories;
    private int page, mode;
    private String openCategory;

    KitPreviewMenu(int id, Inventory inventory, SimpleContainer display, VipStore store, VipStore.Kit kit) {
        super(MenuType.GENERIC_9x6, id, inventory, display, 6);
        this.inventory = inventory;
        this.display = display;
        this.store = store;
        this.kit = kit;
        this.temporary = kit.items.stream().filter(VipStore.KitItem::temporary).toList();
        this.permanent = kit.items.stream().filter(item -> !item.temporary()).toList();
        this.categories = List.copyOf(store.data.planChoiceCategories.getOrDefault(kit.plan, List.of()));
        rebuild();
    }

    private int size() {
        if (mode == 0) return temporary.size();
        if (mode == 1) return permanent.size();
        if (mode == 2 && openCategory == null) return categories.size();
        if (mode == 2) {
            VipStore.ChoiceCategory category = store.data.choiceCategories.get(openCategory);
            return category == null ? 0 : category.items.size();
        }
        return 1;
    }

    private int pages() { return Math.max(1, (size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE); }

    private void rebuild() {
        display.clearContent();
        if (mode <= 1) showKitItems(mode == 0 ? temporary : permanent);
        else if (mode == 2) showChoices();
        else showInformation();
        for (int slot = 45; slot < 54; slot++) control(slot, Items.BLACK_STAINED_GLASS_PANE, "Vitrine VIP", ChatFormatting.GRAY);
        control(PREVIOUS_SLOT, Items.ARROW, "← ANTERIOR", ChatFormatting.AQUA);
        control(PAGE_SLOT, Items.PAPER, "PÁGINA " + (page + 1) + "/" + pages(), ChatFormatting.YELLOW);
        control(MODE_SLOT, modeItem(), modeName(), modeColor());
        if (openCategory != null) control(BACK_SLOT, Items.OAK_DOOR, "VOLTAR ÀS CATEGORIAS", ChatFormatting.YELLOW);
        control(NEXT_SLOT, Items.ARROW, "PRÓXIMA →", ChatFormatting.AQUA);
        control(CLOSE_SLOT, Items.BARRIER, "FECHAR", ChatFormatting.RED);
        broadcastChanges();
    }

    private void showKitItems(List<VipStore.KitItem> items) {
        int offset = page * ITEMS_PER_PAGE;
        for (int slot = 0; slot < ITEMS_PER_PAGE && offset + slot < items.size(); slot++)
            display.setItem(slot, VipStore.decode(items.get(offset + slot).encodedStack(), inventory.player.registryAccess()));
    }

    private void showChoices() {
        int offset = page * ITEMS_PER_PAGE;
        if (openCategory == null) {
            for (int slot = 0; slot < ITEMS_PER_PAGE && offset + slot < categories.size(); slot++) {
                String name = categories.get(offset + slot);
                VipStore.ChoiceCategory category = store.data.choiceCategories.get(name);
                if (category == null) continue;
                ItemStack icon = new ItemStack(Items.CHEST);
                icon.set(DataComponents.CUSTOM_NAME, Component.literal(name.toUpperCase())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                icon.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("Escolha " + category.limit + " opção(ões)").withStyle(ChatFormatting.YELLOW),
                        Component.literal(category.items.size() + " opções disponíveis").withStyle(ChatFormatting.GRAY),
                        Component.literal("Clique para visualizar").withStyle(ChatFormatting.AQUA))));
                display.setItem(slot, icon);
            }
            return;
        }
        VipStore.ChoiceCategory category = store.data.choiceCategories.get(openCategory);
        if (category == null) return;
        for (int slot = 0; slot < ITEMS_PER_PAGE && offset + slot < category.items.size(); slot++) {
            VipStore.ChoiceItem option = category.items.get(offset + slot);
            ItemStack stack = VipStore.decode(option.encodedStack(), inventory.player.registryAccess()).copy();
            List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
            lore.add(Component.empty());
            lore.add(Component.literal(option.temporary() ? "⌛ Temporário" : "◆ Permanente")
                    .withStyle(option.temporary() ? ChatFormatting.GOLD : ChatFormatting.AQUA, ChatFormatting.BOLD));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            display.setItem(slot, stack);
        }
    }

    private void showInformation() {
        VipStore.PlanDefinition plan = store.data.plans.get(kit.plan);
        int choices = categories.stream().map(store.data.choiceCategories::get).filter(java.util.Objects::nonNull)
                .mapToInt(category -> category.limit).sum();
        ItemStack info = new ItemStack(Items.NETHER_STAR);
        info.set(DataComponents.CUSTOM_NAME, Component.literal(plan == null ? kit.plan : plan.displayName())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        info.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Kit: " + kit.name).withStyle(ChatFormatting.AQUA),
                Component.literal("Temporários: " + temporary.size()).withStyle(ChatFormatting.YELLOW),
                Component.literal("Permanentes: " + permanent.size()).withStyle(ChatFormatting.GREEN),
                Component.literal("Categorias de escolha: " + categories.size()).withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.literal("Total que pode escolher: " + choices).withStyle(ChatFormatting.GOLD))));
        display.setItem(22, info);
    }

    private net.minecraft.world.item.Item modeItem() {
        return switch (mode) { case 0 -> Items.CLOCK; case 1 -> Items.DIAMOND; case 2 -> Items.CHEST; default -> Items.NETHER_STAR; };
    }
    private String modeName() {
        return switch (mode) { case 0 -> "TEMPORÁRIOS"; case 1 -> "PERMANENTES"; case 2 -> openCategory == null
                ? "ESCOLHAS" : "ESCOLHAS: " + openCategory; default -> "INFORMAÇÕES"; };
    }
    private ChatFormatting modeColor() {
        return switch (mode) { case 0 -> ChatFormatting.GOLD; case 1 -> ChatFormatting.AQUA;
            case 2 -> ChatFormatting.LIGHT_PURPLE; default -> ChatFormatting.YELLOW; };
    }
    private void control(int slot, net.minecraft.world.item.Item item, String name, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD));
        display.setItem(slot, stack);
    }

    @Override public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (mode == 2 && openCategory == null && slotId >= 0 && slotId < ITEMS_PER_PAGE) {
            int index = page * ITEMS_PER_PAGE + slotId;
            if (index < categories.size()) { openCategory = categories.get(index); page = 0; rebuild(); }
            return;
        }
        if (slotId == PREVIOUS_SLOT && page > 0) { page--; rebuild(); return; }
        if (slotId == NEXT_SLOT && page + 1 < pages()) { page++; rebuild(); return; }
        if (slotId == MODE_SLOT) { mode = (mode + 1) % 4; page = 0; openCategory = null; rebuild(); return; }
        if (slotId == BACK_SLOT && openCategory != null) { openCategory = null; page = 0; rebuild(); return; }
        if (slotId == CLOSE_SLOT) { serverPlayer.closeContainer(); return; }
        if (slotId >= 0 && slotId < 54) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void removed(Player player) { display.clearContent(); super.removed(player); }
}
