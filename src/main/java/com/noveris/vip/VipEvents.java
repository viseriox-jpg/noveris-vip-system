package com.noveris.vip;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class VipEvents {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private final VipService service = new VipService();

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(commandRoot("vip"));
        event.getDispatcher().register(commandRoot("noverisvip"));
    }

    private LiteralArgumentBuilder<CommandSourceStack> commandRoot(String root) {
        return Commands.literal(root)
                .then(Commands.literal("kits").executes(this::listPublicKits))
                .then(Commands.literal("kit")
                        .then(Commands.literal("ver")
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .suggests(this::suggestKits).executes(this::previewKit)))
                        .then(Commands.literal("criar")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .then(Commands.argument("plano", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(VipPlan.names(), builder))
                                                .executes(this::openEditor))))
                        .then(Commands.literal("editar")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .suggests(this::suggestKits).executes(this::editKit)))
                        .then(Commands.literal("listar").requires(source -> source.hasPermission(2))
                                .executes(this::listKits))
                        .then(Commands.literal("excluir")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .suggests(this::suggestKits).executes(this::deleteKit))))
                .then(Commands.literal("dar")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("kit", StringArgumentType.word()).suggests(this::suggestKits)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> grant(ctx, 30))
                                        .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                                .executes(ctx -> grant(ctx, IntegerArgumentType.getInteger(ctx, "dias")))))))
                .then(Commands.literal("renovar")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                        .executes(this::renew))))
                .then(Commands.literal("consultar")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::inspect)))
                .then(Commands.literal("historico")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::history)))
                .then(Commands.literal("cofre")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::vault)));
    }

    private int listPublicKits(CommandContext<CommandSourceStack> ctx) {
        var kits = service.data(ctx.getSource().getServer()).data.kits.values();
        if (kits.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Ainda não existem kits VIP disponíveis.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("✦ KITS VIP DISPONÍVEIS ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        kits.forEach(kit -> {
            long temporary = kit.items.stream().filter(VipStore.KitItem::temporary).count();
            long permanent = kit.items.size() - temporary;
            ctx.getSource().sendSuccess(() -> Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(kit.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal("  Plano: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(kit.plan).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("  |  ⌛ " + temporary + " temporários")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("  |  ◆ " + permanent + " permanentes")
                            .withStyle(ChatFormatting.GREEN)), false);
        });
        ctx.getSource().sendSuccess(() -> Component.literal("Use /vip kit ver <nome> para visualizar os itens.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        return kits.size();
    }

    private int previewKit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "nome");
        if (!service.openKitPreview(ctx.getSource().getPlayerOrException(), name)) {
            ctx.getSource().sendFailure(Component.literal("Esse kit não existe.").withStyle(ChatFormatting.RED));
            return 0;
        }
        return 1;
    }

    private int openEditor(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "nome").toLowerCase();
        VipPlan plan = VipPlan.from(StringArgumentType.getString(ctx, "plano")).orElse(null);
        if (plan == null) { ctx.getSource().sendFailure(Component.literal("Plano inválido.")); return 0; }
        service.openKitEditor(ctx.getSource().getPlayerOrException(), name, plan);
        return 1;
    }

    private int editKit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "nome").toLowerCase();
        VipStore.Kit kit = service.data(ctx.getSource().getServer()).data.kits.get(name);
        if (kit == null) { ctx.getSource().sendFailure(Component.literal("Kit inexistente.")); return 0; }
        service.openKitEditor(ctx.getSource().getPlayerOrException(), name,
                VipPlan.from(kit.plan).orElse(VipPlan.VIAJANTE));
        return 1;
    }

    private int listKits(CommandContext<CommandSourceStack> ctx) {
        return listPublicKits(ctx);
    }

    private int deleteKit(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "nome").toLowerCase();
        VipStore store = service.data(ctx.getSource().getServer());
        if (store.data.kits.remove(name) == null) { ctx.getSource().sendFailure(Component.literal("Kit inexistente.")); return 0; }
        store.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✔ Kit ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" excluído.").withStyle(ChatFormatting.GREEN)), true);
        return 1;
    }

    private int grant(CommandContext<CommandSourceStack> ctx, int days) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String kit = StringArgumentType.getString(ctx, "kit");
        if (!service.grant(staff, target, kit, days)) {
            ctx.getSource().sendFailure(Component.literal("Kit inexistente.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("✔ VIP ENTREGUE\n")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal("Jogador: ").withStyle(ChatFormatting.GRAY))
                .append(target.getDisplayName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal("\nKit: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(kit).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  |  Duração: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(days + " dias").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), true);
        return 1;
    }

    private int renew(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int days = IntegerArgumentType.getInteger(ctx, "dias");
        if (!service.renew(staff, target, days)) { ctx.getSource().sendFailure(Component.literal("O jogador não possui VIP.")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("✔ VIP de ").withStyle(ChatFormatting.GREEN)
                .append(target.getDisplayName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" renovado por ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(days + " dias").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), true);
        return 1;
    }

    private int inspect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        VipStore.Profile profile = service.data(ctx.getSource().getServer()).data.profiles.get(target.getUUID().toString());
        if (profile == null) { ctx.getSource().sendFailure(Component.literal("O jogador não possui VIP registrado.")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("✦ VIP DE ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(target.getDisplayName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal("\nPlano: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(profile.plan()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("  |  Kit: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(profile.kit()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\nVencimento: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(TIME.format(Instant.ofEpochMilli(profile.expiresAt())))
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)), false);
        return 1;
    }

    private int history(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        List<VipStore.HistoryEntry> entries = service.data(ctx.getSource().getServer()).data.history
                .getOrDefault(target.getUUID().toString(), List.of());
        if (entries.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Nenhum histórico VIP.")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("✦ HISTÓRICO VIP — ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(target.getDisplayName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)), false);
        entries.stream().skip(Math.max(0, entries.size() - 12L)).forEach(entry ->
                ctx.getSource().sendSuccess(() -> Component.literal("[" + TIME.format(Instant.ofEpochMilli(entry.timestamp())) + "] ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(actionLabel(entry.action()))
                                .withStyle(actionColor(entry.action()), ChatFormatting.BOLD))
                        .append(Component.literal("\n  " + entry.detail()).withStyle(ChatFormatting.GRAY)), false));
        return entries.size();
    }

    private String actionLabel(String action) {
        return switch (action) {
            case "KIT_SALVO" -> "Kit salvo";
            case "VIP_CONCEDIDO" -> "VIP concedido";
            case "VIP_ENTREGUE" -> "VIP entregue";
            case "VIP_RENOVADO" -> "VIP renovado";
            case "ITEM_DROPADO" -> "Item dropado";
            case "ITEM_RECEBIDO" -> "Item recebido";
            case "ITEM_TRANSFERIDO" -> "Item transferido";
            case "ITEM_EXPIRADO", "ITEM_EXPIRADO_COM_TERCEIRO" -> "Item expirado";
            default -> action.replace('_', ' ').toLowerCase();
        };
    }

    private ChatFormatting actionColor(String action) {
        if (action.contains("EXPIRADO")) return ChatFormatting.RED;
        if (action.contains("DROPADO") || action.contains("TRANSFERIDO")) return ChatFormatting.YELLOW;
        if (action.contains("RECEBIDO")) return ChatFormatting.AQUA;
        return ChatFormatting.GREEN;
    }

    private int vault(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        service.openVault(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
        return 1;
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestKits(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(service.data(ctx.getSource().getServer()).data.kits.keySet(), builder);
    }

    @SubscribeEvent public void tick(ServerTickEvent.Post event) { service.tick(event.getServer()); }

    @SubscribeEvent public void toss(ItemTossEvent event) {
        VipItemData.read(event.getEntity().getItem()).ifPresent(info -> {
            VipStore store = service.data(event.getPlayer().getServer());
            store.addHistory(event.getPlayer().getUUID(), event.getPlayer().getName().getString(), "ITEM_DROPADO",
                    event.getEntity().getItem().getHoverName().getString() + " | kit: " + info.kit());
            if (!event.getPlayer().getUUID().equals(info.originalOwner())) store.addHistory(
                    info.originalOwner(), info.originalOwnerName(), "ITEM_DROPADO_POR_TERCEIRO",
                    event.getEntity().getItem().getHoverName().getString() + " | por: " + event.getPlayer().getName().getString());
            store.save();
        });
    }
}
