package com.noveris.vip;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.UUID;

final class VipItemData {
    private static final String ROOT = "noveris_vip";

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
    }

    record Info(UUID itemId, UUID originalOwner, String originalOwnerName, String kit, long expiresAt) {}
}
