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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class LoreRevokeMenu extends ChestMenu {
    private static final int PREVIOUS = 45, PAGE = 46, CONFIRM = 49, NEXT = 52, CLOSE = 53;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private final SimpleContainer display;
    private final LoreService service;
    private final ServerPlayer target;
    private List<LoreService.ActiveRelic> relics;
    private int page;
    private UUID selected;

    LoreRevokeMenu(int id, Inventory inventory, SimpleContainer display, LoreService service, ServerPlayer target) {
        super(MenuType.GENERIC_9x6, id, inventory, display, 6);
        this.display = display; this.service = service; this.target = target;
        rebuild();
    }

    private void rebuild() {
        display.clearContent();
        relics = service.activeRelics(target);
        int pages = Math.max(1, (relics.size() + 44) / 45);
        page = Math.min(page, pages - 1);
        int offset = page * 45;
        for (int slot = 0; slot < 45 && offset + slot < relics.size(); slot++) {
            LoreService.ActiveRelic relic = relics.get(offset + slot);
            ItemStack stack = relic.display().copy();
            List<Component> lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
            lore.add(Component.empty());
            lore.add(Component.literal("Selo: " + relic.id().toString().substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY));
            lore.add(Component.literal("Portador: " + relic.holder()).withStyle(ChatFormatting.AQUA));
            lore.add(Component.literal("Vence: " + TIME.format(Instant.ofEpochMilli(relic.expiresAt())))
                    .withStyle(ChatFormatting.YELLOW));
            lore.add(Component.literal(relic.id().equals(selected) ? "✔ SELECIONADA" : "Clique para selecionar")
                    .withStyle(relic.id().equals(selected) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            if (relic.id().equals(selected)) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            display.setItem(slot, stack);
        }
        for (int slot = 45; slot < 54; slot++) control(slot, Items.BLACK_STAINED_GLASS_PANE, "Revogação de relíquias", ChatFormatting.GRAY);
        control(PREVIOUS, page > 0 ? Items.ARROW : Items.GRAY_DYE, page > 0 ? "← ANTERIOR" : "SEM PÁGINA ANTERIOR", ChatFormatting.AQUA);
        control(PAGE, Items.PAPER, "PÁGINA " + (page + 1) + "/" + pages, ChatFormatting.YELLOW);
        control(CONFIRM, selected == null ? Items.GRAY_DYE : Items.REDSTONE_BLOCK,
                selected == null ? "SELECIONE UMA RELÍQUIA" : "CONFIRMAR REVOGAÇÃO", selected == null ? ChatFormatting.GRAY : ChatFormatting.RED);
        control(NEXT, page + 1 < pages ? Items.ARROW : Items.GRAY_DYE, page + 1 < pages ? "PRÓXIMA →" : "SEM PRÓXIMA PÁGINA", ChatFormatting.AQUA);
        control(CLOSE, Items.BARRIER, "FECHAR", ChatFormatting.RED);
        broadcastChanges();
    }

    private void control(int slot, net.minecraft.world.item.Item item, String name, ChatFormatting color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color, ChatFormatting.BOLD));
        display.setItem(slot, stack);
    }

    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (!(player instanceof ServerPlayer staff)) return;
        if (slotId >= 0 && slotId < 45) {
            int index = page * 45 + slotId;
            if (index < relics.size()) selected = relics.get(index).id();
            rebuild(); return;
        }
        int pages = Math.max(1, (relics.size() + 44) / 45);
        if (slotId == PREVIOUS && page > 0) { page--; rebuild(); return; }
        if (slotId == NEXT && page + 1 < pages) { page++; rebuild(); return; }
        if (slotId == CONFIRM && selected != null) {
            boolean success = service.revoke(staff, target, selected.toString());
            staff.sendSystemMessage(Component.literal(success ? "Relíquia revogada e enviada ao cofre."
                    : "A relíquia não está mais disponível.").withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
            selected = null; rebuild(); return;
        }
        if (slotId == CLOSE) { staff.closeContainer(); return; }
        if (slotId >= 0 && slotId < 54) return;
        super.clicked(slotId, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void removed(Player player) { display.clearContent(); super.removed(player); }
}
