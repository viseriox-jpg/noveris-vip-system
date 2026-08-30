package com.noveris.vip;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

final class LoreEvents {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private final LoreService service = new LoreService();

    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nlore")
                .executes(this::help)
                .then(Commands.literal("ajuda").executes(this::help))
                .then(Commands.literal("cancelar").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Operação cancelada.")
                            .withStyle(ChatFormatting.GRAY), false);
                    return 1;
                }))
                .then(Commands.literal("temporario")
                        .requires(source -> source.hasPermission(LoreConfig.load(source.getServer()).createPermission))
                        .then(Commands.literal("mao")
                                .then(Commands.argument("duracao", StringArgumentType.word()).suggests(this::suggestDurations)
                                        .executes(ctx -> createHeld(ctx, false, ""))
                                        .then(Commands.argument("modo", StringArgumentType.word()).suggests(this::suggestModes)
                                                .executes(ctx -> createHeld(ctx, mode(ctx), ""))
                                                .then(Commands.argument("motivo", StringArgumentType.greedyString())
                                                        .executes(ctx -> createHeld(ctx, mode(ctx),
                                                                StringArgumentType.getString(ctx, "motivo")))))))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("duracao", StringArgumentType.word()).suggests(this::suggestDurations)
                                        .executes(ctx -> grant(ctx, false, ""))
                                        .then(Commands.argument("modo", StringArgumentType.word()).suggests(this::suggestModes)
                                                .executes(ctx -> grant(ctx, mode(ctx), ""))
                                                .then(Commands.argument("motivo", StringArgumentType.greedyString())
                                                        .executes(ctx -> grant(ctx, mode(ctx),
                                                                StringArgumentType.getString(ctx, "motivo"))))))))
                .then(Commands.literal("revogar").requires(source -> source.hasPermission(
                                LoreConfig.load(source.getServer()).maintenancePermission))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(this::suggestPlayers)
                                .executes(this::openRevoke)
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(this::suggestSeals).executes(this::revoke))))
                .then(Commands.literal("historico")
                        .requires(source -> source.hasPermission(LoreConfig.load(source.getServer()).viewPermission))
                        .then(Commands.literal("apagar").requires(source -> source.hasPermission(
                                        LoreConfig.load(source.getServer()).maintenancePermission))
                                .then(Commands.argument("player", StringArgumentType.word()).suggests(this::suggestPlayers)
                                        .executes(this::requestClearHistory)
                                        .then(Commands.literal("confirmar").executes(this::clearHistory))))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(this::suggestPlayers)
                                .executes(ctx -> history(ctx, 1))
                                .then(Commands.argument("pagina", IntegerArgumentType.integer(1))
                                        .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "pagina"))))))
                .then(Commands.literal("diagnostico")
                        .requires(source -> source.hasPermission(LoreConfig.load(source.getServer()).viewPermission))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(this::suggestPlayers).executes(this::diagnose)))
                .then(Commands.literal("reparar")
                        .requires(source -> source.hasPermission(LoreConfig.load(source.getServer()).maintenancePermission))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::repair)))
                .then(Commands.literal("desempenho")
                        .requires(source -> source.hasPermission(LoreConfig.load(source.getServer()).maintenancePermission))
                        .executes(this::performance))
                .then(Commands.literal("cofre")
                        .requires(source -> source.hasPermission(LoreConfig.load(source.getServer()).viewPermission))
                        .then(Commands.argument("player", StringArgumentType.word()).suggests(this::suggestPlayers).executes(this::vault)
                                .then(Commands.literal("restaurar")
                                        .requires(source -> source.hasPermission(LoreConfig.load(source.getServer()).maintenancePermission))
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("duracao", StringArgumentType.word())
                                                        .suggests(this::suggestDurations).executes(ctx -> restore(ctx, false)))))
                                .then(Commands.literal("manter")
                                        .requires(source -> source.hasPermission(LoreConfig.load(source.getServer()).maintenancePermission))
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                                                .executes(ctx -> restore(ctx, true))))
                                .then(Commands.literal("excluir")
                                        .requires(source -> source.hasPermission(LoreConfig.load(source.getServer()).maintenancePermission))
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                                                .executes(this::deleteVault))))));
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("✦ COMANDOS DE RELÍQUIAS ✦\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("/nlore temporario <player> <duração> [transferivel|vinculado] [motivo]\n")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("/nlore temporario mao <duração> [modo] [motivo]\n").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("/nlore revogar <player> <início-do-id>\n").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("/nlore historico <player>\n/nlore historico apagar <player>\n/nlore cofre <player>")
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\n/nlore diagnostico <player>\n/nlore reparar <player>")
                        .withStyle(ChatFormatting.AQUA)), false);
        return 1;
    }

    private boolean mode(CommandContext<CommandSourceStack> ctx) {
        String value = StringArgumentType.getString(ctx, "modo").toLowerCase(Locale.ROOT);
        return value.equals("transferivel");
    }

    private long duration(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "duracao").toLowerCase(Locale.ROOT);
        if (!raw.matches("[1-9][0-9]*(s|m|h|d)")) return -1;
        long value;
        try { value = Long.parseLong(raw.substring(0, raw.length() - 1)); }
        catch (NumberFormatException exception) { return -1; }
        long multiplier = switch (raw.charAt(raw.length() - 1)) {
            case 's' -> 1_000L; case 'm' -> 60_000L; case 'h' -> 3_600_000L; default -> 86_400_000L;
        };
        if (value > Long.MAX_VALUE / multiplier) return -1;
        long result = value * multiplier;
        return result > 3650L * 86_400_000L ? -1 : result;
    }

    private int grant(CommandContext<CommandSourceStack> ctx, boolean transferable, String reason) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        long duration = duration(ctx);
        if (duration < 0) { ctx.getSource().sendFailure(Component.literal("Duração inválida. Use 30m, 12h, 7d...")); return 0; }
        ServerPlayer staff = ctx.getSource().getPlayerOrException(), target = EntityArgument.getPlayer(ctx, "player");
        if (!service.grant(staff, target, duration, transferable, reason)) {
            ctx.getSource().sendFailure(Component.literal("Segure um item comum na mão principal.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Relíquia concedida a " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private int createHeld(CommandContext<CommandSourceStack> ctx, boolean transferable, String reason) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        long duration = duration(ctx);
        if (duration < 0) { ctx.getSource().sendFailure(Component.literal("Duração inválida.")); return 0; }
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        if (!service.makeHeldTemporary(staff, duration, transferable, reason)) {
            ctx.getSource().sendFailure(Component.literal("Segure um item comum na mão principal.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("O item em sua mão tornou-se uma relíquia temporária.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private int revoke(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        LoreTarget target = resolveTarget(ctx);
        if (target == null) return 0;
        if (!service.revoke(staff, target.id(), target.name(), StringArgumentType.getString(ctx, "id"))) {
            ctx.getSource().sendFailure(Component.literal("Nenhuma relíquia ativa corresponde a esse ID.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Concessão revogada.").withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private int openRevoke(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        LoreTarget target = resolveTarget(ctx);
        if (target == null) return 0;
        service.openRevokeMenu(ctx.getSource().getPlayerOrException(), target.id(), target.name());
        return 1;
    }

    private int history(CommandContext<CommandSourceStack> ctx, int page) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        LoreTarget target = resolveTarget(ctx);
        if (target == null) return 0;
        List<LoreStore.Entry> entries = service.data(ctx.getSource().getServer()).data.history
                .getOrDefault(target.id().toString(), List.of());
        if (entries.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Nenhum registro de relíquia.")); return 0; }
        int pageSize = 6, pages = (entries.size() + pageSize - 1) / pageSize;
        if (page > pages) { ctx.getSource().sendFailure(Component.literal("Página inexistente. Máximo: " + pages)); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("✦ HISTÓRICO DE RELÍQUIAS — " + target.name()
                        + " • " + page + "/" + pages + " ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        int from = Math.max(0, entries.size() - page * pageSize);
        int to = entries.size() - (page - 1) * pageSize;
        entries.subList(from, to).forEach(entry ->
                ctx.getSource().sendSuccess(() -> Component.literal("[" + TIME.format(Instant.ofEpochMilli(entry.timestamp())) + "] ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(entry.action().replace('_', ' ')).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                        .append(Component.literal("\n  " + entry.detail()).withStyle(ChatFormatting.GRAY)), false));
        Component previous = Component.literal("[← ANTERIOR]").withStyle(style -> style
                .withColor(page > 1 ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY).withBold(true)
                .withClickEvent(page > 1 ? new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/nlore historico " + target.name() + " " + (page - 1)) : null));
        Component next = Component.literal("[PRÓXIMA →]").withStyle(style -> style
                .withColor(page < pages ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY).withBold(true)
                .withClickEvent(page < pages ? new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/nlore historico " + target.name() + " " + (page + 1)) : null));
        if (pages > 1) ctx.getSource().sendSuccess(() -> Component.empty().append(previous)
                .append(Component.literal("   ")).append(next), false);
        return entries.size();
    }

    private int diagnose(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        LoreTarget target = resolveTarget(ctx);
        if (target == null) return 0;
        LoreService.Diagnosis result = target.online() == null
                ? service.diagnoseOffline(ctx.getSource().getServer(), target.id()) : service.diagnose(target.online());
        long remaining = result.soonestExpiry() == 0 ? 0 : Math.max(0, result.soonestExpiry() - System.currentTimeMillis());
        int issues = result.duplicates() + result.malformed() + result.retiredCopies();
        ctx.getSource().sendSuccess(() -> Component.literal("✦ DIAGNÓSTICO DE RELÍQUIAS — " + target.name() + " ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\nNo inventário: " + result.active() + " | localizadas no total: "
                        + result.knownActive() + " | recebidas de terceiros: " + result.transferred()
                        + " | no cofre: " + result.vault()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\nPróximo vencimento: " + (result.soonestExpiry() == 0 ? "nenhum"
                        : durationText(remaining))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\nSelos duplicados: " + result.duplicates() + " | metadados inválidos: "
                        + result.malformed() + " | cópias de rollback: " + result.retiredCopies())
                        .withStyle(issues == 0 ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        return issues == 0 ? 1 : issues;
    }

    private int repair(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        LoreService.Repair result = service.repair(ctx.getSource().getPlayerOrException(), target);
        ctx.getSource().sendSuccess(() -> Component.literal("✔ REPARO DE RELÍQUIAS CONCLUÍDO")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal("\nSelos corrigidos: " + result.reidentified()
                        + " | arquivadas com segurança: " + result.archived()
                        + " | cópias de rollback removidas: " + result.removedRollback())
                        .withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    private String durationText(long millis) {
        long minutes = Math.max(0, millis / 60_000L), days = minutes / 1440, hours = minutes % 1440 / 60;
        return days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + minutes % 60 + "m" : minutes + "m";
    }

    private int performance(CommandContext<CommandSourceStack> ctx) {
        LoreService.Performance result = service.performance();
        ctx.getSource().sendSuccess(() -> Component.literal("✦ DESEMPENHO NLORE ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(String.format(Locale.ROOT,
                        "\nÚltima varredura: %.3f ms | maior: %.3f ms\nVarreduras lentas: %d"
                                + "\nRecipientes vanilla: %d | fila: %d\nArmazenamentos de mods: %d | fila: %d",
                        result.lastNanos() / 1_000_000.0, result.maxNanos() / 1_000_000.0,
                        result.slowScans(), result.loadedContainers(), result.queuedContainers(),
                        result.loadedModHandlers(), result.queuedModHandlers()))
                        .withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private int requestClearHistory(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        LoreTarget target = resolveTarget(ctx);
        if (target == null) return 0;
        String command = "/nlore historico apagar " + target.name() + " confirmar";
        Component confirm = Component.literal("[CONFIRMAR]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Apagar definitivamente"))));
        Component cancel = Component.literal("[CANCELAR]").withStyle(style -> style
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nlore cancelar"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Manter o histórico"))));
        ctx.getSource().sendSuccess(() -> Component.literal("⚠ Apagar todo o histórico de relíquias de "
                        + target.name() + "?\n").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .append(confirm).append(Component.literal("  ")).append(cancel), false);
        return 1;
    }

    private int clearHistory(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        LoreTarget target = resolveTarget(ctx);
        if (target == null) return 0;
        LoreStore store = service.data(ctx.getSource().getServer());
        int removed = store.clearHistory(target.id());
        store.history(staff.getUUID(), staff.getName().getString(), "HISTORICO_APAGADO",
                target.name() + " | " + removed + " registro(s)");
        store.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Histórico de relíquias apagado: "
                + removed + " registro(s).").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        return 1;
    }

    private int vault(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        LoreTarget target = resolveTarget(ctx);
        if (target == null) return 0;
        service.openVault(ctx.getSource().getPlayerOrException(), target.id(), target.name()); return 1;
    }
    private int restore(CommandContext<CommandSourceStack> ctx, boolean permanent) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        long duration = permanent ? 0 : duration(ctx);
        if (!permanent && duration < 0) { ctx.getSource().sendFailure(Component.literal("Duração inválida.")); return 0; }
        LoreTarget target = resolveTarget(ctx);
        if (target == null || target.online() == null) {
            ctx.getSource().sendFailure(Component.literal("O jogador precisa estar online para receber o item restaurado.")); return 0;
        }
        LoreService.Result result = service.restore(ctx.getSource().getPlayerOrException(), target.online(),
                IntegerArgumentType.getInteger(ctx, "slot"), duration, permanent);
        if (result != LoreService.Result.SUCCESS) { ctx.getSource().sendFailure(Component.literal("Não foi possível restaurar: " + result)); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal(permanent ? "Relíquia restaurada permanentemente."
                : "Relíquia restaurada com novo prazo.").withStyle(ChatFormatting.GREEN), true); return 1;
    }
    private int deleteVault(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        LoreTarget target = resolveTarget(ctx);
        if (target == null) return 0;
        if (!service.deleteVault(ctx.getSource().getPlayerOrException(), target.id(),
                IntegerArgumentType.getInteger(ctx, "slot"))) { ctx.getSource().sendFailure(Component.literal("Slot inexistente.")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("Registro do cofre excluído.").withStyle(ChatFormatting.RED), true); return 1;
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestDurations(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("30m", "1h", "12h", "1d", "7d", "30d"), builder);
    }
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestModes(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(List.of("vinculado", "transferivel"), builder);
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSeals(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        LoreTarget target = resolveTarget(ctx, false);
        return SharedSuggestionProvider.suggest(target == null ? List.of()
                : service.activeSeals(ctx.getSource().getServer(), target.id()), builder);
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder);
    }

    private LoreTarget resolveTarget(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        LoreTarget target = resolveTarget(ctx, true);
        if (target == null) ctx.getSource().sendFailure(Component.literal(
                "Jogador desconhecido. Ele precisa ter entrado no servidor ao menos uma vez."));
        return target;
    }

    private LoreTarget resolveTarget(CommandContext<CommandSourceStack> ctx, boolean unused) {
        String name = StringArgumentType.getString(ctx, "player");
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (online != null) return new LoreTarget(online.getUUID(), online.getName().getString(), online);
        var cache = ctx.getSource().getServer().getProfileCache();
        if (cache == null) return null;
        var profile = cache.get(name).orElse(null);
        return profile == null ? null : new LoreTarget(profile.getId(), profile.getName(), null);
    }

    @SubscribeEvent public void tick(ServerTickEvent.Post event) { service.tick(event.getServer()); }
    @SubscribeEvent public void chunk(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || event.getLevel().getServer() == null) return;
        chunk.getBlockEntities().values().forEach(service::queue);
    }
    @SubscribeEvent public void chunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        chunk.getBlockEntities().values().forEach(service::unload);
    }
    @SubscribeEvent public void toss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        LoreItemData.read(event.getEntity().getItem()).ifPresent(info -> {
            LoreStore store = service.data(player.getServer());
            store.history(info.owner(), info.ownerName(), "RELIQUIA_DROPADA",
                    event.getEntity().getItem().getHoverName().getString() + " | por: " + player.getName().getString());
            store.save();
        });
    }

    private record LoreTarget(java.util.UUID id, String name, ServerPlayer online) {}
}
