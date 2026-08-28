package com.noveris.vip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

final class VipStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long VAULT_RETENTION_MS = 7L * 24 * 60 * 60 * 1000;
    private final Path file;
    final Data data;

    private VipStore(Path file, Data data) { this.file = file; this.data = data; }

    static VipStore load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("noveris_vip_system.json");
        try {
            if (Files.exists(file)) {
                Data loaded = GSON.fromJson(Files.readString(file), Data.class);
                if (loaded != null) return new VipStore(file, loaded.normalize());
            }
        } catch (Exception exception) {
            NoverisVipSystem.LOGGER.error("Falha ao carregar os dados VIP", exception);
        }
        return new VipStore(file, new Data().normalize());
    }

    synchronized void save() {
        try { Files.writeString(file, GSON.toJson(data)); }
        catch (IOException exception) { NoverisVipSystem.LOGGER.error("Falha ao salvar os dados VIP", exception); }
    }

    void addHistory(UUID playerId, String playerName, String action, String detail) {
        data.history.computeIfAbsent(playerId.toString(), ignored -> new ArrayList<>())
                .add(new HistoryEntry(System.currentTimeMillis(), playerName, action, detail));
        List<HistoryEntry> entries = data.history.get(playerId.toString());
        if (entries.size() > 250) entries.subList(0, entries.size() - 250).clear();
    }

    void archive(UUID holder, String holderName, ItemStack stack, HolderLookup.Provider registries) {
        VipItemData.Info info = VipItemData.read(stack).orElseThrow();
        data.vault.computeIfAbsent(holder.toString(), ignored -> new ArrayList<>()).add(
                new VaultEntry(encode(stack, registries), System.currentTimeMillis(),
                        System.currentTimeMillis() + VAULT_RETENTION_MS, info.originalOwnerName(), info.kit()));
        addHistory(holder, holderName, "ITEM_EXPIRADO", stack.getHoverName().getString() + " | kit: " + info.kit());
        if (!holder.equals(info.originalOwner())) addHistory(info.originalOwner(), info.originalOwnerName(),
                "ITEM_EXPIRADO_COM_TERCEIRO", stack.getHoverName().getString() + " | portador: " + holderName);
    }

    void purgeVault() {
        long now = System.currentTimeMillis();
        data.vault.values().forEach(entries -> entries.removeIf(entry -> entry.deleteAt <= now));
        data.vault.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    static String encode(ItemStack stack, HolderLookup.Provider registries) {
        try {
            CompoundTag tag = (CompoundTag) stack.save(registries);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    static ItemStack decode(String encoded, HolderLookup.Provider registries) {
        try {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(
                    Base64.getDecoder().decode(encoded)), NbtAccounter.unlimitedHeap());
            return ItemStack.parseOptional(registries, tag);
        } catch (IOException exception) { return ItemStack.EMPTY; }
    }

    static final class Data {
        Map<String, Kit> kits;
        Map<String, Profile> profiles;
        Map<String, List<VaultEntry>> vault;
        Map<String, List<HistoryEntry>> history;
        Map<String, PlanDefinition> plans;
        Map<String, PendingDelivery> pendingDeliveries;
        Map<String, List<Integer>> sentWarnings;
        Data normalize() {
            if (kits == null) kits = new HashMap<>();
            if (profiles == null) profiles = new HashMap<>();
            if (vault == null) vault = new HashMap<>();
            if (history == null) history = new HashMap<>();
            if (plans == null) plans = new LinkedHashMap<>();
            if (plans.isEmpty()) {
                plans.put("viajante", new PlanDefinition("viajante", "Viajante", true, 1));
                plans.put("nobre", new PlanDefinition("nobre", "Nobre", true, 2));
                plans.put("regente", new PlanDefinition("regente", "Regente", true, 3));
                plans.put("soberano", new PlanDefinition("soberano", "Soberano", true, 4));
            }
            if (pendingDeliveries == null) pendingDeliveries = new HashMap<>();
            if (sentWarnings == null) sentWarnings = new HashMap<>();
            return this;
        }
    }
    static final class Kit {
        String name;
        String plan;
        List<KitItem> items = new ArrayList<>();
        Kit() {}
        Kit(String name, String plan) { this.name = name; this.plan = plan; }
    }
    record KitItem(String encodedStack, boolean temporary) {}
    record Profile(String playerName, String plan, String kit, long grantedAt, long expiresAt) {}
    record VaultEntry(String encodedStack, long archivedAt, long deleteAt,
                      String originalOwner, String kit) {}
    record HistoryEntry(long timestamp, String playerName, String action, String detail) {}
    record PlanDefinition(String id, String displayName, boolean enabled, int order) {}
    record PendingDelivery(String playerName, String kit, int days, String staffName, long queuedAt) {}
}
