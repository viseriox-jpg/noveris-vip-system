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
    private static final int PREVIOUS_SLOT = 45, TEMPORARY_SLOT = 46, PERMANENT_SLOT = 47;
    private static final int CHOICES_SLOT = 48, INFORMATION_SLOT = 49, PAGE_SLOT = 50;
    private static final int BACK_SLOT = 51, NEXT_SLOT = 52, CLOSE_SLOT = 53;
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
        navigation(PREVIOUS_SLOT, page > 0, "← ANTERIOR");
        tab(TEMPORARY_SLOT, Items.CLOCK, "TEMPORÁRIOS", ChatFormatting.GOLD, 0,
                temporary.size() + " pilha(s) enquanto o VIP estiver ativo");
        tab(PERMANENT_SLOT, Items.DIAMOND, "PERMANENTES", ChatFormatting.AQUA, 1,
                permanent.size() + " pilha(s) que não expiram");
        tab(CHOICES_SLOT, Items.CHEST, "ESCOLHAS", ChatFormatting.LIGHT_PURPLE, 2,
                categories.size() + " categoria(s) vinculada(s)");
        tab(INFORMATION_SLOT, Items.NETHER_STAR, "INFORMAÇÕES", ChatFormatting.YELLOW, 3,
                "Resumo completo do plano");
        control(PAGE_SLOT, Items.PAPER, "PÁGINA " + (page + 1) + "/" + pages(), ChatFormatting.YELLOW);
        if (openCategory != null) control(BACK_SLOT, Items.OAK_DOOR, "VOLTAR ÀS CATEGORIAS", ChatFormatting.YELLOW);
        navigation(NEXT_SLOT, page + 1 < pages(), "PRÓXIMA →");
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
                long temporaryOptions = category.items.stream().filter(VipStore.ChoiceItem::temporary).count();
                ItemStack icon = category.items.isEmpty() ? new ItemStack(Items.CHEST)
                        : VipStore.decode(category.items.getFirst().encodedStack(), inventory.player.registryAccess()).copy();
                icon.setCount(1);
                icon.set(DataComponents.CUSTOM_NAME, Component.literal(name.toUpperCase())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                icon.set(DataComponents.LORE, new ItemLore(List.of(
                        Component.literal("Escolha " + category.limit + " opção(ões)").withStyle(ChatFormatting.YELLOW),
                        Component.literal(category.items.size() + " opções disponíveis").withStyle(ChatFormatting.GRAY),
                        Component.literal("⌛ " + temporaryOptions + " temporárias  ◆ "
                                + (category.items.size() - temporaryOptions) + " permanentes")
                                .withStyle(ChatFormatting.AQUA),
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

    private void tab(int slot, net.minecraft.world.item.Item item, String name, ChatFormatting color,
                     int tabMode, String description) {
        ItemStack stack = GuiPilotIcons.fromLegacy(item, name);
        boolean active = mode == tabMode;
        stack.set(DataComponents.CUSTOM_NAME, Component.literal((active ? "✔ " : "") + name)
                .withStyle(active ? ChatFormatting.GREEN : color, ChatFormatting.BOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(description).withStyle(ChatFormatting.GRAY),
                Component.literal(active ? "Seção atual" : "Clique para abrir")
                        .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.AQUA))));
        if (active) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        display.setItem(slot, stack);
    }

    private void navigation(int slot, boolean enabled, String name) {
        control(slot, enabled ? Items.ARROW : Items.GRAY_DYE,
                enabled ? name : "SEM OUTRA PÁGINA", enabled ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY);
    }
    private void control(int slot, net.minecraft.world.item.Item item, String name, ChatFormatting color) {
        ItemStack stack = GuiPilotIcons.fromLegacy(item, name);
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
        if (slotId >= TEMPORARY_SLOT && slotId <= INFORMATION_SLOT) {
            mode = slotId - TEMPORARY_SLOT;
            page = 0;
            openCategory = mode == 2 && categories.size() == 1 ? categories.getFirst() : null;
            rebuild();
            return;
        }
        if (slotId == BACK_SLOT && openCategory != null) { openCategory = null; page = 0; rebuild(); return; }
        if (slotId == CLOSE_SLOT) { serverPlayer.closeContainer(); return; }
        if (slotId >= 0 && slotId < 54) return;
        super.clicked(slotId, button, clickType, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void removed(Player player) { display.clearContent(); super.removed(player); }
}
