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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.Container;

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
                .executes(this::help)
                .then(Commands.literal("ajuda").executes(this::help))
                .then(Commands.literal("cancelar").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Operação cancelada.")
                            .withStyle(ChatFormatting.GRAY), false);
                    return 1;
                }))
                .then(Commands.literal("kits").executes(this::listPublicKits))
                .then(Commands.literal("plano")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).kitManage))
                        .then(Commands.literal("listar").executes(this::listPlans))
                        .then(Commands.literal("criar")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("nome", StringArgumentType.string())
                                                .executes(this::createPlan))))
                        .then(Commands.literal("ativar")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(this::suggestAllPlans)
                                        .executes(ctx -> togglePlan(ctx, true))))
                        .then(Commands.literal("desativar")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(this::suggestAllPlans)
                                        .executes(ctx -> togglePlan(ctx, false))))
                        .then(Commands.literal("renomear")
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(this::suggestAllPlans)
                                        .then(Commands.argument("nome", StringArgumentType.string())
                                                .executes(this::renamePlan)))))
                .then(Commands.literal("kit")
                        .then(Commands.literal("ver")
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .suggests(this::suggestKits).executes(this::previewKit)))
                        .then(Commands.literal("criar")
                                .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).kitManage))
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .then(Commands.argument("plano", StringArgumentType.word())
                                                .suggests(this::suggestPlans)
                                                .executes(this::openEditor))))
                        .then(Commands.literal("editar")
                                .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).kitManage))
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .suggests(this::suggestKits).executes(this::editKit)))
                        .then(Commands.literal("listar").requires(source -> source.hasPermission(VipConfig.load(source.getServer()).kitManage))
                                .executes(this::listKits))
                        .then(Commands.literal("excluir")
                                .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).kitManage))
                                .then(Commands.argument("nome", StringArgumentType.word())
                                        .suggests(this::suggestKits).executes(this::requestDeleteKit)
                                        .then(Commands.literal("confirmar").executes(this::deleteKit)))))
                .then(Commands.literal("dar")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).grant))
                        .then(Commands.argument("kit", StringArgumentType.word()).suggests(this::suggestKits)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> grant(ctx, 30, false))
                                        .then(Commands.literal("confirmar").executes(ctx -> grant(ctx, 30, true)))
                                        .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                                .executes(ctx -> grant(ctx, IntegerArgumentType.getInteger(ctx, "dias"), false))
                                                .then(Commands.literal("confirmar").executes(ctx -> grant(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "dias"), true)))))))
                .then(Commands.literal("renovar")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).renew))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                        .executes(this::renew))))
                .then(Commands.literal("renovarcomkit")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).renew))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                        .executes(this::renewWithKit))))
                .then(Commands.literal("entregarpermanentes")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).grant))
                        .then(Commands.argument("kit", StringArgumentType.word()).suggests(this::suggestKits)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(this::deliverPermanent))))
                .then(Commands.literal("daroffline")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).grant))
                        .then(Commands.argument("kit", StringArgumentType.word()).suggests(this::suggestKits)
                                .then(Commands.argument("nick", StringArgumentType.word())
                                        .executes(ctx -> queueOffline(ctx, 30))
                                        .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                                .executes(ctx -> queueOffline(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "dias")))))))
                .then(Commands.literal("renovaroffline")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).renew))
                        .then(Commands.argument("nick", StringArgumentType.word())
                                .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                        .executes(this::renewOffline))))
                .then(Commands.literal("consultar")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).history))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::inspect)))
                .then(Commands.literal("diagnostico")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).history))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::diagnose)))
                .then(Commands.literal("reparar")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::repair)))
                .then(Commands.literal("remover")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).grant))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(this::requestRemoveVip)
                                .then(Commands.literal("confirmar").executes(this::removeVip))))
                .then(Commands.literal("historico")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).history))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> history(ctx, "todos", 1))
                                .then(Commands.argument("filtro", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("todos", "vip", "item", "cofre", "kit"), builder))
                                        .executes(ctx -> history(ctx,
                                                StringArgumentType.getString(ctx, "filtro"), 1))
                                        .then(Commands.argument("pagina", IntegerArgumentType.integer(1))
                                                .executes(ctx -> history(ctx,
                                                        StringArgumentType.getString(ctx, "filtro"),
                                                        IntegerArgumentType.getInteger(ctx, "pagina")))))))
                .then(Commands.literal("apagarhistorico")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(this::requestClearHistory)
                                .then(Commands.literal("confirmar").executes(this::clearHistory))))
                .then(Commands.literal("cofre")
                        .requires(source -> source.hasPermission(VipConfig.load(source.getServer()).vault))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::vault)
                                .then(Commands.literal("restaurar")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 54))
                                                .then(Commands.argument("dias", IntegerArgumentType.integer(1, 3650))
                                                        .executes(ctx -> restoreVault(ctx, false)))))
                                .then(Commands.literal("permanente")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 54))
                                                .executes(ctx -> restoreVault(ctx, true))))
                                .then(Commands.literal("excluir")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 54))
                                                .executes(this::deleteVault)))));
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        VipConfig config = VipConfig.load(source.getServer());
        source.sendSuccess(() -> Component.literal("✦ AJUDA — SISTEMA VIP ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        helpLine(source, "/vip kits", "Ver os kits disponíveis", "/vip kits");
        helpLine(source, "/vip kit ver <nome>", "Visualizar um kit", "/vip kit ver ");
        if (source.hasPermission(config.kitManage)) {
            helpLine(source, "/vip kit criar <nome> <plano>", "Criar um kit", "/vip kit criar ");
            helpLine(source, "/vip kit editar <nome>", "Editar um kit", "/vip kit editar ");
        }
        if (source.hasPermission(config.grant)) {
            helpLine(source, "/vip dar <kit> <player> [dias]", "Entregar VIP", "/vip dar ");
            helpLine(source, "/vip remover <player>", "Remover VIP", "/vip remover ");
        }
        if (source.hasPermission(config.renew))
            helpLine(source, "/vip renovar <player> <dias>", "Renovar VIP", "/vip renovar ");
        if (source.hasPermission(config.history)) {
            helpLine(source, "/vip diagnostico <player>", "Diagnóstico completo", "/vip diagnostico ");
            helpLine(source, "/vip historico <player>", "Consultar auditoria", "/vip historico ");
        }
        if (source.hasPermission(config.vault))
            helpLine(source, "/vip cofre <player>", "Abrir o cofre", "/vip cofre ");
        if (source.hasPermission(4)) {
            helpLine(source, "/vip reparar <player>", "Corrigir inconsistências", "/vip reparar ");
            helpLine(source, "/vip apagarhistorico <player>", "Apagar histórico", "/vip apagarhistorico ");
        }
        source.sendSuccess(() -> Component.literal("Clique em um comando para colocá-lo no chat.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
        return 1;
    }

    private void helpLine(CommandSourceStack source, String command, String description, String suggestion) {
        source.sendSuccess(() -> Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(command).withStyle(style -> style
                        .withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestion))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Clique para preencher")))))
                .append(Component.literal(" — " + description).withStyle(ChatFormatting.GRAY)), false);
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

    private int listPlans(CommandContext<CommandSourceStack> ctx) {
        var plans = service.data(ctx.getSource().getServer()).data.plans.values().stream()
                .sorted(java.util.Comparator.comparingInt(VipStore.PlanDefinition::order)).toList();
        ctx.getSource().sendSuccess(() -> Component.literal("✦ PLANOS VIP ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        plans.forEach(plan -> ctx.getSource().sendSuccess(() -> Component.literal("• " + plan.id() + " — ")
                .withStyle(ChatFormatting.AQUA).append(Component.literal(plan.displayName())
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(plan.enabled() ? "  [ATIVO]" : "  [DESATIVADO]")
                        .withStyle(plan.enabled() ? ChatFormatting.GREEN : ChatFormatting.RED)), false));
        return plans.size();
    }

    private int createPlan(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id").toLowerCase();
        String name = StringArgumentType.getString(ctx, "nome");
        VipStore store = service.data(ctx.getSource().getServer());
        if (store.data.plans.containsKey(id)) { ctx.getSource().sendFailure(Component.literal("Esse plano já existe.")); return 0; }
        int order = store.data.plans.size() + 1;
        store.data.plans.put(id, new VipStore.PlanDefinition(id, name, true, order));
        store.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Plano " + name + " criado.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private int togglePlan(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String id = StringArgumentType.getString(ctx, "id").toLowerCase();
        VipStore store = service.data(ctx.getSource().getServer());
        VipStore.PlanDefinition old = store.data.plans.get(id);
        if (old == null) { ctx.getSource().sendFailure(Component.literal("Plano inexistente.")); return 0; }
        store.data.plans.put(id, new VipStore.PlanDefinition(id, old.displayName(), enabled, old.order()));
        store.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Plano " + id + (enabled ? " ativado." : " desativado."))
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return 1;
    }

    private int renamePlan(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id").toLowerCase();
        String name = StringArgumentType.getString(ctx, "nome");
        VipStore store = service.data(ctx.getSource().getServer());
        VipStore.PlanDefinition old = store.data.plans.get(id);
        if (old == null) { ctx.getSource().sendFailure(Component.literal("Plano inexistente.")); return 0; }
        store.data.plans.put(id, new VipStore.PlanDefinition(id, name, old.enabled(), old.order()));
        store.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Nome de exibição alterado para " + name + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
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
        String plan = StringArgumentType.getString(ctx, "plano").toLowerCase();
        VipStore.PlanDefinition definition = service.data(ctx.getSource().getServer()).data.plans.get(plan);
        if (definition == null || !definition.enabled()) {
            ctx.getSource().sendFailure(Component.literal("Plano inexistente ou desativado.")); return 0;
        }
        service.openKitEditor(ctx.getSource().getPlayerOrException(), name, plan);
        return 1;
    }

    private int editKit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "nome").toLowerCase();
        VipStore.Kit kit = service.data(ctx.getSource().getServer()).data.kits.get(name);
        if (kit == null) { ctx.getSource().sendFailure(Component.literal("Kit inexistente.")); return 0; }
        service.openKitEditor(ctx.getSource().getPlayerOrException(), name, kit.plan);
        return 1;
    }

    private int listKits(CommandContext<CommandSourceStack> ctx) {
        return listPublicKits(ctx);
    }

    private int requestDeleteKit(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "nome").toLowerCase();
        if (!service.data(ctx.getSource().getServer()).data.kits.containsKey(name)) {
            ctx.getSource().sendFailure(Component.literal("Kit inexistente."));
            return 0;
        }
        sendConfirmation(ctx.getSource(), "Excluir definitivamente o kit " + name + "?",
                "/vip kit excluir " + name + " confirmar");
        return 1;
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

    private int grant(CommandContext<CommandSourceStack> ctx, int days, boolean confirmed) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String kit = StringArgumentType.getString(ctx, "kit");
        VipStore.Profile existing = service.data(ctx.getSource().getServer()).data.profiles
                .get(target.getUUID().toString());
        if (!confirmed && existing != null && existing.expiresAt() > System.currentTimeMillis()) {
            sendConfirmation(ctx.getSource(), target.getName().getString() + " já possui o plano "
                    + existing.plan() + ". Substituir pelo kit " + kit + "?", "/vip dar " + kit + " "
                    + target.getName().getString() + " " + days + " confirmar");
            return 0;
        }
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

    private int renewWithKit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int days = IntegerArgumentType.getInteger(ctx, "dias");
        if (!service.renewAndDeliver(staff, target, days)) {
            ctx.getSource().sendFailure(Component.literal("O jogador não possui VIP ou o kit foi removido.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("✔ VIP renovado e kit entregue novamente.")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        return 1;
    }

    private int deliverPermanent(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String kit = StringArgumentType.getString(ctx, "kit");
        if (!service.deliverPermanentItems(staff, target, kit)) {
            ctx.getSource().sendFailure(Component.literal("Kit inexistente.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("✔ Somente os itens permanentes foram entregues.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private int queueOffline(CommandContext<CommandSourceStack> ctx, int days) throws CommandSyntaxException {
        String nick = StringArgumentType.getString(ctx, "nick");
        OfflinePlayer player = resolveOffline(ctx.getSource(), nick);
        if (player == null) return 0;
        String kit = StringArgumentType.getString(ctx, "kit");
        String staff = ctx.getSource().getPlayerOrException().getName().getString();
        if (!service.queueDelivery(ctx.getSource().getServer(), player.id, player.name, kit, days, staff)) {
            ctx.getSource().sendFailure(Component.literal("Kit inexistente.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("✔ Entrega de " + kit + " agendada para "
                + player.name + ". O kit será entregue quando o jogador entrar.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private int renewOffline(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String nick = StringArgumentType.getString(ctx, "nick");
        OfflinePlayer player = resolveOffline(ctx.getSource(), nick);
        if (player == null) return 0;
        int days = IntegerArgumentType.getInteger(ctx, "dias");
        String staff = ctx.getSource().getPlayerOrException().getName().getString();
        if (!service.renewOffline(ctx.getSource().getServer(), player.id, player.name, days, staff)) {
            ctx.getSource().sendFailure(Component.literal("O jogador não possui VIP registrado.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("✔ VIP offline renovado por " + days + " dias.")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private OfflinePlayer resolveOffline(CommandSourceStack source, String nick) {
        var cache = source.getServer().getProfileCache();
        if (cache == null) {
            source.sendFailure(Component.literal("O cache de perfis do servidor não está disponível.")); return null;
        }
        var profile = cache.get(nick).orElse(null);
        if (profile == null) {
            source.sendFailure(Component.literal("Jogador desconhecido. Ele precisa ter entrado no servidor ao menos uma vez."));
            return null;
        }
        return new OfflinePlayer(profile.getId(), profile.getName());
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

    private int diagnose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        VipService.Diagnosis diagnosis = service.diagnose(target);
        VipStore.Profile profile = diagnosis.profile();
        Component report = Component.literal("✦ DIAGNÓSTICO VIP — ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(target.getDisplayName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        if (profile == null) {
            report.append(Component.literal("\nPlano atual: nenhum").withStyle(ChatFormatting.RED));
        } else {
            long remaining = Math.max(0, profile.expiresAt() - System.currentTimeMillis());
            report.append(Component.literal("\nPlano atual: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(profile.plan()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("  |  Kit: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(profile.kit()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("\nInício: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(TIME.format(Instant.ofEpochMilli(profile.grantedAt())))
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  |  Vencimento: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(TIME.format(Instant.ofEpochMilli(profile.expiresAt())))
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("\nTempo restante: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(formatDuration(remaining)).withStyle(ChatFormatting.GREEN));
        }
        report.append(Component.literal("\nKits recebidos: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(diagnosis.deliveredKits())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  |  Itens temporários ativos: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(diagnosis.temporaryItems())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\nItens arquivados: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(diagnosis.vaultItems())).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  |  Entrega pendente: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(diagnosis.pendingDelivery() ? "sim" : "não")
                        .withStyle(diagnosis.pendingDelivery() ? ChatFormatting.YELLOW : ChatFormatting.GREEN))
                .append(Component.literal("\nPossíveis inconsistências: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(diagnosis.issues())).withStyle(
                        diagnosis.issues() == 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
        ctx.getSource().sendSuccess(() -> report, false);
        return 1;
    }

    private String formatDuration(long millis) {
        long totalHours = millis / 3_600_000L;
        long days = totalHours / 24;
        long hours = totalHours % 24;
        return days + " dia(s) e " + hours + " hora(s)";
    }

    private int repair(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        VipService.RepairResult result = service.repair(staff, target);
        ctx.getSource().sendSuccess(() -> Component.literal("✔ REPARO CONCLUÍDO — ")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(target.getDisplayName().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\nIDs duplicados corrigidos: " + result.reidentified()
                        + " | cópias inválidas removidas: " + result.removed()
                        + "\nDatas sincronizadas: " + result.synchronizedItems()
                        + " | metadados inválidos limpos: " + result.invalidMetadata())
                        .withStyle(ChatFormatting.GRAY)), true);
        return 1;
    }

    private int requestRemoveVip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        sendConfirmation(ctx.getSource(), "Remover o VIP e arquivar os itens temporários de "
                + target.getName().getString() + "?", "/vip remover " + target.getName().getString() + " confirmar");
        return 1;
    }

    private int removeVip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (!service.removeVip(staff, target)) {
            ctx.getSource().sendFailure(Component.literal("O jogador não possui VIP registrado.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("VIP removido; itens temporários foram enviados ao cofre.")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return 1;
    }

    private int requestClearHistory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        sendConfirmation(ctx.getSource(), "Apagar definitivamente todo o histórico de "
                + target.getName().getString() + "?", "/vip apagarhistorico "
                + target.getName().getString() + " confirmar");
        return 1;
    }

    private int clearHistory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        VipStore store = service.data(ctx.getSource().getServer());
        int removed = store.clearHistory(target.getUUID());
        store.addHistory(staff.getUUID(), staff.getName().getString(), "HISTORICO_APAGADO",
                target.getName().getString() + " | " + removed + " registro(s)");
        store.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✔ Histórico de " + target.getName().getString()
                + " apagado: " + removed + " registro(s).").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        return 1;
    }

    private void sendConfirmation(CommandSourceStack source, String warning, String confirmCommand) {
        Component confirm = Component.literal("[CONFIRMAR]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, confirmCommand))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Executar operação"))));
        Component cancel = Component.literal("[CANCELAR]").withStyle(style -> style
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vip cancelar"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Cancelar operação"))));
        source.sendSuccess(() -> Component.literal("⚠ " + warning + "\n")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                .append(confirm).append(Component.literal("  ")).append(cancel), false);
    }

    private int history(CommandContext<CommandSourceStack> ctx, String filter, int page) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        List<VipStore.HistoryEntry> entries = service.data(ctx.getSource().getServer()).data.history
                .getOrDefault(target.getUUID().toString(), List.of());
        List<VipStore.HistoryEntry> filtered = entries.stream().filter(entry -> matchesFilter(entry.action(), filter)).toList();
        if (filtered.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Nenhum evento para esse filtro.")); return 0; }
        int pageSize = 6;
        int pageCount = (filtered.size() + pageSize - 1) / pageSize;
        if (page > pageCount) { ctx.getSource().sendFailure(Component.literal("Página inexistente. Máximo: " + pageCount)); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("✦ HISTÓRICO VIP — ")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(target.getDisplayName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal("  |  " + filter + "  |  " + page + "/" + pageCount)
                        .withStyle(ChatFormatting.GRAY)), false);
        int from = Math.max(0, filtered.size() - page * pageSize);
        int to = filtered.size() - (page - 1) * pageSize;
        filtered.subList(from, to).forEach(entry ->
                ctx.getSource().sendSuccess(() -> Component.literal("[" + TIME.format(Instant.ofEpochMilli(entry.timestamp())) + "] ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(actionLabel(entry.action()))
                                .withStyle(actionColor(entry.action()), ChatFormatting.BOLD))
                        .append(Component.literal("\n  " + entry.detail()).withStyle(ChatFormatting.GRAY)), false));
        return filtered.size();
    }

    private boolean matchesFilter(String action, String filter) {
        return switch (filter.toLowerCase()) {
            case "todos" -> true;
            case "vip" -> action.startsWith("VIP_") || action.contains("ENTREGA");
            case "item" -> action.startsWith("ITEM_");
            case "cofre" -> action.startsWith("COFRE_");
            case "kit" -> action.startsWith("KIT_");
            default -> false;
        };
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

    private int restoreVault(CommandContext<CommandSourceStack> ctx, boolean permanent)
            throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        int days = permanent ? 0 : IntegerArgumentType.getInteger(ctx, "dias");
        VipService.VaultResult result = service.restoreVault(staff, target, slot, days, permanent);
        if (result != VipService.VaultResult.SUCCESS) {
            ctx.getSource().sendFailure(Component.literal("Não foi possível restaurar: " + switch (result) {
                case INVALID_SLOT -> "slot inexistente";
                case EXPIRED_ARCHIVE -> "o prazo de sete dias já terminou";
                case INVALID_ITEM -> "os dados do item estão inválidos";
                default -> "erro desconhecido";
            } + "."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(permanent
                ? "✔ Item restaurado como permanente." : "✔ Item restaurado por " + days + " dias.")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        return 1;
    }

    private int deleteVault(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        if (!service.deleteVaultEntry(staff, target, slot)) {
            ctx.getSource().sendFailure(Component.literal("Slot inexistente no cofre.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Item do cofre excluído definitivamente.")
                .withStyle(ChatFormatting.RED), true);
        return 1;
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestKits(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(service.data(ctx.getSource().getServer()).data.kits.keySet(), builder);
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlans(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(service.data(ctx.getSource().getServer()).data.plans.values().stream()
                .filter(VipStore.PlanDefinition::enabled).map(VipStore.PlanDefinition::id).toList(), builder);
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestAllPlans(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(service.data(ctx.getSource().getServer()).data.plans.keySet(), builder);
    }

    @SubscribeEvent public void tick(ServerTickEvent.Post event) { service.tick(event.getServer()); }

    @SubscribeEvent public void playerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) service.playerLoggedIn(player);
    }

    @SubscribeEvent public void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || event.getLevel().getServer() == null) return;
        chunk.getBlockEntities().values().forEach(blockEntity -> {
            if (blockEntity instanceof Container container) service.queueContainer(container);
        });
    }

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

    private record OfflinePlayer(java.util.UUID id, String name) {}
}
