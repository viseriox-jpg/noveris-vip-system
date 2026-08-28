package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.Container;
import net.minecraft.core.component.DataComponents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class VipService {
    private VipStore store;
    private MinecraftServer server;
    private final Map<UUID, UUID> lastKnownHolder = new HashMap<>();
    private int ticks;

    private VipStore store(MinecraftServer currentServer) {
        if (store == null || server != currentServer) {
            server = currentServer;
            store = VipStore.load(currentServer);
        }
        return store;
    }

    void openKitEditor(ServerPlayer staff, String kitName, String plan) {
        VipStore current = store(staff.getServer());
        SimpleContainer editor = new SimpleContainer(54);
        VipStore.Kit existing = current.data.kits.get(kitName.toLowerCase());
        if (existing != null) {
            int temporarySlot = KitEditorMenu.TEMPORARY_FROM;
            int permanentSlot = KitEditorMenu.PERMANENT_FROM;
            for (VipStore.KitItem item : existing.items) {
                int slot = item.temporary() ? temporarySlot++ : permanentSlot++;
                if (slot <= (item.temporary() ? KitEditorMenu.TEMPORARY_TO : KitEditorMenu.PERMANENT_TO)) editor.setItem(slot,
                        VipStore.decode(item.encodedStack(), staff.registryAccess()).copy());
            }
        }
        MenuProvider provider = new SimpleMenuProvider((id, inventory, player) ->
                new KitEditorMenu(id, inventory, editor, this, kitName.toLowerCase(), plan),
                Component.literal("Editor VIP — " + kitName));
        staff.openMenu(provider);
    }

    void saveKit(ServerPlayer staff, String kitName, String plan, SimpleContainer editor) {
        VipStore current = store(staff.getServer());
        VipStore.Kit kit = new VipStore.Kit(kitName, plan);
        saveRange(staff, editor, kit, KitEditorMenu.TEMPORARY_FROM, KitEditorMenu.TEMPORARY_TO, true);
        saveRange(staff, editor, kit, KitEditorMenu.PERMANENT_FROM, KitEditorMenu.PERMANENT_TO, false);
        current.data.kits.put(kitName.toLowerCase(), kit);
        current.addHistory(staff.getUUID(), staff.getName().getString(), "KIT_SALVO",
                kitName + " | plano: " + plan + " | itens: " + kit.items.size());
        current.save();
        staff.sendSystemMessage(Component.literal("Kit " + kitName + " salvo com "
                + kit.items.size() + " pilhas de itens.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    private void saveRange(ServerPlayer staff, SimpleContainer editor, VipStore.Kit kit,
                           int from, int to, boolean temporary) {
        for (int slot = from; slot <= to; slot++) {
            ItemStack stack = editor.getItem(slot);
            if (!stack.isEmpty()) kit.items.add(new VipStore.KitItem(
                    VipStore.encode(stack.copy(), staff.registryAccess()), temporary));
        }
    }

    boolean grant(ServerPlayer staff, ServerPlayer target, String kitName, int days) {
        VipStore current = store(staff.getServer());
        VipStore.Kit kit = current.data.kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        long now = System.currentTimeMillis();
        long expiresAt = now + days * 24L * 60 * 60 * 1000;
        current.data.profiles.put(target.getUUID().toString(), new VipStore.Profile(
                target.getName().getString(), kit.plan, kit.name, now, expiresAt));
        for (VipStore.KitItem template : kit.items) {
            ItemStack stack = VipStore.decode(template.encodedStack(), target.registryAccess()).copy();
            if (template.temporary()) VipItemData.attach(stack, target.getUUID(),
                    target.getName().getString(), kit.name, expiresAt);
            if (!target.getInventory().add(stack)) target.drop(stack, false);
        }
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_CONCEDIDO",
                kit.plan + " | kit: " + kit.name + " | " + days + " dias | staff: " + staff.getName().getString());
        current.addHistory(staff.getUUID(), staff.getName().getString(), "VIP_ENTREGUE",
                target.getName().getString() + " | kit: " + kit.name);
        current.save();
        current.data.sentWarnings.remove(target.getUUID().toString());
        target.sendSystemMessage(Component.literal("✦ VIP ATIVADO ✦\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Plano: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(kit.plan).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("  |  Kit: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(kit.name).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\nDuração: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(days + " dias").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
        return true;
    }

    boolean renew(ServerPlayer staff, ServerPlayer target, int days) {
        VipStore current = store(staff.getServer());
        VipStore.Profile old = current.data.profiles.get(target.getUUID().toString());
        if (old == null) return false;
        long base = Math.max(System.currentTimeMillis(), old.expiresAt());
        long newExpiry = base + days * 24L * 60 * 60 * 1000;
        current.data.profiles.put(target.getUUID().toString(), new VipStore.Profile(
                old.playerName(), old.plan(), old.kit(), old.grantedAt(), newExpiry));
        updateExpiry(target, newExpiry);
        current.data.sentWarnings.remove(target.getUUID().toString());
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_RENOVADO",
                days + " dias | staff: " + staff.getName().getString());
        current.save();
        return true;
    }

    boolean deliverPermanentItems(ServerPlayer staff, ServerPlayer target, String kitName) {
        VipStore current = store(staff.getServer());
        VipStore.Kit kit = current.data.kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        for (VipStore.KitItem template : kit.items) {
            if (template.temporary()) continue;
            ItemStack stack = VipStore.decode(template.encodedStack(), target.registryAccess()).copy();
            if (!target.getInventory().add(stack)) target.drop(stack, false);
        }
        current.addHistory(target.getUUID(), target.getName().getString(), "PERMANENTES_ENTREGUES",
                "kit: " + kit.name + " | staff: " + staff.getName().getString());
        current.save();
        return true;
    }

    boolean renewAndDeliver(ServerPlayer staff, ServerPlayer target, int days) {
        if (!renew(staff, target, days)) return false;
        VipStore current = store(staff.getServer());
        VipStore.Profile profile = current.data.profiles.get(target.getUUID().toString());
        VipStore.Kit kit = current.data.kits.get(profile.kit().toLowerCase());
        if (kit == null) return false;
        for (VipStore.KitItem template : kit.items) {
            ItemStack stack = VipStore.decode(template.encodedStack(), target.registryAccess()).copy();
            if (template.temporary()) VipItemData.attach(stack, target.getUUID(),
                    target.getName().getString(), kit.name, profile.expiresAt());
            if (!target.getInventory().add(stack)) target.drop(stack, false);
        }
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_RENOVADO_COM_KIT",
                days + " dias | staff: " + staff.getName().getString());
        current.save();
        return true;
    }

    boolean removeVip(ServerPlayer staff, ServerPlayer target) {
        VipStore current = store(staff.getServer());
        VipStore.Profile removed = current.data.profiles.remove(target.getUUID().toString());
        if (removed == null) return false;
        for (ItemStack stack : allPlayerStacks(target)) {
            VipItemData.read(stack).ifPresent(info -> {
                if (!info.originalOwner().equals(target.getUUID())) return;
                current.archive(target.getUUID(), target.getName().getString(), stack.copy(), target.registryAccess());
                stack.setCount(0);
            });
        }
        current.data.sentWarnings.remove(target.getUUID().toString());
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_REMOVIDO",
                "plano: " + removed.plan() + " | staff: " + staff.getName().getString());
        current.save();
        return true;
    }

    boolean queueDelivery(MinecraftServer server, UUID playerId, String playerName,
                          String kitName, int days, String staffName) {
        VipStore current = store(server);
        if (!current.data.kits.containsKey(kitName.toLowerCase())) return false;
        current.data.pendingDeliveries.put(playerId.toString(), new VipStore.PendingDelivery(
                playerName, kitName.toLowerCase(), days, staffName, System.currentTimeMillis()));
        current.addHistory(playerId, playerName, "ENTREGA_PENDENTE",
                "kit: " + kitName + " | " + days + " dias | staff: " + staffName);
        current.save();
        return true;
    }

    boolean renewOffline(MinecraftServer server, UUID playerId, String playerName,
                         int days, String staffName) {
        VipStore current = store(server);
        VipStore.Profile old = current.data.profiles.get(playerId.toString());
        if (old == null) return false;
        long newExpiry = Math.max(System.currentTimeMillis(), old.expiresAt())
                + days * 24L * 60 * 60 * 1000;
        current.data.profiles.put(playerId.toString(), new VipStore.Profile(
                playerName, old.plan(), old.kit(), old.grantedAt(), newExpiry));
        current.data.sentWarnings.remove(playerId.toString());
        current.addHistory(playerId, playerName, "VIP_RENOVADO_OFFLINE",
                days + " dias | staff: " + staffName);
        current.save();
        return true;
    }

    void playerLoggedIn(ServerPlayer player) {
        VipStore current = store(player.getServer());
        VipStore.PendingDelivery pending = current.data.pendingDeliveries.remove(player.getUUID().toString());
        if (pending != null) deliverPending(current, player, pending);
        VipStore.Profile profile = current.data.profiles.get(player.getUUID().toString());
        if (profile != null) updateExpiry(player, profile.expiresAt());
        current.save();
    }

    private void deliverPending(VipStore current, ServerPlayer target, VipStore.PendingDelivery pending) {
        VipStore.Kit kit = current.data.kits.get(pending.kit());
        if (kit == null) return;
        long now = System.currentTimeMillis();
        long expiresAt = now + pending.days() * 24L * 60 * 60 * 1000;
        current.data.profiles.put(target.getUUID().toString(), new VipStore.Profile(
                target.getName().getString(), kit.plan, kit.name, now, expiresAt));
        for (VipStore.KitItem template : kit.items) {
            ItemStack stack = VipStore.decode(template.encodedStack(), target.registryAccess()).copy();
            if (template.temporary()) VipItemData.attach(stack, target.getUUID(),
                    target.getName().getString(), kit.name, expiresAt);
            if (!target.getInventory().add(stack)) target.drop(stack, false);
        }
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_ENTREGUE_AO_ENTRAR",
                "kit: " + kit.name + " | staff: " + pending.staffName());
        target.sendSystemMessage(Component.literal("✦ SEU VIP PENDENTE FOI ENTREGUE ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private void updateExpiry(ServerPlayer player, long expiry) {
        for (ItemStack stack : allPlayerStacks(player)) {
            VipItemData.read(stack).ifPresent(info -> {
                if (info.originalOwner().equals(player.getUUID())) VipItemData.renew(stack, expiry);
            });
        }
    }

    void tick(MinecraftServer currentServer) {
        if (++ticks % 20 != 0) return;
        VipStore current = store(currentServer);
        long now = System.currentTimeMillis();
        normalizeSplitStacks(currentServer);
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            scanPlayer(current, player, now);
            sendExpiryWarnings(current, player, now);
        }
        if (ticks % 1200 == 0) scanDroppedItems(currentServer, current, now);
        if (ticks % 1200 == 0) { current.purgeVault(); current.save(); }
    }

    private void normalizeSplitStacks(MinecraftServer server) {
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (ItemStack stack : allPlayerStacks(player)) {
                VipItemData.read(stack).ifPresent(info -> {
                    if (!seen.add(info.itemId())) seen.add(VipItemData.reidentify(stack));
                });
            }
        }
    }

    private void sendExpiryWarnings(VipStore current, ServerPlayer player, long now) {
        VipStore.Profile profile = current.data.profiles.get(player.getUUID().toString());
        if (profile == null || profile.expiresAt() <= now) return;
        long remainingDays = Math.max(1, (profile.expiresAt() - now + 86_399_999L) / 86_400_000L);
        List<Integer> sent = current.data.sentWarnings.computeIfAbsent(player.getUUID().toString(),
                ignored -> new java.util.ArrayList<>());
        for (int warning : VipConfig.load(player.getServer()).warningDays) {
            if (remainingDays <= warning && !sent.contains(warning)) {
                sent.add(warning);
                player.sendSystemMessage(Component.literal("⚠ SEU VIP EXPIRA EM " + remainingDays + " DIA(S)")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                        .append(Component.literal("\nPlano: " + profile.plan() + " | use /vip kits para consultar os planos.")
                                .withStyle(ChatFormatting.GRAY)));
                current.save();
            }
        }
    }

    private void scanDroppedItems(MinecraftServer server, VipStore current, long now) {
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity itemEntity)) continue;
                ItemStack stack = itemEntity.getItem();
                VipItemData.read(stack).ifPresent(info -> {
                    if (info.expiresAt() > now) return;
                    current.archive(info.originalOwner(), info.originalOwnerName(), stack.copy(), level.registryAccess());
                    itemEntity.discard();
                    current.save();
                });
            }
        }
    }

    void scanContainer(MinecraftServer server, Container container) {
        VipStore current = store(server);
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            VipItemData.read(stack).ifPresent(info -> {
                if (info.expiresAt() > now) return;
                current.archive(info.originalOwner(), info.originalOwnerName(), stack.copy(), server.registryAccess());
                stack.setCount(0);
                current.save();
            });
        }
    }

    private void scanPlayer(VipStore current, ServerPlayer player, long now) {
        for (ItemStack stack : allPlayerStacks(player)) {
            VipItemData.read(stack).ifPresent(info -> {
                UUID oldHolder = lastKnownHolder.put(info.itemId(), player.getUUID());
                if (oldHolder != null && !oldHolder.equals(player.getUUID())) {
                    current.addHistory(player.getUUID(), player.getName().getString(), "ITEM_RECEBIDO",
                            stack.getHoverName().getString() + " | kit: " + info.kit());
                    current.addHistory(oldHolder, oldHolder.toString(), "ITEM_TRANSFERIDO",
                            stack.getHoverName().getString() + " | para: " + player.getName().getString());
                    current.save();
                }
                if (info.expiresAt() <= now && !stack.isEmpty()) {
                    current.archive(player.getUUID(), player.getName().getString(), stack.copy(), player.registryAccess());
                    stack.setCount(0);
                    current.save();
                }
            });
        }
    }

    List<ItemStack> allPlayerStacks(ServerPlayer player) {
        List<ItemStack> result = new java.util.ArrayList<>();
        player.getInventory().items.forEach(result::add);
        player.getInventory().armor.forEach(result::add);
        player.getInventory().offhand.forEach(result::add);
        for (int slot = 0; slot < player.getEnderChestInventory().getContainerSize(); slot++)
            result.add(player.getEnderChestInventory().getItem(slot));
        return result;
    }

    VipStore data(MinecraftServer server) { return store(server); }

    void openVault(ServerPlayer staff, ServerPlayer target) {
        VipStore current = store(staff.getServer());
        List<VipStore.VaultEntry> entries = current.data.vault.getOrDefault(
                target.getUUID().toString(), List.of());
        SimpleContainer inventory = new SimpleContainer(54);
        for (int i = 0; i < Math.min(54, entries.size()); i++) {
            inventory.setItem(i, VipStore.decode(entries.get(i).encodedStack(), staff.registryAccess()));
        }
        staff.openMenu(new SimpleMenuProvider((id, inv, player) -> new VaultViewMenu(id, inv, inventory),
                Component.literal("Cofre VIP de " + target.getName().getString() + " | " + entries.size() + " itens")));
    }

    VaultResult restoreVault(ServerPlayer staff, ServerPlayer target, int oneBasedSlot,
                             int days, boolean permanent) {
        VipStore current = store(staff.getServer());
        List<VipStore.VaultEntry> entries = current.data.vault.get(target.getUUID().toString());
        if (entries == null || oneBasedSlot < 1 || oneBasedSlot > entries.size()) return VaultResult.INVALID_SLOT;
        VipStore.VaultEntry entry = entries.get(oneBasedSlot - 1);
        if (entry.deleteAt() <= System.currentTimeMillis()) return VaultResult.EXPIRED_ARCHIVE;
        ItemStack stack = VipStore.decode(entry.encodedStack(), target.registryAccess());
        if (stack.isEmpty()) return VaultResult.INVALID_ITEM;
        if (permanent) VipItemData.makePermanent(stack);
        else VipItemData.renew(stack, System.currentTimeMillis() + days * 86_400_000L);
        if (!target.getInventory().add(stack)) target.drop(stack, false);
        entries.remove(oneBasedSlot - 1);
        current.addHistory(target.getUUID(), target.getName().getString(),
                permanent ? "COFRE_RESTAURADO_PERMANENTE" : "COFRE_RESTAURADO",
                stack.getHoverName().getString() + " | staff: " + staff.getName().getString());
        current.save();
        return VaultResult.SUCCESS;
    }

    boolean deleteVaultEntry(ServerPlayer staff, ServerPlayer target, int oneBasedSlot) {
        VipStore current = store(staff.getServer());
        List<VipStore.VaultEntry> entries = current.data.vault.get(target.getUUID().toString());
        if (entries == null || oneBasedSlot < 1 || oneBasedSlot > entries.size()) return false;
        VipStore.VaultEntry removed = entries.remove(oneBasedSlot - 1);
        current.addHistory(target.getUUID(), target.getName().getString(), "COFRE_EXCLUIDO",
                "kit: " + removed.kit() + " | staff: " + staff.getName().getString());
        current.save();
        return true;
    }

    enum VaultResult { SUCCESS, INVALID_SLOT, EXPIRED_ARCHIVE, INVALID_ITEM }

    boolean openKitPreview(ServerPlayer player, String kitName) {
        VipStore.Kit kit = store(player.getServer()).data.kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        SimpleContainer preview = new SimpleContainer(54);
        fillHeader(preview, 0, Items.ORANGE_STAINED_GLASS_PANE,
                "ITENS TEMPORÁRIOS — duram enquanto o VIP estiver ativo", ChatFormatting.GOLD);
        fillHeader(preview, 27, Items.LIGHT_BLUE_STAINED_GLASS_PANE,
                "ITENS PERMANENTES — continuam após o VIP", ChatFormatting.AQUA);
        int temporarySlot = KitEditorMenu.TEMPORARY_FROM;
        int permanentSlot = KitEditorMenu.PERMANENT_FROM;
        for (VipStore.KitItem item : kit.items) {
            int slot = item.temporary() ? temporarySlot++ : permanentSlot++;
            int limit = item.temporary() ? KitEditorMenu.TEMPORARY_TO : KitEditorMenu.PERMANENT_TO;
            if (slot <= limit) preview.setItem(slot, VipStore.decode(item.encodedStack(), player.registryAccess()));
        }
        player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new VaultViewMenu(id, inv, preview),
                Component.literal("Kit " + kit.name + " — " + kit.plan)));
        return true;
    }

    private void fillHeader(SimpleContainer inventory, int from, net.minecraft.world.item.Item item,
                            String label, ChatFormatting color) {
        for (int slot = from; slot < from + 9; slot++) {
            ItemStack marker = new ItemStack(item);
            marker.set(DataComponents.CUSTOM_NAME, Component.literal(label).withStyle(color, ChatFormatting.BOLD));
            inventory.setItem(slot, marker);
        }
    }
}
