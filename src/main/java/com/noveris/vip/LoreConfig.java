package com.noveris.vip;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LoreConfig {
    private static final List<String> DEFAULTS = List.of(
            "# Noveris Lore - configuração independente",
            "permission_create = 2",
            "permission_view = 2",
            "permission_maintenance = 4",
            "vault_retention_days = 7",
            "warning_times = \"1d,1h,10m\"",
            "lore_refresh_seconds = 60",
            "performance_warn_ms = 25",
            "message_grant_title = \"✦ UMA RELÍQUIA LHE FOI CONCEDIDA ✦\"",
            "message_grant_body = \"{item} permanecerá em suas mãos enquanto durar a vontade que a concedeu.\"",
            "message_warning_title = \"✦ O VÍNCULO DE UMA RELÍQUIA ENFRAQUECE ✦\"",
            "message_warning_body = \"{item} será reclamado em menos de {tempo}.\"",
            "message_revoke_title = \"✦ A CONCESSÃO FOI REVOGADA ✦\"",
            "message_expired_body = \"Seu tempo com esta relíquia terminou. A vontade que a concedeu agora a reclama.\""
    );
    final int createPermission, viewPermission, maintenancePermission, vaultDays, refreshSeconds, performanceWarnMs;
    final long[] warningMillis;
    final String grantTitle, grantBody, warningTitle, warningBody, revokeTitle, expiredBody;

    private LoreConfig(Map<String, String> values) {
        createPermission = number(values, "permission_create", 2, 0, 4);
        viewPermission = number(values, "permission_view", 2, 0, 4);
        maintenancePermission = number(values, "permission_maintenance", 4, 0, 4);
        vaultDays = number(values, "vault_retention_days", 7, 1, 365);
        refreshSeconds = number(values, "lore_refresh_seconds", 60, 10, 3600);
        performanceWarnMs = number(values, "performance_warn_ms", 25, 1, 1000);
        warningMillis = java.util.Arrays.stream(values.getOrDefault("warning_times", "1d,1h,10m").split(","))
                .map(String::trim).mapToLong(LoreConfig::duration).filter(value -> value > 0).distinct().sorted().toArray();
        grantTitle = text(values, "message_grant_title", "✦ UMA RELÍQUIA LHE FOI CONCEDIDA ✦");
        grantBody = text(values, "message_grant_body", "{item} permanecerá em suas mãos enquanto durar a vontade que a concedeu.");
        warningTitle = text(values, "message_warning_title", "✦ O VÍNCULO DE UMA RELÍQUIA ENFRAQUECE ✦");
        warningBody = text(values, "message_warning_body", "{item} será reclamado em menos de {tempo}.");
        revokeTitle = text(values, "message_revoke_title", "✦ A CONCESSÃO FOI REVOGADA ✦");
        expiredBody = text(values, "message_expired_body", "Seu tempo com esta relíquia terminou. A vontade que a concedeu agora a reclama.");
    }

    private static String text(Map<String, String> values, String key, String fallback) {
        return values.getOrDefault(key, fallback).replace("\\n", "\n");
    }

    static LoreConfig load(MinecraftServer server) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("noveris_lore-server.toml");
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) Files.write(file, DEFAULTS);
            Map<String, String> values = new HashMap<>();
            for (String raw : Files.readAllLines(file)) {
                String line = raw.split("#", 2)[0].trim();
                int equals = line.indexOf('=');
                if (equals > 0) values.put(line.substring(0, equals).trim(),
                        line.substring(equals + 1).trim().replace("\"", ""));
            }
            return new LoreConfig(values);
        } catch (IOException exception) {
            NoverisVipSystem.LOGGER.error("Falha ao carregar configuração de lore", exception);
            return new LoreConfig(Map.of());
        }
    }

    static long duration(String raw) {
        raw = raw.toLowerCase();
        if (!raw.matches("[1-9][0-9]*(s|m|h|d)")) return -1;
        try {
            long value = Long.parseLong(raw.substring(0, raw.length() - 1));
            long unit = switch (raw.charAt(raw.length() - 1)) {
                case 's' -> 1_000L; case 'm' -> 60_000L; case 'h' -> 3_600_000L; default -> 86_400_000L;
            };
            return Math.multiplyExact(value, unit);
        } catch (ArithmeticException | NumberFormatException exception) { return -1; }
    }

    private static int number(Map<String, String> values, String key, int fallback, int min, int max) {
        try { return Math.clamp(Integer.parseInt(values.getOrDefault(key, String.valueOf(fallback))), min, max); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
