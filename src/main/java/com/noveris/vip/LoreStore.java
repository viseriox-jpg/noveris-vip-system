package com.noveris.vip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class LoreStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long RETENTION = 7L * 24 * 60 * 60 * 1000;
    private final Path file;
    final Data data;

    private LoreStore(Path file, Data data) { this.file = file; this.data = data; }

    static LoreStore load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("noveris_lore.json");
        try {
            if (Files.exists(file)) {
                Data loaded = GSON.fromJson(Files.readString(file), Data.class);
                if (loaded != null) return new LoreStore(file, loaded.normalize());
            }
        } catch (Exception exception) { NoverisVipSystem.LOGGER.error("Falha ao carregar dados de lore", exception); }
        return new LoreStore(file, new Data().normalize());
    }

    synchronized void save() {
        try { Files.writeString(file, GSON.toJson(data)); }
        catch (IOException exception) { NoverisVipSystem.LOGGER.error("Falha ao salvar dados de lore", exception); }
    }

    void history(UUID player, String name, String action, String detail) {
        List<Entry> entries = data.history.computeIfAbsent(player.toString(), ignored -> new ArrayList<>());
        entries.add(new Entry(System.currentTimeMillis(), name, action, detail));
        if (entries.size() > 250) entries.subList(0, entries.size() - 250).clear();
    }

    void archive(ItemStack stack, HolderLookup.Provider registries, String cause) {
        LoreItemData.Info info = LoreItemData.read(stack).orElseThrow();
        long now = System.currentTimeMillis();
        data.retiredIds.add(info.itemId().toString());
        data.retired.put(info.itemId().toString(), new Retired(now, stack.getHoverName().getString(), info.ownerName()));
        data.vault.computeIfAbsent(info.owner().toString(), ignored -> new ArrayList<>()).add(
                new VaultEntry(VipStore.encode(stack, registries), now, now + RETENTION,
                        stack.getHoverName().getString(), cause));
        history(info.owner(), info.ownerName(), "RELIQUIA_RECOLHIDA",
                stack.getHoverName().getString() + " | motivo: " + cause + " | id: " + info.itemId());
    }

    void purge() {
        long now = System.currentTimeMillis();
        data.vault.values().forEach(entries -> entries.removeIf(entry -> entry.deleteAt() <= now));
        data.vault.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        long retiredLimit = now - 90L * 24 * 60 * 60 * 1000;
        data.retired.entrySet().removeIf(entry -> entry.getValue().retiredAt() < retiredLimit);
        data.retiredIds.retainAll(data.retired.keySet());
    }

    static final class Data {
        Map<String, List<VaultEntry>> vault;
        Map<String, List<Entry>> history;
        Set<String> retiredIds;
        Map<String, Retired> retired;
        Data normalize() {
            if (vault == null) vault = new HashMap<>();
            if (history == null) history = new HashMap<>();
            if (retiredIds == null) retiredIds = new HashSet<>();
            if (retired == null) retired = new HashMap<>();
            return this;
        }
    }
    record VaultEntry(String encodedStack, long archivedAt, long deleteAt, String itemName, String cause) {}
    record Entry(long timestamp, String playerName, String action, String detail) {}
    record Retired(long retiredAt, String itemName, String ownerName) {}
}
