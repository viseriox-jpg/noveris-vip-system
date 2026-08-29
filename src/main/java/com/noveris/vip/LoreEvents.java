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
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).grant))
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
                .then(Commands.literal("revogar").requires(source -> source.hasPermission(4))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("id", StringArgumentType.word()).executes(this::revoke))))
                .then(Commands.literal("historico")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).history))
                        .then(Commands.literal("apagar").requires(source -> source.hasPermission(4))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(this::requestClearHistory)
                                        .then(Commands.literal("confirmar").executes(this::clearHistory))))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::history)))
                .then(Commands.literal("cofre")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).vault))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::vault)
                                .then(Commands.literal("restaurar")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 54))
                                                .then(Commands.argument("duracao", StringArgumentType.word())
                                                        .suggests(this::suggestDurations).executes(ctx -> restore(ctx, false)))))
                                .then(Commands.literal("manter")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 54))
                                                .executes(ctx -> restore(ctx, true))))
                                .then(Commands.literal("excluir")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 54))
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
        ServerPlayer staff = ctx.getSource().getPlayerOrException(), target = EntityArgument.getPlayer(ctx, "player");
        if (!service.revoke(staff, target, StringArgumentType.getString(ctx, "id"))) {
            ctx.getSource().sendFailure(Component.literal("Nenhuma relíquia ativa corresponde a esse ID.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Concessão revogada.").withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private int history(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        List<LoreStore.Entry> entries = service.data(ctx.getSource().getServer()).data.history
                .getOrDefault(target.getUUID().toString(), List.of());
        ctx.getSource().sendSuccess(() -> Component.literal("✦ HISTÓRICO DE RELÍQUIAS — " + target.getName().getString() + " ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        entries.stream().skip(Math.max(0, entries.size() - 10L)).forEach(entry ->
                ctx.getSource().sendSuccess(() -> Component.literal("[" + TIME.format(Instant.ofEpochMilli(entry.timestamp())) + "] ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(entry.action().replace('_', ' ')).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                        .append(Component.literal("\n  " + entry.detail()).withStyle(ChatFormatting.GRAY)), false));
        return entries.size();
    }

    private int requestClearHistory(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String command = "/nlore historico apagar " + target.getName().getString() + " confirmar";
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
                        + target.getName().getString() + "?\n").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .append(confirm).append(Component.literal("  ")).append(cancel), false);
        return 1;
    }

    private int clearHistory(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        LoreStore store = service.data(ctx.getSource().getServer());
        int removed = store.clearHistory(target.getUUID());
        store.history(staff.getUUID(), staff.getName().getString(), "HISTORICO_APAGADO",
                target.getName().getString() + " | " + removed + " registro(s)");
        store.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Histórico de relíquias apagado: "
                + removed + " registro(s).").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        return 1;
    }

    private int vault(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        service.openVault(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player")); return 1;
    }
    private int restore(CommandContext<CommandSourceStack> ctx, boolean permanent) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        long duration = permanent ? 0 : duration(ctx);
        if (!permanent && duration < 0) { ctx.getSource().sendFailure(Component.literal("Duração inválida.")); return 0; }
        LoreService.Result result = service.restore(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"),
                IntegerArgumentType.getInteger(ctx, "slot"), duration, permanent);
        if (result != LoreService.Result.SUCCESS) { ctx.getSource().sendFailure(Component.literal("Não foi possível restaurar: " + result)); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal(permanent ? "Relíquia restaurada permanentemente."
                : "Relíquia restaurada com novo prazo.").withStyle(ChatFormatting.GREEN), true); return 1;
    }
    private int deleteVault(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!service.deleteVault(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"),
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

    @SubscribeEvent public void tick(ServerTickEvent.Post event) { service.tick(event.getServer()); }
    @SubscribeEvent public void chunk(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || event.getLevel().getServer() == null) return;
        chunk.getBlockEntities().values().forEach(blockEntity -> { if (blockEntity instanceof Container container) service.queue(container); });
    }
    @SubscribeEvent public void toss(ItemTossEvent event) {
        LoreItemData.read(event.getEntity().getItem()).ifPresent(info -> {
            LoreStore store = service.data(event.getPlayer().getServer());
            store.history(info.owner(), info.ownerName(), "RELIQUIA_DROPADA",
                    event.getEntity().getItem().getHoverName().getString() + " | por: " + event.getPlayer().getName().getString());
            store.save();
        });
    }
}
