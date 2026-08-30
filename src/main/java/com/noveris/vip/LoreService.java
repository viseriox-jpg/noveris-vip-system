package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

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
    private LoreConfig cachedConfig;
    private MinecraftServer configServer;
    private long configLoadedAt;
    private int ticks;
    private long lastScanNanos, maxScanNanos, slowScans, lastPerformanceWarning;
    private final Map<UUID, UUID> lastHolder = new HashMap<>();
    private final ArrayDeque<Container> containers = new ArrayDeque<>();
    private final Set<Container> queued = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Container> loadedContainers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<BlockEntity> blockEntities = new ArrayDeque<>();
    private final Set<BlockEntity> loadedBlockEntities = Collections.newSetFromMap(new IdentityHashMap<>());

    private LoreStore store(MinecraftServer current) {
        if (store == null || server != current) { server = current; store = LoreStore.load(current); }
        return store;
    }

    LoreStore data(MinecraftServer server) { return store(server); }

    private LoreConfig config(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (cachedConfig == null || configServer != server || now - configLoadedAt >= 5_000L) {
            cachedConfig = LoreConfig.load(server);
            configServer = server;
            configLoadedAt = now;
        }
        return cachedConfig;
    }

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
        LoreConfig config = config(staff.getServer());
        target.sendSystemMessage(Component.literal(config.grantTitle)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\n" + config.grantBody.replace("{item}", granted.getHoverName().getString()))
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

    void queue(BlockEntity blockEntity) {
        if (blockEntity instanceof Container container) { queue(container); return; }
        if (loadedBlockEntities.add(blockEntity)) blockEntities.addLast(blockEntity);
    }

    void unload(Container container) {
        loadedContainers.remove(container);
        queued.remove(container);
        containers.remove(container);
    }

    void unload(BlockEntity blockEntity) {
        if (blockEntity instanceof Container container) { unload(container); return; }
        loadedBlockEntities.remove(blockEntity);
        blockEntities.remove(blockEntity);
    }

    void tick(MinecraftServer server) {
        long started = System.nanoTime();
        processContainers(server);
        if (++ticks % 20 != 0) { recordPerformance(server, started, false); return; }
        LoreStore current = store(server);
        LoreConfig config = config(server);
        Set<UUID> seen = new HashSet<>();
        boolean refreshLore = ticks % (config.refreshSeconds * 20) == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            scanPlayer(current, player, seen, config, refreshLore);
        if (ticks % (config.entityScanSeconds * 20) == 0) scanWorldEntities(server, current);
        if (ticks % 1200 == 0) {
            current.purge();
            current.save();
        }
        recordPerformance(server, started, true);
    }

    private void recordPerformance(MinecraftServer server, long started, boolean evaluateWarning) {
        lastScanNanos = System.nanoTime() - started;
        maxScanNanos = Math.max(maxScanNanos, lastScanNanos);
        if (!evaluateWarning) return;
        LoreConfig config = config(server);
        if (lastScanNanos >= config.performanceWarnMs * 1_000_000L) {
            slowScans++;
            long now = System.currentTimeMillis();
            if (now - lastPerformanceWarning >= 60_000L) {
                lastPerformanceWarning = now;
                NoverisVipSystem.LOGGER.warn("Varredura Noveris Lore levou {} ms (limite: {} ms)",
                        lastScanNanos / 1_000_000.0, config.performanceWarnMs);
            }
        }
    }

    Performance performance() {
        return new Performance(lastScanNanos, maxScanNanos, slowScans, loadedContainers.size(), containers.size(),
                loadedBlockEntities.size(), blockEntities.size());
    }

    private void scanPlayer(LoreStore current, ServerPlayer player, Set<UUID> seen,
                            LoreConfig config, boolean refreshLore) {
        for (ItemStack stack : allStacks(player)) {
            scanNestedStack(player.getServer(), stack, "mochila de " + player.getName().getString(), 0);
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
                        config.expiredBody);
                current.save();
            }
        }
    }

    private void warn(LoreStore current, ServerPlayer player, LoreItemData.Info info, String item,
                      long remaining, long threshold) {
        if (remaining <= 0 || remaining > threshold) return;
        String key = info.itemId() + ":" + threshold;
        if (!current.data.sentWarnings.add(key)) return;
        LoreConfig config = config(player.getServer());
        player.sendSystemMessage(Component.literal(config.warningTitle)
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .append(Component.literal("\n" + config.warningBody.replace("{item}", item)
                                .replace("{tempo}", durationLabel(threshold)))
                        .withStyle(ChatFormatting.GRAY)));
        current.save();
    }

    private String durationLabel(long millis) {
        if (millis % 86_400_000L == 0) return millis / 86_400_000L + " dia(s)";
        if (millis % 3_600_000L == 0) return millis / 3_600_000L + " hora(s)";
        return Math.max(1, millis / 60_000L) + " minuto(s)";
    }

    private void notifyRemoval(ServerPlayer player, String item, String message) {
        LoreConfig config = config(player.getServer());
        player.sendSystemMessage(Component.literal(config.revokeTitle)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("\n" + item + "\n" + message).withStyle(ChatFormatting.GRAY)));
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1,
                player.getZ(), 35, 0.5, 0.8, 0.5, 0.15);
        player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.8F, 0.7F);
    }

    private void scanWorldEntities(MinecraftServer server, LoreStore current) {
        long now = System.currentTimeMillis();
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
            ItemStack stack;
            String location;
            if (entity instanceof ItemEntity dropped) {
                stack = dropped.getItem(); location = "no chão";
            } else if (entity instanceof ItemFrame frame) {
                stack = frame.getItem(); location = "em moldura";
            } else continue;
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null) continue;
            if (current.data.retiredIds.contains(info.itemId().toString())) {
                if (entity instanceof ItemEntity dropped) dropped.discard();
                else ((ItemFrame) entity).setItem(ItemStack.EMPTY);
                continue;
            }
            if (info.expiresAt() <= now) {
                current.archive(stack.copy(), level.registryAccess(), "tempo encerrado " + location,
                        config(server).vaultDays);
                current.history(info.owner(), info.ownerName(), "RELIQUIA_RECOLHIDA",
                        stack.getHoverName().getString() + " | " + location + " | " + entity.blockPosition());
                if (entity instanceof ItemEntity dropped) dropped.discard();
                else ((ItemFrame) entity).setItem(ItemStack.EMPTY);
                current.save();
            }
        }
    }

    private void processContainers(MinecraftServer server) {
        LoreConfig config = config(server);
        for (int processed = 0; processed < config.containersPerTick && !containers.isEmpty(); processed++) {
            Container container = containers.removeFirst();
            if (container instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity
                    && blockEntity.isRemoved()) { loadedContainers.remove(container); queued.remove(container); continue; }
            long now = System.currentTimeMillis();
            LoreStore current = store(server);
            boolean changed = false;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                scanNestedStack(server, stack, containerLabel(container), 0);
                LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
                if (info == null) continue;
                if (current.data.retiredIds.contains(info.itemId().toString())) { stack.setCount(0); changed = true; }
                else if (info.expiresAt() <= now) {
                    archiveFromStorage(server, current, stack, info, containerLabel(container));
                    stack.setCount(0); changed = true;
                }
            }
            if (changed) { container.setChanged(); current.save(); }
            if (loadedContainers.contains(container)) containers.addLast(container);
        }
        for (int processed = 0; processed < config.modHandlersPerTick && !blockEntities.isEmpty(); processed++) {
            BlockEntity blockEntity = blockEntities.removeFirst();
            if (blockEntity.isRemoved() || blockEntity.getLevel() == null) {
                loadedBlockEntities.remove(blockEntity); continue;
            }
            IItemHandler handler = null;
            for (Direction side : Direction.values()) {
                handler = blockEntity.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                        blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
                if (handler != null) break;
            }
            if (handler == null) handler = blockEntity.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, null);
            if (handler != null) scanHandler(server, handler, blockEntityLabel(blockEntity));
            if (loadedBlockEntities.contains(blockEntity)) blockEntities.addLast(blockEntity);
        }
    }

    private void scanHandler(MinecraftServer server, IItemHandler handler, String location) {
        scanHandler(server, handler, location, 0);
    }

    private void scanHandler(MinecraftServer server, IItemHandler handler, String location, int depth) {
        LoreStore current = store(server);
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            scanNestedStack(server, stack, location, depth);
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null) continue;
            if (!current.data.retiredIds.contains(info.itemId().toString()) && info.expiresAt() > now) continue;
            ItemStack removed = handler.extractItem(slot, stack.getCount(), false);
            if (removed.isEmpty()) continue;
            if (info.expiresAt() <= now) archiveFromStorage(server, current, removed, info, location);
            changed = true;
        }
        if (changed) current.save();
    }

    private void scanNestedStack(MinecraftServer server, ItemStack stack, String parent, int depth) {
        if (stack.isEmpty() || depth >= config(server).nestedInventoryDepth) return;
        IItemHandler nested = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (nested == null || nested.getSlots() == 0) return;
        String type = stack.getItem().getClass().getName();
        String location = type.startsWith("net.p3pp3rf1y.sophisticatedbackpacks")
                ? "Sophisticated Backpack em " + parent : "inventário interno em " + parent;
        scanHandler(server, nested, location, depth + 1);
    }

    private void archiveFromStorage(MinecraftServer server, LoreStore current, ItemStack stack,
                                    LoreItemData.Info info, String location) {
        current.archive(stack.copy(), server.registryAccess(), "tempo encerrado em " + location,
                config(server).vaultDays);
        current.history(info.owner(), info.ownerName(), "RELIQUIA_RECOLHIDA",
                stack.getHoverName().getString() + " | " + location);
    }

    private String containerLabel(Container container) {
        if (container instanceof BlockEntity blockEntity)
            return blockEntityLabel(blockEntity);
        return "recipiente " + container.getClass().getSimpleName();
    }

    private String blockEntityLabel(BlockEntity blockEntity) {
        String type = blockEntity.getClass().getName();
        String mod = type.startsWith("net.p3pp3rf1y.sophisticatedbackpacks") ? "Sophisticated Backpack"
                : type.startsWith("net.p3pp3rf1y.sophisticatedstorage") ? "Sophisticated Storage"
                : type.startsWith("com.tom.storagemod") ? "Tom's Storage"
                : blockEntity.getBlockState().getBlock().getName().getString();
        return mod + " em " + blockEntity.getBlockPos();
    }

    boolean revoke(ServerPlayer staff, ServerPlayer target, String idPrefix) {
        return revoke(staff, target.getUUID(), target.getName().getString(), idPrefix);
    }

    boolean revoke(ServerPlayer staff, UUID targetId, String targetName, String idPrefix) {
        LoreStore current = store(staff.getServer());
        for (ServerPlayer holder : staff.getServer().getPlayerList().getPlayers()) for (ItemStack stack : allStacks(holder)) {
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null || !info.owner().equals(targetId)
                    || !info.itemId().toString().startsWith(idPrefix.toLowerCase())) continue;
            ItemStack archived = stack.copy();
            current.archive(archived, holder.registryAccess(), "revogação administrativa",
                    config(staff.getServer()).vaultDays);
            stack.setCount(0);
            notifyRemoval(holder, archived.getHoverName().getString(),
                    "A concessão foi encerrada antes de seu tempo.");
            current.save();
            return true;
        }
        for (Container container : loadedContainers) for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            LoreItemData.Info info = LoreItemData.read(stack).orElse(null);
            if (info == null || !info.owner().equals(targetId)
                    || !info.itemId().toString().startsWith(idPrefix.toLowerCase())) continue;
            current.archive(stack.copy(), staff.registryAccess(), "revogação administrativa em recipiente",
                    config(staff.getServer()).vaultDays);
            stack.setCount(0); container.setChanged(); current.save(); return true;
        }
        for (var level : staff.getServer().getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof ItemEntity dropped)) continue;
            LoreItemData.Info info = LoreItemData.read(dropped.getItem()).orElse(null);
            if (info == null || !info.owner().equals(targetId)
                    || !info.itemId().toString().startsWith(idPrefix.toLowerCase())) continue;
            current.archive(dropped.getItem().copy(), level.registryAccess(), "revogação administrativa no chão",
                    config(staff.getServer()).vaultDays);
            dropped.discard(); current.save(); return true;
        }
        return false;
    }

    List<ActiveRelic> activeRelics(ServerPlayer target) {
        return activeRelics(target.getServer(), target.getUUID());
    }

    List<ActiveRelic> activeRelics(MinecraftServer server, UUID targetId) {
        List<ActiveRelic> result = new ArrayList<>();
        for (ServerPlayer holder : server.getPlayerList().getPlayers()) for (ItemStack stack : allStacks(holder))
            LoreItemData.read(stack).filter(info -> info.owner().equals(targetId)).ifPresent(info -> result.add(
                    new ActiveRelic(info.itemId(), stack.getHoverName().getString(), info.expiresAt(),
                            info.transferable(), holder.getName().getString(), stack.copy())));
        for (Container container : loadedContainers) for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            LoreItemData.read(stack).filter(info -> info.owner().equals(targetId)).ifPresent(info -> result.add(
                    new ActiveRelic(info.itemId(), stack.getHoverName().getString(), info.expiresAt(),
                            info.transferable(), "recipiente carregado", stack.copy())));
        }
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof ItemEntity dropped)) continue;
            ItemStack stack = dropped.getItem();
            LoreItemData.read(stack).filter(info -> info.owner().equals(targetId)).ifPresent(info -> result.add(
                    new ActiveRelic(info.itemId(), stack.getHoverName().getString(), info.expiresAt(),
                            info.transferable(), "no chão", stack.copy())));
        }
        return result;
    }

    List<String> activeSeals(ServerPlayer target) {
        return activeRelics(target).stream().map(relic -> relic.id().toString().substring(0, 8)).toList();
    }

    List<String> activeSeals(MinecraftServer server, UUID targetId) {
        return activeRelics(server, targetId).stream().map(relic -> relic.id().toString().substring(0, 8)).toList();
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

    Diagnosis diagnoseOffline(MinecraftServer server, UUID targetId) {
        LoreStore current = store(server);
        List<ActiveRelic> relics = activeRelics(server, targetId);
        Set<UUID> seen = new HashSet<>();
        int duplicates = 0, retired = 0;
        long soonest = Long.MAX_VALUE;
        for (ActiveRelic relic : relics) {
            if (!seen.add(relic.id())) duplicates++;
            if (current.data.retiredIds.contains(relic.id().toString())) retired++;
            soonest = Math.min(soonest, relic.expiresAt());
        }
        return new Diagnosis(0, 0, current.data.vault.getOrDefault(targetId.toString(), List.of()).size(),
                duplicates, 0, retired, soonest == Long.MAX_VALUE ? 0 : soonest, relics.size());
    }

    Repair repair(ServerPlayer staff, ServerPlayer target) {
        LoreStore current = store(target.getServer());
        Set<UUID> ids = new HashSet<>();
        int reidentified = 0, archived = 0, removedRollback = 0;
        LoreConfig config = config(target.getServer());
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
        openRevokeMenu(staff, target.getUUID(), target.getName().getString());
    }

    void openRevokeMenu(ServerPlayer staff, UUID targetId, String targetName) {
        staff.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new LoreRevokeMenu(id, inventory, new SimpleContainer(54), this, targetId, targetName),
                Component.literal("Revogar relíquia — " + targetName)));
    }

    void openVault(ServerPlayer staff, ServerPlayer target) {
        openVault(staff, target.getUUID(), target.getName().getString());
    }

    void openVault(ServerPlayer staff, UUID targetId, String targetName) {
        List<LoreStore.VaultEntry> entries = store(staff.getServer()).data.vault
                .getOrDefault(targetId.toString(), List.of());
        SimpleContainer inventory = new SimpleContainer(54);
        staff.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new LoreVaultMenu(id, inv, inventory, entries),
                Component.literal("Cofre de relíquias — " + targetName)));
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
        return deleteVault(staff, target.getUUID(), slot);
    }

    boolean deleteVault(ServerPlayer staff, UUID targetId, int slot) {
        LoreStore current = store(staff.getServer());
        List<LoreStore.VaultEntry> entries = current.data.vault.get(targetId.toString());
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
    record Performance(long lastNanos, long maxNanos, long slowScans,
                       int loadedContainers, int queuedContainers, int loadedModHandlers, int queuedModHandlers) {}
}
