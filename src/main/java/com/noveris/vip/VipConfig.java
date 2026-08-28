package com.noveris.vip;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class VipConfig {
    private static final List<String> DEFAULTS = List.of(
            "# Noveris VIP System - níveis de permissão (0 a 4)",
            "permission_kit_manage = 2",
            "permission_grant = 2",
            "permission_renew = 2",
            "permission_history = 2",
            "permission_vault = 2",
            "warning_days = \"7,3,1\""
    );
    final int kitManage, grant, renew, history, vault;
    final int[] warningDays;

    private VipConfig(Map<String, String> values) {
        kitManage = number(values, "permission_kit_manage", 2, 0, 4);
        grant = number(values, "permission_grant", 2, 0, 4);
        renew = number(values, "permission_renew", 2, 0, 4);
        history = number(values, "permission_history", 2, 0, 4);
        vault = number(values, "permission_vault", 2, 0, 4);
        warningDays = java.util.Arrays.stream(values.getOrDefault("warning_days", "7,3,1").split(","))
                .map(String::trim).mapToInt(value -> {
                    try { return Math.max(1, Integer.parseInt(value)); }
                    catch (NumberFormatException ignored) { return 1; }
                }).distinct().sorted().toArray();
    }

    static VipConfig load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig")
                .resolve("noveris_vip_system-server.toml");
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) Files.write(file, DEFAULTS);
            Map<String, String> values = new HashMap<>();
            for (String raw : Files.readAllLines(file)) {
                String line = raw.split("#", 2)[0].trim();
                int equals = line.indexOf('=');
                if (equals < 1) continue;
                String value = line.substring(equals + 1).trim().replace("\"", "");
                values.put(line.substring(0, equals).trim(), value);
            }
            return new VipConfig(values);
        } catch (IOException exception) {
            NoverisVipSystem.LOGGER.error("Falha ao carregar configuração VIP", exception);
            return new VipConfig(Map.of());
        }
    }

    private static int number(Map<String, String> values, String key, int fallback, int min, int max) {
        try { return Math.clamp(Integer.parseInt(values.getOrDefault(key, String.valueOf(fallback))), min, max); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
