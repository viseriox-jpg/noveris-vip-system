package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class LoreService {
    private LoreStore store;
    private MinecraftServer server;
    private int ticks;
    private final Map<UUID, UUID> lastHolder = new HashMap<>();
    private final ArrayDeque<Container> containers = new ArrayDeque<>();
    private final Set<Container> queued = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Container> loadedContainers = Collections.newSetFromMap(new IdentityHashMap<>());

    private LoreStore store(MinecraftServer current) {
        if (store == null || server != current) { server = current; store = LoreStore.load(current); }
        return store;
    }

    LoreStore data(MinecraftServer server) { return store(server); }

    boolean grant(ServerPlayer staff, ServerPlayer target, long durationMillis,
                  boolean transferable, String reason) {
        ItemStack held = staff.getMainHandItem();
        if (held.isEmpty() || LoreItemData.hasTag(held) || VipItemData.hasVipTag(held)) return false;
        ItemStack granted = held.copy();
        long expiresAt = System.currentTimeMillis() + durationMillis;
        LoreItemData.attach(granted, target.getUUID(), target.getName().getString(), expiresAt,
                transferable, reason, staff.getName().getString());
        if (!target.getInventory().add(granted)) target.drop(granted, false);
        LoreStore current = store(staff.getServer());
        current.history(target.getUUID(), target.getName().getString(), "RELIQUIA_CONCEDIDA",
                granted.getHoverName().getString() + " | " + (transferable ? "transferível" : "vinculada")
                        + " | selo: " + LoreItemData.read(granted).orElseThrow().itemId()
                        + " | staff: " + staff.getName().getString());
        current.save();
        target.sendSystemMessage(Component.literal("✦ UMA RELÍQUIA LHE FOI CONCEDIDA ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\n" + granted.getHoverName().getString()
                        + " permanecerá em suas mãos enquanto durar a vontade que a concedeu.")
                        .withStyle(ChatFormatting.YELLOW)));
        return true;
    }

    boolean makeHeldTemporary(ServerPlayer staff, long durationMillis, boolean transferable, String reason) {
        ItemStack held = staff.getMainHandItem();
        if (held.isEmpty() || LoreItemData.hasTag(held) || VipItemData.hasVipTag(held)) return false;
        LoreItemData.attach(held, staff.getUUID(), staff.getName().getString(),
                System.currentTimeMillis() + durationMillis, transferable, reason, staff.getName().getString());
        LoreStore current = store(staff.getServer());
        current.history(staff.getUUID(), staff.getName().getString(), "RELIQUIA_CRIADA",
                held.getHoverName().getString() + " | aplicada à mão");
        current.save();
        return true;
    }

    void queue(Container container) {
        loadedContainers.add(container);
        if (queued.add(container)) containers.addLast(container);
    }

    void tick(MinecraftServer server) {
        processContainers(server);
        if (++ticks % 20 != 0) return;
        LoreStore current = store(server);
        LoreConfig config = LoreConfig.load(server);
        Set<UUID> seen = new HashSet<>();
        boolean refreshLore = ticks % (config.refreshSeconds * 20) == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            scanPlayer(current, player, seen, config, refreshLore);
        if (ticks % 1200 == 0) {
            scanDropped(server, current);
            current.purge();
            current.save();
        }
    }

    private void scanPlayer(LoreStore current, ServerPlayer player, Set<UUID> seen,
                            LoreConfig config, boolean refreshLore) {
        for (ItemStack stack : allStacks(player)) {
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null || stack.isEmpty()) continue;
            if (refreshLore) LoreItemData.refreshLore(stack);
            if (current.data.retiredIds.contains(info.itemId().toString())) {
                String name = stack.getHoverName().getString();
                stack.setCount(0);
                notifyRemoval(player, name, "Uma cópia que já havia sido recolhida tentou retornar.");
                continue;
            }
            if (!seen.add(info.itemId())) {
                UUID newId = LoreItemData.reidentify(stack);
                if (newId != null) seen.add(newId);
                info = LoreItemData.read(stack).orElse(info);
            }
            if (!info.transferable() && !info.owner().equals(player.getUUID())) {
                ItemStack archived = stack.copy();
                current.archive(archived, player.registryAccess(), "vínculo rompido", config.vaultDays);
                stack.setCount(0);
                current.history(player.getUUID(), player.getName().getString(), "RELIQUIA_RECUSOU_PORTADOR",
                        archived.getHoverName().getString() + " | pertence a: " + info.ownerName());
                notifyRemoval(player, archived.getHoverName().getString(),
                        "A relíquia recusou mãos às quais jamais foi confiada.");
                current.save();
                continue;
            }
            UUID previous = lastHolder.put(info.itemId(), player.getUUID());
            if (previous != null && !previous.equals(player.getUUID())) {
                current.history(info.owner(), info.ownerName(), "RELIQUIA_TRANSFERIDA",
                        stack.getHoverName().getString() + " | novo portador: " + player.getName().getString());
                current.save();
            }
            long remaining = info.expiresAt() - System.currentTimeMillis();
            for (long warning : config.warningMillis)
                warn(current, player, info, stack.getHoverName().getString(), remaining, warning);
            if (info.expiresAt() <= System.currentTimeMillis()) {
                ItemStack archived = stack.copy();
                current.archive(archived, player.registryAccess(), "tempo encerrado", config.vaultDays);
                stack.setCount(0);
                notifyRemoval(player, archived.getHoverName().getString(),
                        "Seu tempo com esta relíquia terminou. A vontade que a concedeu agora a reclama.");
                current.save();
            }
        }
    }

    private void warn(LoreStore current, ServerPlayer player, LoreItemData.Info info, String item,
                      long remaining, long threshold) {
        if (remaining <= 0 || remaining > threshold) return;
        String key = info.itemId() + ":" + threshold;
        if (!current.data.sentWarnings.add(key)) return;
        player.sendSystemMessage(Component.literal("✦ O VÍNCULO DE UMA RELÍQUIA ENFRAQUECE ✦")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .append(Component.literal("\n" + item + " será reclamado em menos de " + durationLabel(threshold) + ".")
                        .withStyle(ChatFormatting.GRAY)));
        current.save();
    }

    private String durationLabel(long millis) {
        if (millis % 86_400_000L == 0) return millis / 86_400_000L + " dia(s)";
        if (millis % 3_600_000L == 0) return millis / 3_600_000L + " hora(s)";
        return Math.max(1, millis / 60_000L) + " minuto(s)";
    }

    private void notifyRemoval(ServerPlayer player, String item, String message) {
        player.sendSystemMessage(Component.literal("✦ A CONCESSÃO FOI REVOGADA ✦")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("\n" + item + "\n" + message).withStyle(ChatFormatting.GRAY)));
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1,
                player.getZ(), 35, 0.5, 0.8, 0.5, 0.15);
        player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.8F, 0.7F);
    }

    private void scanDropped(MinecraftServer server, LoreStore current) {
        long now = System.currentTimeMillis();
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof ItemEntity dropped)) continue;
            ItemStack stack = dropped.getItem();
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null) continue;
            if (current.data.retiredIds.contains(info.itemId().toString())) { dropped.discard(); continue; }
            if (info.expiresAt() <= now) {
                current.archive(stack.copy(), level.registryAccess(), "tempo encerrado no chão",
                        LoreConfig.load(server).vaultDays);
                dropped.discard();
                current.save();
            }
        }
    }

    private void processContainers(MinecraftServer server) {
        for (int processed = 0; processed < 8 && !containers.isEmpty(); processed++) {
            Container container = containers.removeFirst();
            queued.remove(container);
            long now = System.currentTimeMillis();
            LoreStore current = store(server);
            boolean changed = false;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
                if (info == null) continue;
                if (current.data.retiredIds.contains(info.itemId().toString())) { stack.setCount(0); changed = true; }
                else if (info.expiresAt() <= now) {
                    current.archive(stack.copy(), server.registryAccess(), "tempo encerrado em recipiente",
                            LoreConfig.load(server).vaultDays);
                    stack.setCount(0); changed = true;
                }
            }
            if (changed) { container.setChanged(); current.save(); }
        }
    }

    boolean revoke(ServerPlayer staff, ServerPlayer target, String idPrefix) {
        LoreStore current = store(staff.getServer());
        for (ServerPlayer holder : staff.getServer().getPlayerList().getPlayers()) for (ItemStack stack : allStacks(holder)) {
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null || !info.owner().equals(target.getUUID())
                    || !info.itemId().toString().startsWith(idPrefix.toLowerCase())) continue;
            ItemStack archived = stack.copy();
            current.archive(archived, holder.registryAccess(), "revogação administrativa",
                    LoreConfig.load(staff.getServer()).vaultDays);
            stack.setCount(0);
            notifyRemoval(holder, archived.getHoverName().getString(),
                    "A concessão foi encerrada antes de seu tempo.");
            current.save();
            return true;
        }
        for (Container container : loadedContainers) for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null || !info.owner().equals(target.getUUID())
                    || !info.itemId().toString().startsWith(idPrefix.toLowerCase())) continue;
            current.archive(stack.copy(), staff.registryAccess(), "revogação administrativa em recipiente",
                    LoreConfig.load(staff.getServer()).vaultDays);
            stack.setCount(0); container.setChanged(); current.save(); return true;
        }
        for (var level : staff.getServer().getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof ItemEntity dropped)) continue;
            LoreItemData.Info info = LoreItemData.read(dropped.getItem()).orElse(null);
            if (info == null || !info.owner().equals(target.getUUID())
                    || !info.itemId().toString().startsWith(idPrefix.toLowerCase())) continue;
            current.archive(dropped.getItem().copy(), level.registryAccess(), "revogação administrativa no chão",
                    LoreConfig.load(staff.getServer()).vaultDays);
            dropped.discard(); current.save(); return true;
        }
        return false;
    }

    List<ActiveRelic> activeRelics(ServerPlayer target) {
        List<ActiveRelic> result = new ArrayList<>();
        for (ServerPlayer holder : target.getServer().getPlayerList().getPlayers()) for (ItemStack stack : allStacks(holder))
            LoreItemData.read(stack).filter(info -> info.owner().equals(target.getUUID())).ifPresent(info -> result.add(
                    new ActiveRelic(info.itemId(), stack.getHoverName().getString(), info.expiresAt(),
                            info.transferable(), holder.getName().getString(), stack.copy())));
        for (Container container : loadedContainers) for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            LoreItemData.read(stack).filter(info -> info.owner().equals(target.getUUID())).ifPresent(info -> result.add(
                    new ActiveRelic(info.itemId(), stack.getHoverName().getString(), info.expiresAt(),
                            info.transferable(), "recipiente carregado", stack.copy())));
        }
        for (var level : target.getServer().getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof ItemEntity dropped)) continue;
            ItemStack stack = dropped.getItem();
            LoreItemData.read(stack).filter(info -> info.owner().equals(target.getUUID())).ifPresent(info -> result.add(
                    new ActiveRelic(info.itemId(), stack.getHoverName().getString(), info.expiresAt(),
                            info.transferable(), "no chão", stack.copy())));
        }
        return result;
    }

    List<String> activeSeals(ServerPlayer target) {
        return activeRelics(target).stream().map(relic -> relic.id().toString().substring(0, 8)).toList();
    }

    Diagnosis diagnose(ServerPlayer target) {
        LoreStore current = store(target.getServer());
        Set<UUID> ids = new HashSet<>();
        int active = 0, duplicates = 0, malformed = 0, retired = 0, transferred = 0;
        long soonest = Long.MAX_VALUE;
        for (ItemStack stack : allStacks(target)) {
            if (!LoreItemData.hasTag(stack)) continue;
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null) { malformed++; continue; }
            active++;
            if (!ids.add(info.itemId())) duplicates++;
            if (current.data.retiredIds.contains(info.itemId().toString())) retired++;
            if (!info.owner().equals(target.getUUID())) transferred++;
            soonest = Math.min(soonest, info.expiresAt());
        }
        int vault = current.data.vault.getOrDefault(target.getUUID().toString(), List.of()).size();
        int knownActive = activeRelics(target).size();
        return new Diagnosis(active, transferred, vault, duplicates, malformed, retired,
                soonest == Long.MAX_VALUE ? 0 : soonest, knownActive);
    }

    Repair repair(ServerPlayer staff, ServerPlayer target) {
        LoreStore current = store(target.getServer());
        Set<UUID> ids = new HashSet<>();
        int reidentified = 0, archived = 0, removedRollback = 0;
        LoreConfig config = LoreConfig.load(target.getServer());
        for (ItemStack stack : allStacks(target)) {
            if (!LoreItemData.hasTag(stack)) continue;
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null) {
                current.archiveMalformed(target.getUUID(), target.getName().getString(), stack.copy(),
                        target.registryAccess(), "metadados incompletos", config.vaultDays);
                stack.setCount(0); archived++; continue;
            }
            if (current.data.retiredIds.contains(info.itemId().toString())) {
                stack.setCount(0); removedRollback++; continue;
            }
            if (!ids.add(info.itemId())) { LoreItemData.reidentify(stack); reidentified++; }
            LoreItemData.refreshLore(stack);
        }
        current.history(target.getUUID(), target.getName().getString(), "RELIQUIAS_REPARADAS",
                "selos corrigidos: " + reidentified + " | arquivadas: " + archived
                        + " | rollback removido: " + removedRollback + " | staff: " + staff.getName().getString());
        current.save();
        return new Repair(reidentified, archived, removedRollback);
    }

    void openRevokeMenu(ServerPlayer staff, ServerPlayer target) {
        staff.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new LoreRevokeMenu(id, inventory, new SimpleContainer(54), this, target),
                Component.literal("Revogar relíquia — " + target.getName().getString())));
    }

    void openVault(ServerPlayer staff, ServerPlayer target) {
        List<LoreStore.VaultEntry> entries = store(staff.getServer()).data.vault
                .getOrDefault(target.getUUID().toString(), List.of());
        SimpleContainer inventory = new SimpleContainer(54);
        for (int i = 0; i < Math.min(54, entries.size()); i++)
            inventory.setItem(i, VipStore.decode(entries.get(i).encodedStack(), staff.registryAccess()));
        staff.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new VaultViewMenu(id, inv, inventory),
                Component.literal("Cofre de relíquias — " + target.getName().getString())));
    }

    Result restore(ServerPlayer staff, ServerPlayer target, int slot, long duration, boolean permanent) {
        LoreStore current = store(staff.getServer());
        List<LoreStore.VaultEntry> entries = current.data.vault.get(target.getUUID().toString());
        if (entries == null || slot < 1 || slot > entries.size()) return Result.INVALID_SLOT;
        LoreStore.VaultEntry entry = entries.get(slot - 1);
        if (entry.deleteAt() <= System.currentTimeMillis()) return Result.EXPIRED;
        ItemStack stack = VipStore.decode(entry.encodedStack(), target.registryAccess());
        if (stack.isEmpty()) return Result.INVALID_ITEM;
        if (!permanent && LoreItemData.read(stack).isEmpty()) return Result.INVALID_ITEM;
        if (permanent) LoreItemData.makePermanent(stack);
        else LoreItemData.restore(stack, System.currentTimeMillis() + duration);
        entries.remove(slot - 1);
        if (!target.getInventory().add(stack)) target.drop(stack, false);
        current.history(target.getUUID(), target.getName().getString(), permanent
                ? "RELIQUIA_TORNADA_PERMANENTE" : "RELIQUIA_RESTAURADA", stack.getHoverName().getString());
        current.save();
        return Result.SUCCESS;
    }

    boolean deleteVault(ServerPlayer staff, ServerPlayer target, int slot) {
        LoreStore current = store(staff.getServer());
        List<LoreStore.VaultEntry> entries = current.data.vault.get(target.getUUID().toString());
        if (entries == null || slot < 1 || slot > entries.size()) return false;
        entries.remove(slot - 1); current.save(); return true;
    }

    private List<ItemStack> allStacks(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        player.getInventory().items.forEach(stacks::add);
        player.getInventory().armor.forEach(stacks::add);
        player.getInventory().offhand.forEach(stacks::add);
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++)
            stacks.add(player.getEnderChestInventory().getItem(i));
        return stacks;
    }

    enum Result { SUCCESS, INVALID_SLOT, EXPIRED, INVALID_ITEM }
    record ActiveRelic(UUID id, String name, long expiresAt, boolean transferable,
                       String holder, ItemStack display) {}
    record Diagnosis(int active, int transferred, int vault, int duplicates,
                     int malformed, int retiredCopies, long soonestExpiry, int knownActive) {}
    record Repair(int reidentified, int archived, int removedRollback) {}
}
