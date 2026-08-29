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
    private final Set<String> warnings = new HashSet<>();
    private final ArrayDeque<Container> containers = new ArrayDeque<>();
    private final Set<Container> queued = Collections.newSetFromMap(new IdentityHashMap<>());

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

    void queue(Container container) { if (queued.add(container)) containers.addLast(container); }

    void tick(MinecraftServer server) {
        processContainers(server);
        if (++ticks % 20 != 0) return;
        LoreStore current = store(server);
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) scanPlayer(current, player, seen);
        if (ticks % 1200 == 0) {
            scanDropped(server, current);
            current.purge();
            current.save();
        }
    }

    private void scanPlayer(LoreStore current, ServerPlayer player, Set<UUID> seen) {
        for (ItemStack stack : allStacks(player)) {
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null || stack.isEmpty()) continue;
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
                current.archive(archived, player.registryAccess(), "vínculo rompido");
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
            if (remaining > 60_000L)
                warn(player, info, stack.getHoverName().getString(), remaining, 600_000L, "10 minutos");
            warn(player, info, stack.getHoverName().getString(), remaining, 60_000L, "1 minuto");
            if (info.expiresAt() <= System.currentTimeMillis()) {
                ItemStack archived = stack.copy();
                current.archive(archived, player.registryAccess(), "tempo encerrado");
                stack.setCount(0);
                notifyRemoval(player, archived.getHoverName().getString(),
                        "Seu tempo com esta relíquia terminou. A vontade que a concedeu agora a reclama.");
                current.save();
            }
        }
    }

    private void warn(ServerPlayer player, LoreItemData.Info info, String item, long remaining,
                      long threshold, String label) {
        if (remaining <= 0 || remaining > threshold) return;
        String key = info.itemId() + ":" + threshold;
        if (!warnings.add(key)) return;
        player.sendSystemMessage(Component.literal("✦ O VÍNCULO DE UMA RELÍQUIA ENFRAQUECE ✦")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .append(Component.literal("\n" + item + " será reclamado em menos de " + label + ".")
                        .withStyle(ChatFormatting.GRAY)));
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
                current.archive(stack.copy(), level.registryAccess(), "tempo encerrado no chão");
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
                    current.archive(stack.copy(), server.registryAccess(), "tempo encerrado em recipiente");
                    stack.setCount(0); changed = true;
                }
            }
            if (changed) { container.setChanged(); current.save(); }
        }
    }

    boolean revoke(ServerPlayer staff, ServerPlayer target, String idPrefix) {
        LoreStore current = store(staff.getServer());
        for (ItemStack stack : allStacks(target)) {
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null || !info.itemId().toString().startsWith(idPrefix.toLowerCase())) continue;
            ItemStack archived = stack.copy();
            current.archive(archived, target.registryAccess(), "revogação administrativa");
            stack.setCount(0);
            notifyRemoval(target, archived.getHoverName().getString(),
                    "A concessão foi encerrada antes de seu tempo.");
            current.save();
            return true;
        }
        return false;
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
}
