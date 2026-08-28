package com.noveris.vip;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
        event.getDispatcher().register(Commands.literal("noverisvip")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("kit")
                        .then(Commands.literal("criar")
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .then(Commands.argument("plano", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(VipPlan.names(), builder))
                                                .executes(this::openEditor))))
                        .then(Commands.literal("editar")
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .suggests(this::suggestKits).executes(this::editKit)))
                        .then(Commands.literal("listar").executes(this::listKits))
                        .then(Commands.literal("excluir")
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .suggests(this::suggestKits).executes(this::deleteKit))))
                .then(Commands.literal("dar")
                        .then(Commands.argument("kit", StringArgumentType.word()).suggests(this::suggestKits)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> grant(ctx, 30))
                                        .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                                .executes(ctx -> grant(ctx, IntegerArgumentType.getInteger(ctx, "dias"))))))
                .then(Commands.literal("renovar")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                        .executes(this::renew))))
                .then(Commands.literal("consultar")
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::inspect)))
                .then(Commands.literal("historico")
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::history)))
                .then(Commands.literal("cofre")
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::vault))));
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
        var kits = service.data(ctx.getSource().getServer()).data.kits.values();
        if (kits.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Nenhum kit cadastrado.")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("Kits VIP:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        kits.forEach(kit -> ctx.getSource().sendSuccess(() -> Component.literal("- " + kit.name
                + " | " + kit.plan + " | " + kit.items.size() + " itens"), false));
        return kits.size();
    }

    private int deleteKit(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "nome").toLowerCase();
        VipStore store = service.data(ctx.getSource().getServer());
        if (store.data.kits.remove(name) == null) { ctx.getSource().sendFailure(Component.literal("Kit inexistente.")); return 0; }
        store.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Kit " + name + " excluído."), true);
        return 1;
    }

    private int grant(CommandContext<CommandSourceStack> ctx, int days) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String kit = StringArgumentType.getString(ctx, "kit");
        if (!service.grant(staff, target, kit, days)) {
            ctx.getSource().sendFailure(Component.literal("Kit inexistente.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("VIP entregue a " + target.getName().getString()
                + " por " + days + " dias.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private int renew(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int days = IntegerArgumentType.getInteger(ctx, "dias");
        if (!service.renew(staff, target, days)) { ctx.getSource().sendFailure(Component.literal("O jogador não possui VIP.")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("VIP renovado."), true);
        return 1;
    }

    private int inspect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        VipStore.Profile profile = service.data(ctx.getSource().getServer()).data.profiles.get(target.getUUID().toString());
        if (profile == null) { ctx.getSource().sendFailure(Component.literal("O jogador não possui VIP registrado.")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " | plano: "
                + profile.plan() + " | kit: " + profile.kit() + " | vence: "
                + TIME.format(Instant.ofEpochMilli(profile.expiresAt()))), false);
        return 1;
    }

    private int history(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        List<VipStore.HistoryEntry> entries = service.data(ctx.getSource().getServer()).data.history
                .getOrDefault(target.getUUID().toString(), List.of());
        if (entries.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Nenhum histórico VIP.")); return 0; }
        entries.stream().skip(Math.max(0, entries.size() - 12L)).forEach(entry ->
                ctx.getSource().sendSuccess(() -> Component.literal("[" + TIME.format(Instant.ofEpochMilli(entry.timestamp()))
                        + "] " + entry.action() + " | " + entry.detail()), false));
        return entries.size();
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
