package com.noveris.vip;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class VipItemData {
    private static final String ROOT = "noveris_vip";
    private static final DateTimeFormatter EXPIRY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    static void attach(ItemStack stack, UUID originalOwner, String ownerName,
                       String kit, long expiresAt) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag vip = new CompoundTag();
        vip.putUUID("item_id", UUID.randomUUID());
        vip.putUUID("original_owner", originalOwner);
        vip.putString("original_owner_name", ownerName);
        vip.putString("kit", kit);
        vip.putLong("expires_at", expiresAt);
        root.put(ROOT, vip);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        updateLore(stack, expiresAt, false);
    }

    static Optional<Info> read(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(ROOT)) return Optional.empty();
        CompoundTag vip = root.getCompound(ROOT);
        if (!vip.hasUUID("item_id") || !vip.hasUUID("original_owner")) return Optional.empty();
        return Optional.of(new Info(vip.getUUID("item_id"), vip.getUUID("original_owner"),
                vip.getString("original_owner_name"), vip.getString("kit"), vip.getLong("expires_at")));
    }

    static void renew(ItemStack stack, long expiresAt) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(ROOT)) return;
        CompoundTag vip = root.getCompound(ROOT);
        vip.putLong("expires_at", expiresAt);
        root.put(ROOT, vip);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        updateLore(stack, expiresAt, true);
    }

    private static void updateLore(ItemStack stack, long expiresAt, boolean replacePrevious) {
        ItemLore current = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<Component> lines = new ArrayList<>(current.lines());
        if (replacePrevious && lines.size() >= 3) lines.subList(lines.size() - 3, lines.size()).clear();
        long days = Math.max(1, ChronoUnit.DAYS.between(Instant.now(), Instant.ofEpochMilli(expiresAt)) + 1);
        lines.add(Component.empty());
        lines.add(Component.literal("⌛ Item temporário • " + days + " dias restantes")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        lines.add(Component.literal("Expira em " + EXPIRY_TIME.format(Instant.ofEpochMilli(expiresAt)))
                .withStyle(ChatFormatting.YELLOW));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    record Info(UUID itemId, UUID originalOwner, String originalOwnerName, String kit, long expiresAt) {}
}
