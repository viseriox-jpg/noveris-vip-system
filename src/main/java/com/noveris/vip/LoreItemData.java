package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class LoreItemData {
    private static final String ROOT = "noveris_lore";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    static void attach(ItemStack stack, UUID owner, String ownerName, long expiresAt,
                       boolean transferable, String reason, String granter) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag lore = new CompoundTag();
        lore.putUUID("item_id", UUID.randomUUID());
        lore.putUUID("owner", owner);
        lore.putString("owner_name", ownerName);
        lore.putLong("expires_at", expiresAt);
        lore.putBoolean("transferable", transferable);
        lore.putString("reason", reason);
        lore.putString("granter", granter);
        root.put(ROOT, lore);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        updateLore(stack, new Info(lore.getUUID("item_id"), owner, ownerName, expiresAt,
                transferable, reason, granter));
    }

    static Optional<Info> read(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(ROOT)) return Optional.empty();
        CompoundTag lore = root.getCompound(ROOT);
        if (!lore.hasUUID("item_id") || !lore.hasUUID("owner")) return Optional.empty();
        return Optional.of(new Info(lore.getUUID("item_id"), lore.getUUID("owner"),
                lore.getString("owner_name"), lore.getLong("expires_at"), lore.getBoolean("transferable"),
                lore.getString("reason"), lore.getString("granter")));
    }

    static void restore(ItemStack stack, long expiresAt) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(ROOT)) return;
        CompoundTag lore = root.getCompound(ROOT);
        lore.putUUID("item_id", UUID.randomUUID());
        lore.putLong("expires_at", expiresAt);
        root.put(ROOT, lore);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        read(stack).ifPresent(info -> updateLore(stack, info));
    }

    static UUID reidentify(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(ROOT)) return null;
        CompoundTag lore = root.getCompound(ROOT);
        UUID id = UUID.randomUUID();
        lore.putUUID("item_id", id);
        root.put(ROOT, lore);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return id;
    }

    static void makePermanent(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.remove(ROOT);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        ItemLore current = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<Component> lines = new ArrayList<>(current.lines());
        int marker = findMarker(lines);
        if (marker >= 0) lines.subList(marker, lines.size()).clear();
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    static boolean hasTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(ROOT);
    }

    private static void updateLore(ItemStack stack, Info info) {
        List<Component> lines = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        int marker = findMarker(lines);
        if (marker >= 0) lines.subList(marker, lines.size()).clear();
        lines.add(Component.literal("✦ RELÍQUIA CONCEDIDA ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(Component.literal("Vinculada a: " + info.ownerName()).withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Selo: " + info.itemId().toString().substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.literal(info.transferable() ? "Vínculo: transferível" : "Vínculo: pessoal")
                .withStyle(info.transferable() ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.literal("Expira em " + TIME.format(Instant.ofEpochMilli(info.expiresAt())))
                .withStyle(ChatFormatting.YELLOW));
        if (!info.reason().isBlank()) lines.add(Component.literal(info.reason()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static int findMarker(List<Component> lines) {
        for (int i = 0; i < lines.size(); i++)
            if (lines.get(i).getString().equals("✦ RELÍQUIA CONCEDIDA ✦")) return i;
        return -1;
    }

    record Info(UUID itemId, UUID owner, String ownerName, long expiresAt,
                boolean transferable, String reason, String granter) {}
}
