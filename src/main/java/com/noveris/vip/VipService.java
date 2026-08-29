package com.noveris.vip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.Container;
import net.minecraft.core.component.DataComponents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class VipService {
    private VipStore store;
    private MinecraftServer server;
    private final Map<UUID, UUID> lastKnownHolder = new HashMap<>();
    private final java.util.ArrayDeque<Container> containerQueue = new java.util.ArrayDeque<>();
    private final java.util.Set<Container> queuedContainers = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<>());
    private static final int CONTAINERS_PER_TICK = 8;
    private int ticks;

    private VipStore store(MinecraftServer currentServer) {
        if (store == null || server != currentServer) {
            if (server != null && server != currentServer) {
                containerQueue.clear();
                queuedContainers.clear();
                lastKnownHolder.clear();
            }
            server = currentServer;
            store = VipStore.load(currentServer);
        }
        return store;
    }

    void openKitEditor(ServerPlayer staff, String kitName, String plan) {
        VipStore current = store(staff.getServer());
        SimpleContainer editor = new SimpleContainer(54);
        VipStore.Kit existing = current.data.kits.get(kitName.toLowerCase());
        MenuProvider provider = new SimpleMenuProvider((id, inventory, player) ->
                new KitEditorMenu(id, inventory, editor, this, kitName.toLowerCase(), plan, existing),
                Component.literal("Editor VIP — " + kitName));
        staff.openMenu(provider);
    }

    void saveKit(ServerPlayer staff, String kitName, String plan,
                 List<ItemStack> temporary, List<ItemStack> permanent) {
        VipStore current = store(staff.getServer());
        VipStore.Kit kit = new VipStore.Kit(kitName, plan);
        temporary.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> kit.items.add(
                new VipStore.KitItem(VipStore.encode(stack.copy(), staff.registryAccess()), true)));
        permanent.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> kit.items.add(
                new VipStore.KitItem(VipStore.encode(stack.copy(), staff.registryAccess()), false)));
        current.data.kits.put(kitName.toLowerCase(), kit);
        current.addHistory(staff.getUUID(), staff.getName().getString(), "KIT_SALVO",
                kitName + " | plano: " + plan + " | itens: " + kit.items.size());
        current.save();
        staff.sendSystemMessage(Component.literal("Kit " + kitName + " salvo com "
                + kit.items.size() + " pilhas de itens.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    void openChoiceCategoryEditor(ServerPlayer staff, String name, int limit) {
        VipStore current = store(staff.getServer());
        SimpleContainer editor = new SimpleContainer(54);
        VipStore.ChoiceCategory existing = current.data.choiceCategories.get(name.toLowerCase());
        staff.openMenu(new SimpleMenuProvider((id, inventory, player) ->
                new ChoiceCategoryEditorMenu(id, inventory, editor, this, name.toLowerCase(), limit, existing),
                Component.literal("Catálogo VIP — " + name + " • escolha " + limit)));
    }

    void saveChoiceCategory(ServerPlayer staff, String name, int limit,
                            List<ItemStack> temporary, List<ItemStack> permanent) {
        VipStore current = store(staff.getServer());
        VipStore.ChoiceCategory category = new VipStore.ChoiceCategory(name, limit);
        temporary.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> category.items.add(
                new VipStore.ChoiceItem(VipStore.encode(stack.copy(), staff.registryAccess()), true)));
        permanent.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> category.items.add(
                new VipStore.ChoiceItem(VipStore.encode(stack.copy(), staff.registryAccess()), false)));
        current.data.choiceCategories.put(name.toLowerCase(), category);
        current.addHistory(staff.getUUID(), staff.getName().getString(), "CATALOGO_SALVO",
                name + " | limite: " + limit + " | opções: " + category.items.size());
        current.save();
        staff.sendSystemMessage(Component.literal("Catálogo " + name + " salvo com " + category.items.size()
                + " opções e limite de " + limit + ".").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
    }

    boolean linkChoiceCategory(MinecraftServer server, String plan, String category) {
        VipStore current = store(server);
        if (!current.data.plans.containsKey(plan) || !current.data.choiceCategories.containsKey(category)) return false;
        List<String> categories = current.data.planChoiceCategories.computeIfAbsent(plan,
                ignored -> new java.util.ArrayList<>());
        if (!categories.contains(category)) categories.add(category);
        current.save();
        return true;
    }

    boolean unlinkChoiceCategory(MinecraftServer server, String plan, String category) {
        VipStore current = store(server);
        List<String> categories = current.data.planChoiceCategories.get(plan);
        if (categories == null || !categories.remove(category)) return false;
        current.save();
        return true;
    }

    boolean deleteChoiceCategory(MinecraftServer server, String category) {
        VipStore current = store(server);
        if (current.data.choiceCategories.remove(category) == null) return false;
        current.data.planChoiceCategories.values().forEach(list -> list.remove(category));
        current.data.pendingChoices.values().forEach(pending -> pending.remainingCategories.remove(category));
        current.save();
        return true;
    }

    private void createPendingChoices(VipStore current, UUID playerId, String playerName,
                                      String plan, String kit) {
        List<String> categories = current.data.planChoiceCategories.getOrDefault(plan, List.of()).stream()
                .filter(current.data.choiceCategories::containsKey).distinct().toList();
        if (categories.isEmpty()) current.data.pendingChoices.remove(playerId.toString());
        else current.data.pendingChoices.put(playerId.toString(),
                new VipStore.PendingChoices(playerName, plan, kit, categories));
    }

    boolean openChoices(ServerPlayer player) {
        VipStore current = store(player.getServer());
        String key = player.getUUID().toString();
        VipStore.PendingChoices pending = current.data.pendingChoices.get(key);
        VipStore.Profile profile = current.data.profiles.get(key);
        if (profile == null || profile.expiresAt() <= System.currentTimeMillis()
                || !current.data.choiceEligiblePlayers.contains(key)) {
            if (pending != null) { current.data.pendingChoices.remove(key); current.save(); }
            player.sendSystemMessage(Component.literal("Nenhuma decisão aguarda por você.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal("\nO direito de escolha ainda não foi concedido ao seu nome.")
                            .withStyle(ChatFormatting.GRAY)));
            return false;
        }
        if (pending == null && current.data.completedChoiceGrants.contains(key)) {
            player.sendSystemMessage(Component.literal("✦ SUA DECISÃO JÁ FOI SELADA ✦")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("\nAs relíquias escolhidas já lhe foram confiadas.")
                            .withStyle(ChatFormatting.GRAY)));
            return false;
        }
        if (pending == null) {
            createPendingChoices(current, player.getUUID(), player.getName().getString(), profile.plan(), profile.kit());
            pending = current.data.pendingChoices.get(key);
            current.save();
            if (pending == null) {
                player.sendSystemMessage(Component.literal("Nenhuma decisão aguarda por você.")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                        .append(Component.literal("\nSeu título ainda não possui relíquias destinadas à escolha.")
                                .withStyle(ChatFormatting.GRAY)));
                return false;
            }
        }
        pending.remainingCategories.removeIf(name -> !current.data.choiceCategories.containsKey(name));
        if (pending.remainingCategories.isEmpty()) {
            current.data.pendingChoices.remove(key);
            current.save();
            player.sendSystemMessage(Component.literal("✦ SUA DECISÃO JÁ FOI SELADA ✦")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            return false;
        }
        String categoryName = pending.remainingCategories.getFirst();
        VipStore.ChoiceCategory category = current.data.choiceCategories.get(categoryName);
        SimpleContainer display = new SimpleContainer(54);
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                new ChoiceSelectionMenu(id, inventory, display, this, categoryName, category),
                Component.literal("Escolha VIP — " + categoryName + " • " + category.limit)));
        return true;
    }

    boolean completeChoice(ServerPlayer player, String categoryName, List<Integer> indexes) {
        VipStore current = store(player.getServer());
        String key = player.getUUID().toString();
        VipStore.PendingChoices pending = current.data.pendingChoices.get(key);
        VipStore.Profile profile = current.data.profiles.get(key);
        VipStore.ChoiceCategory category = current.data.choiceCategories.get(categoryName);
        if (pending == null || profile == null || category == null || profile.expiresAt() <= System.currentTimeMillis()
                || pending.remainingCategories.isEmpty() || !pending.remainingCategories.getFirst().equals(categoryName)) return false;
        List<Integer> unique = indexes.stream().distinct().toList();
        int required = Math.min(category.limit, category.items.size());
        if (unique.size() != required || unique.stream().anyMatch(index -> index < 0 || index >= category.items.size())) return false;
        List<String> names = new java.util.ArrayList<>();
        for (int index : unique) {
            VipStore.ChoiceItem option = category.items.get(index);
            ItemStack stack = VipStore.decode(option.encodedStack(), player.registryAccess()).copy();
            names.add(stack.getHoverName().getString());
            if (option.temporary()) VipItemData.attach(stack, player.getUUID(), player.getName().getString(),
                    pending.kit + "/" + categoryName, profile.expiresAt());
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        pending.selections.put(categoryName, names);
        pending.remainingCategories.removeFirst();
        current.addHistory(player.getUUID(), player.getName().getString(), "ESCOLHA_VIP_CONCLUIDA",
                categoryName + " | " + String.join(", ", names));
        boolean finished = pending.remainingCategories.isEmpty();
        if (finished) {
            current.data.pendingChoices.remove(key);
            current.data.completedChoiceGrants.add(key);
        }
        current.save();
        player.sendSystemMessage(Component.literal(finished ? "✦ SUA DECISÃO FOI SELADA ✦"
                : "✦ UMA DECISÃO FOI SELADA ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(finished
                        ? "\nAs relíquias escolhidas agora carregam o seu nome."
                        : "\nUma nova escolha se abre diante de você.")
                        .withStyle(ChatFormatting.YELLOW)));
        return true;
    }

    String choiceStatus(ServerPlayer target) {
        VipStore current = store(target.getServer());
        String key = target.getUUID().toString();
        VipStore.PendingChoices pending = current.data.pendingChoices.get(key);
        if (pending == null && current.data.completedChoiceGrants.contains(key))
            return "todas as escolhas desta entrega foram concluídas";
        if (pending == null && current.data.choiceEligiblePlayers.contains(key))
            return "liberado por /vip dar, aguardando catálogos vinculados ao plano";
        if (pending == null) return "nenhuma escolha liberada por /vip dar";
        return "plano " + pending.plan + " | restantes: " + String.join(", ", pending.remainingCategories)
                + " | concluídas: " + String.join(", ", pending.selections.keySet());
    }

    boolean resetChoices(ServerPlayer staff, ServerPlayer target) {
        VipStore current = store(target.getServer());
        VipStore.Profile profile = current.data.profiles.get(target.getUUID().toString());
        if (profile == null || profile.expiresAt() <= System.currentTimeMillis()
                || !current.data.choiceEligiblePlayers.contains(target.getUUID().toString())) return false;
        createPendingChoices(current, target.getUUID(), target.getName().getString(), profile.plan(), profile.kit());
        current.data.completedChoiceGrants.remove(target.getUUID().toString());
        current.addHistory(target.getUUID(), target.getName().getString(), "ESCOLHAS_RESETADAS",
                "staff: " + staff.getName().getString());
        current.save();
        return current.data.pendingChoices.containsKey(target.getUUID().toString());
    }

    boolean grant(ServerPlayer staff, ServerPlayer target, String kitName, int days) {
        VipStore current = store(staff.getServer());
        VipStore.Kit kit = current.data.kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        long now = System.currentTimeMillis();
        long expiresAt = now + days * 24L * 60 * 60 * 1000;
        current.data.profiles.put(target.getUUID().toString(), new VipStore.Profile(
                target.getName().getString(), kit.plan, kit.name, now, expiresAt));
        for (VipStore.KitItem template : kit.items) {
            ItemStack stack = VipStore.decode(template.encodedStack(), target.registryAccess()).copy();
            if (template.temporary()) VipItemData.attach(stack, target.getUUID(),
                    target.getName().getString(), kit.name, expiresAt);
            if (!target.getInventory().add(stack)) target.drop(stack, false);
        }
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_CONCEDIDO",
                kit.plan + " | kit: " + kit.name + " | " + days + " dias | staff: " + staff.getName().getString());
        current.addHistory(staff.getUUID(), staff.getName().getString(), "VIP_ENTREGUE",
                target.getName().getString() + " | kit: " + kit.name);
        current.data.choiceEligiblePlayers.add(target.getUUID().toString());
        current.data.completedChoiceGrants.remove(target.getUUID().toString());
        createPendingChoices(current, target.getUUID(), target.getName().getString(), kit.plan, kit.name);
        current.data.sentWarnings.remove(target.getUUID().toString());
        current.save();
        target.sendSystemMessage(Component.literal("✦ UM NOVO TÍTULO LHE FOI CONCEDIDO ✦\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(planFlavor(kit.plan) + "\n\n").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("Plano: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(kit.plan).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("  |  Kit: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(kit.name).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("\nDuração: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(days + " dias").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal("\nAs relíquias de seu título foram confiadas a você.")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC)));
        if (current.data.pendingChoices.containsKey(target.getUUID().toString()))
            target.sendSystemMessage(Component.literal("✦ O DIREITO DE ESCOLHA LHE FOI CONCEDIDO ✦")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("\nDefina as relíquias que carregarão seu nome: ")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("/vip escolher").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        return true;
    }

    boolean grantTest(ServerPlayer staff, ServerPlayer target, String kitName, int minutes) {
        VipStore current = store(staff.getServer());
        VipStore.Kit kit = current.data.kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        long now = System.currentTimeMillis();
        long expiresAt = now + minutes * 60_000L;
        current.data.profiles.put(target.getUUID().toString(), new VipStore.Profile(
                target.getName().getString(), kit.plan, kit.name, now, expiresAt));
        current.data.choiceEligiblePlayers.remove(target.getUUID().toString());
        current.data.pendingChoices.remove(target.getUUID().toString());
        current.data.completedChoiceGrants.remove(target.getUUID().toString());
        int delivered = 0;
        for (VipStore.KitItem template : kit.items) {
            if (!template.temporary()) continue;
            ItemStack stack = VipStore.decode(template.encodedStack(), target.registryAccess()).copy();
            VipItemData.attach(stack, target.getUUID(), target.getName().getString(), kit.name, expiresAt);
            if (!target.getInventory().add(stack)) target.drop(stack, false);
            delivered++;
        }
        current.data.sentWarnings.remove(target.getUUID().toString());
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_TESTE_INICIADO",
                kit.name + " | " + minutes + " minuto(s) | itens temporários: " + delivered
                        + " | staff: " + staff.getName().getString());
        current.save();
        target.sendSystemMessage(Component.literal("⚠ VIP DE TESTE ATIVADO POR " + minutes + " MINUTO(S)")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        return true;
    }

    boolean renew(ServerPlayer staff, ServerPlayer target, int days) {
        VipStore current = store(staff.getServer());
        VipStore.Profile old = current.data.profiles.get(target.getUUID().toString());
        if (old == null) return false;
        long base = Math.max(System.currentTimeMillis(), old.expiresAt());
        long newExpiry = base + days * 24L * 60 * 60 * 1000;
        current.data.profiles.put(target.getUUID().toString(), new VipStore.Profile(
                old.playerName(), old.plan(), old.kit(), old.grantedAt(), newExpiry));
        updateExpiry(target, newExpiry);
        current.data.sentWarnings.remove(target.getUUID().toString());
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_RENOVADO",
                days + " dias | staff: " + staff.getName().getString());
        current.save();
        target.sendSystemMessage(Component.literal("✦ SEU TÍTULO PERMANECE ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\nO tempo de sua concessão foi estendido por ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(days + " dias").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
        return true;
    }

    boolean deliverPermanentItems(ServerPlayer staff, ServerPlayer target, String kitName) {
        VipStore current = store(staff.getServer());
        VipStore.Kit kit = current.data.kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        for (VipStore.KitItem template : kit.items) {
            if (template.temporary()) continue;
            ItemStack stack = VipStore.decode(template.encodedStack(), target.registryAccess()).copy();
            if (!target.getInventory().add(stack)) target.drop(stack, false);
        }
        current.addHistory(target.getUUID(), target.getName().getString(), "PERMANENTES_ENTREGUES",
                "kit: " + kit.name + " | staff: " + staff.getName().getString());
        current.save();
        return true;
    }

    boolean renewAndDeliver(ServerPlayer staff, ServerPlayer target, int days) {
        if (!renew(staff, target, days)) return false;
        VipStore current = store(staff.getServer());
        VipStore.Profile profile = current.data.profiles.get(target.getUUID().toString());
        VipStore.Kit kit = current.data.kits.get(profile.kit().toLowerCase());
        if (kit == null) return false;
        for (VipStore.KitItem template : kit.items) {
            ItemStack stack = VipStore.decode(template.encodedStack(), target.registryAccess()).copy();
            if (template.temporary()) VipItemData.attach(stack, target.getUUID(),
                    target.getName().getString(), kit.name, profile.expiresAt());
            if (!target.getInventory().add(stack)) target.drop(stack, false);
        }
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_RENOVADO_COM_KIT",
                days + " dias | staff: " + staff.getName().getString());
        current.save();
        return true;
    }

    boolean removeVip(ServerPlayer staff, ServerPlayer target) {
        VipStore current = store(staff.getServer());
        VipStore.Profile removed = current.data.profiles.remove(target.getUUID().toString());
        if (removed == null) return false;
        for (ItemStack stack : allPlayerStacks(target)) {
            VipItemData.read(stack).ifPresent(info -> {
                if (!info.originalOwner().equals(target.getUUID())) return;
                current.archive(target.getUUID(), target.getName().getString(), stack.copy(), target.registryAccess());
                stack.setCount(0);
            });
        }
        current.data.sentWarnings.remove(target.getUUID().toString());
        current.data.pendingChoices.remove(target.getUUID().toString());
        current.data.choiceEligiblePlayers.remove(target.getUUID().toString());
        current.data.completedChoiceGrants.remove(target.getUUID().toString());
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_REMOVIDO",
                "plano: " + removed.plan() + " | staff: " + staff.getName().getString());
        current.save();
        target.sendSystemMessage(Component.literal("✦ O TEMPO DA CONCESSÃO TERMINOU ✦")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("\nAs relíquias ligadas ao antigo título foram recolhidas.")
                        .withStyle(ChatFormatting.GRAY)));
        return true;
    }

    boolean queueDelivery(MinecraftServer server, UUID playerId, String playerName,
                          String kitName, int days, String staffName) {
        VipStore current = store(server);
        if (!current.data.kits.containsKey(kitName.toLowerCase())) return false;
        current.data.pendingDeliveries.put(playerId.toString(), new VipStore.PendingDelivery(
                playerName, kitName.toLowerCase(), days, staffName, System.currentTimeMillis(), false));
        current.addHistory(playerId, playerName, "ENTREGA_PENDENTE",
                "kit: " + kitName + " | " + days + " dias | staff: " + staffName);
        current.save();
        return true;
    }

    boolean queueRenewalKit(MinecraftServer server, UUID playerId, String playerName, String staffName) {
        VipStore current = store(server);
        VipStore.Profile profile = current.data.profiles.get(playerId.toString());
        if (profile == null || !current.data.kits.containsKey(profile.kit().toLowerCase())) return false;
        current.data.pendingDeliveries.put(playerId.toString(), new VipStore.PendingDelivery(
                playerName, profile.kit().toLowerCase(), 0, staffName, System.currentTimeMillis(), true));
        current.addHistory(playerId, playerName, "KIT_REENTREGA_PENDENTE",
                "kit: " + profile.kit() + " | staff: " + staffName);
        current.save();
        return true;
    }

    boolean renewOffline(MinecraftServer server, UUID playerId, String playerName,
                         int days, String staffName) {
        VipStore current = store(server);
        VipStore.Profile old = current.data.profiles.get(playerId.toString());
        if (old == null) return false;
        long newExpiry = Math.max(System.currentTimeMillis(), old.expiresAt())
                + days * 24L * 60 * 60 * 1000;
        current.data.profiles.put(playerId.toString(), new VipStore.Profile(
                playerName, old.plan(), old.kit(), old.grantedAt(), newExpiry));
        current.data.sentWarnings.remove(playerId.toString());
        current.addHistory(playerId, playerName, "VIP_RENOVADO_OFFLINE",
                days + " dias | staff: " + staffName);
        current.save();
        return true;
    }

    void playerLoggedIn(ServerPlayer player) {
        VipStore current = store(player.getServer());
        VipStore.PendingDelivery pending = current.data.pendingDeliveries.remove(player.getUUID().toString());
        if (pending != null) deliverPending(current, player, pending);
        VipStore.Profile profile = current.data.profiles.get(player.getUUID().toString());
        if (profile != null) updateExpiry(player, profile.expiresAt());
        current.save();
    }

    private void deliverPending(VipStore current, ServerPlayer target, VipStore.PendingDelivery pending) {
        VipStore.Kit kit = current.data.kits.get(pending.kit());
        if (kit == null) return;
        long now = System.currentTimeMillis();
        VipStore.Profile existing = current.data.profiles.get(target.getUUID().toString());
        long expiresAt = pending.renewalKit() && existing != null ? existing.expiresAt()
                : now + pending.days() * 24L * 60 * 60 * 1000;
        if (!pending.renewalKit()) current.data.profiles.put(target.getUUID().toString(), new VipStore.Profile(
                target.getName().getString(), kit.plan, kit.name, now, expiresAt));
        for (VipStore.KitItem template : kit.items) {
            ItemStack stack = VipStore.decode(template.encodedStack(), target.registryAccess()).copy();
            if (template.temporary()) VipItemData.attach(stack, target.getUUID(),
                    target.getName().getString(), kit.name, expiresAt);
            if (!target.getInventory().add(stack)) target.drop(stack, false);
        }
        current.addHistory(target.getUUID(), target.getName().getString(),
                pending.renewalKit() ? "KIT_REENTREGUE" : "VIP_ENTREGUE_AO_ENTRAR",
                "kit: " + kit.name + " | staff: " + pending.staffName());
        if (!pending.renewalKit()) {
            current.data.choiceEligiblePlayers.add(target.getUUID().toString());
            current.data.completedChoiceGrants.remove(target.getUUID().toString());
            createPendingChoices(current, target.getUUID(), target.getName().getString(), kit.plan, kit.name);
        }
        target.sendSystemMessage(Component.literal(pending.renewalKit()
                        ? "✦ AS RELÍQUIAS DE SEU TÍTULO RETORNARAM ✦"
                        : "✦ UM TÍTULO FOI RESERVADO EM SEU NOME ✦")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(pending.renewalKit()
                        ? "\nA renovação foi cumprida durante sua ausência."
                        : "\nEnquanto esteve ausente, a concessão aguardou seu retorno.")
                        .withStyle(ChatFormatting.YELLOW)));
        if (!pending.renewalKit() && current.data.pendingChoices.containsKey(target.getUUID().toString()))
            target.sendSystemMessage(Component.literal("O direito de escolha também lhe foi concedido: ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("/vip escolher").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
    }

    private void updateExpiry(ServerPlayer player, long expiry) {
        for (ItemStack stack : allPlayerStacks(player)) {
            VipItemData.read(stack).ifPresent(info -> {
                if (info.originalOwner().equals(player.getUUID())) VipItemData.renew(stack, expiry);
            });
        }
    }

    void tick(MinecraftServer currentServer) {
        processContainerQueue(currentServer);
        if (++ticks % 20 != 0) return;
        VipStore current = store(currentServer);
        long now = System.currentTimeMillis();
        normalizeSplitStacks(currentServer);
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            scanPlayer(current, player, now);
            sendExpiryWarnings(current, player, now);
        }
        if (ticks % 1200 == 0) scanDroppedItems(currentServer, current, now);
        if (ticks % 1200 == 0) {
            current.purgeVault();
            current.cleanup(VipConfig.load(currentServer));
            current.save();
        }
    }

    void queueContainer(Container container) {
        if (queuedContainers.add(container)) containerQueue.addLast(container);
    }

    private void processContainerQueue(MinecraftServer server) {
        for (int processed = 0; processed < CONTAINERS_PER_TICK && !containerQueue.isEmpty(); processed++) {
            Container container = containerQueue.removeFirst();
            queuedContainers.remove(container);
            if (container instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity
                    && blockEntity.isRemoved()) continue;
            scanContainer(server, container);
        }
    }

    private void normalizeSplitStacks(MinecraftServer server) {
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (ItemStack stack : allPlayerStacks(player)) {
                VipItemData.read(stack).ifPresent(info -> {
                    if (!seen.add(info.itemId())) seen.add(VipItemData.reidentify(stack));
                });
            }
        }
    }

    Diagnosis diagnose(ServerPlayer target) {
        VipStore current = store(target.getServer());
        String key = target.getUUID().toString();
        VipStore.Profile profile = current.data.profiles.get(key);
        int temporary = 0;
        int issues = 0;
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (ItemStack stack : allPlayerStacks(target)) {
            VipItemData.Info info = VipItemData.read(stack).orElse(null);
            if (info == null) {
                if (VipItemData.hasVipTag(stack)) issues++;
                continue;
            }
            temporary++;
            if (!seen.add(info.itemId()) || isRetired(current, info.itemId())) issues++;
            VipStore.Profile ownerProfile = current.data.profiles.get(info.originalOwner().toString());
            if (ownerProfile == null || ownerProfile.expiresAt() != info.expiresAt()) issues++;
        }
        long grants = current.data.history.getOrDefault(key, List.of()).stream()
                .filter(entry -> entry.action().equals("VIP_CONCEDIDO")
                        || entry.action().equals("VIP_ENTREGUE_AO_ENTRAR")
                        || entry.action().equals("VIP_RENOVADO_COM_KIT")).count();
        return new Diagnosis(profile, temporary,
                current.data.vault.getOrDefault(key, List.of()).size(),
                current.data.pendingDeliveries.containsKey(key), grants, issues);
    }

    GlobalDiagnosis diagnoseAll(MinecraftServer server) {
        VipStore current = store(server);
        long now = System.currentTimeMillis();
        int missingKitProfiles = 0;
        int expiredProfiles = 0;
        for (VipStore.Profile profile : current.data.profiles.values()) {
            if (!current.data.kits.containsKey(profile.kit().toLowerCase())) missingKitProfiles++;
            if (profile.expiresAt() <= now) expiredProfiles++;
        }
        int invalidPending = 0;
        for (VipStore.PendingDelivery pending : current.data.pendingDeliveries.values())
            if (!current.data.kits.containsKey(pending.kit().toLowerCase())) invalidPending++;
        int onlineIssues = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) onlineIssues += diagnose(player).issues();
        return new GlobalDiagnosis(current.data.profiles.size(), expiredProfiles, missingKitProfiles,
                current.data.pendingDeliveries.size(), invalidPending, current.data.vault.size(), onlineIssues);
    }

    RepairResult repair(ServerPlayer staff, ServerPlayer target) {
        VipStore current = store(target.getServer());
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        int reidentified = 0, removed = 0, synchronizedItems = 0, invalidMetadata = 0;
        for (ItemStack stack : allPlayerStacks(target)) {
            VipItemData.Info info = VipItemData.read(stack).orElse(null);
            if (info == null) {
                if (VipItemData.hasVipTag(stack)) {
                    VipItemData.makePermanent(stack);
                    invalidMetadata++;
                }
                continue;
            }
            if (isRetired(current, info.itemId())) {
                stack.setCount(0);
                removed++;
                continue;
            }
            if (!seen.add(info.itemId())) {
                UUID newId = VipItemData.reidentify(stack);
                if (newId != null) seen.add(newId);
                reidentified++;
                continue;
            }
            VipStore.Profile ownerProfile = current.data.profiles.get(info.originalOwner().toString());
            if (ownerProfile == null) {
                current.archive(target.getUUID(), target.getName().getString(), stack.copy(), target.registryAccess());
                stack.setCount(0);
                removed++;
            } else if (ownerProfile.expiresAt() != info.expiresAt()) {
                VipItemData.renew(stack, ownerProfile.expiresAt());
                synchronizedItems++;
            }
        }
        current.addHistory(target.getUUID(), target.getName().getString(), "VIP_REPARADO",
                "IDs renovados: " + reidentified + " | removidos: " + removed
                        + " | datas corrigidas: " + synchronizedItems + " | metadados limpos: " + invalidMetadata
                        + " | staff: " + staff.getName().getString());
        current.save();
        return new RepairResult(reidentified, removed, synchronizedItems, invalidMetadata);
    }

    record Diagnosis(VipStore.Profile profile, int temporaryItems, int vaultItems,
                     boolean pendingDelivery, long deliveredKits, int issues) {}
    record RepairResult(int reidentified, int removed, int synchronizedItems, int invalidMetadata) {}
    record GlobalDiagnosis(int profiles, int expiredProfiles, int missingKitProfiles,
                           int pendingDeliveries, int invalidPendingDeliveries,
                           int vaultOwners, int onlineItemIssues) {}

    private boolean isRetired(VipStore current, UUID itemId) {
        return current.data.retiredItemIds.contains(itemId.toString());
    }

    private void alertRollback(MinecraftServer server, VipStore current, VipItemData.Info info,
                               ItemStack stack, String playerName, String location) {
        VipStore.RetiredItem retired = current.data.retiredItems.get(info.itemId().toString());
        long archivedAt = retired == null ? 0 : retired.retiredAt();
        String archivedTime = archivedAt == 0 ? "desconhecido" : java.time.format.DateTimeFormatter
                .ofPattern("dd/MM/yyyy HH:mm").withZone(java.time.ZoneId.of("America/Sao_Paulo"))
                .format(java.time.Instant.ofEpochMilli(archivedAt));
        String detail = "jogador: " + playerName + " | item: " + stack.getHoverName().getString()
                + " | kit: " + info.kit() + " | id: " + info.itemId() + " | local: " + location
                + " | original arquivado: " + archivedTime;
        current.addHistory(info.originalOwner(), info.originalOwnerName(), "ITEM_ROLLBACK_BLOQUEADO", detail);
        for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
            if (!staff.hasPermissions(VipConfig.load(server).history)) continue;
            staff.sendSystemMessage(Component.literal("⚠ CÓPIA VIP DE ROLLBACK BLOQUEADA\n")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal("Jogador: " + playerName + " | Item: " + stack.getHoverName().getString()
                            + "\nKit: " + info.kit() + " | ID: " + info.itemId()
                            + "\nLocal: " + location + " | Arquivado em: " + archivedTime)
                            .withStyle(ChatFormatting.GRAY)));
        }
    }

    private void sendExpiryWarnings(VipStore current, ServerPlayer player, long now) {
        VipStore.Profile profile = current.data.profiles.get(player.getUUID().toString());
        if (profile == null) return;
        List<Integer> sent = current.data.sentWarnings.computeIfAbsent(player.getUUID().toString(),
                ignored -> new java.util.ArrayList<>());
        if (profile.expiresAt() <= now) {
            finalizeExpiredVip(current, player, profile);
            return;
        }
        long remainingDays = Math.max(1, (profile.expiresAt() - now + 86_399_999L) / 86_400_000L);
        for (int warning : VipConfig.load(player.getServer()).warningDays) {
            if (remainingDays <= warning && !sent.contains(warning)) {
                sent.add(warning);
                player.sendSystemMessage(Component.literal(remainingDays == 1
                                ? "✦ O ÚLTIMO CICLO DE SUA CONCESSÃO SE APROXIMA ✦"
                                : "✦ A FORÇA QUE SUSTENTA SEU TÍTULO ENFRAQUECE ✦")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                        .append(Component.literal("\nRestam ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(remainingDays + " dia(s)")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                        .append(Component.literal(" para o término do título ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(profile.plan()).withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
                current.save();
            }
        }
    }

    private void finalizeExpiredVip(VipStore current, ServerPlayer player, VipStore.Profile profile) {
        String key = player.getUUID().toString();
        current.data.profiles.remove(key);
        current.data.sentWarnings.remove(key);
        current.data.pendingChoices.remove(key);
        current.data.choiceEligiblePlayers.remove(key);
        current.data.completedChoiceGrants.remove(key);
        current.data.pendingDeliveries.remove(key);
        current.addHistory(player.getUUID(), player.getName().getString(), "VIP_EXPIRADO",
                "plano: " + profile.plan() + " | kit: " + profile.kit()
                        + " | relíquias recolhidas ao cofre por sete dias");

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("O TÍTULO FOI RECOLHIDO").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal("O tempo da concessão terminou").withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(Component.literal("✦ O TEMPO DA CONCESSÃO TERMINOU ✦")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("\nSeu título chegou ao fim. As relíquias vinculadas foram recolhidas "
                        + "e permanecerão sob custódia por sete dias.")
                        .withStyle(ChatFormatting.GRAY)));
        current.save();
    }

    private String planFlavor(String plan) {
        return switch (plan.toLowerCase()) {
            case "viajante" -> "Os caminhos agora reconhecem seus passos.";
            case "nobre" -> "Seu nome foi inscrito entre os dignos.";
            case "regente" -> "A autoridade dos Regentes lhe foi confiada.";
            case "soberano" -> "Diante de seu título, antigas portas se abrem.";
            default -> "Seu nome foi reconhecido entre os dignos.";
        };
    }

    private void scanDroppedItems(MinecraftServer server, VipStore current, long now) {
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity itemEntity)) continue;
                ItemStack stack = itemEntity.getItem();
                VipItemData.read(stack).ifPresent(info -> {
                    if (isRetired(current, info.itemId())) {
                        alertRollback(server, current, info, stack, info.originalOwnerName(),
                                "item no chão em " + level.dimension().location() + " "
                                        + itemEntity.blockPosition().toShortString());
                        itemEntity.discard();
                        current.save();
                        return;
                    }
                    if (info.expiresAt() > now) return;
                    current.archive(info.originalOwner(), info.originalOwnerName(), stack.copy(), level.registryAccess());
                    itemEntity.discard();
                    current.save();
                });
            }
        }
    }

    void scanContainer(MinecraftServer server, Container container) {
        VipStore current = store(server);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            VipItemData.Info info = VipItemData.read(stack).orElse(null);
            if (info == null) continue;
            if (isRetired(current, info.itemId())) {
                String location = container instanceof net.minecraft.world.level.block.entity.BlockEntity blockEntity
                        ? "recipiente em " + blockEntity.getBlockPos().toShortString() : "recipiente carregado";
                alertRollback(server, current, info, stack, info.originalOwnerName(), location);
                stack.setCount(0);
                changed = true;
                continue;
            }
            if (info.expiresAt() > now) continue;
            current.archive(info.originalOwner(), info.originalOwnerName(), stack.copy(), server.registryAccess());
            stack.setCount(0);
            changed = true;
        }
        if (changed) {
            container.setChanged();
            current.save();
        }
    }

    private void scanPlayer(VipStore current, ServerPlayer player, long now) {
        for (ItemStack stack : allPlayerStacks(player)) {
            VipItemData.read(stack).ifPresent(info -> {
                if (isRetired(current, info.itemId())) {
                    alertRollback(player.getServer(), current, info, stack, player.getName().getString(),
                            "inventário/ender chest do jogador");
                    stack.setCount(0);
                    current.save();
                    return;
                }
                UUID oldHolder = lastKnownHolder.put(info.itemId(), player.getUUID());
                if (oldHolder != null && !oldHolder.equals(player.getUUID())) {
                    current.addHistory(player.getUUID(), player.getName().getString(), "ITEM_RECEBIDO",
                            stack.getHoverName().getString() + " | kit: " + info.kit());
                    current.addHistory(oldHolder, oldHolder.toString(), "ITEM_TRANSFERIDO",
                            stack.getHoverName().getString() + " | para: " + player.getName().getString());
                    current.save();
                }
                if (info.expiresAt() <= now && !stack.isEmpty()) {
                    current.archive(player.getUUID(), player.getName().getString(), stack.copy(), player.registryAccess());
                    stack.setCount(0);
                    current.save();
                }
            });
        }
    }

    List<ItemStack> allPlayerStacks(ServerPlayer player) {
        List<ItemStack> result = new java.util.ArrayList<>();
        player.getInventory().items.forEach(result::add);
        player.getInventory().armor.forEach(result::add);
        player.getInventory().offhand.forEach(result::add);
        for (int slot = 0; slot < player.getEnderChestInventory().getContainerSize(); slot++)
            result.add(player.getEnderChestInventory().getItem(slot));
        return result;
    }

    VipStore data(MinecraftServer server) { return store(server); }

    void openVault(ServerPlayer staff, ServerPlayer target) {
        VipStore current = store(staff.getServer());
        List<VipStore.VaultEntry> entries = current.data.vault.getOrDefault(
                target.getUUID().toString(), List.of());
        SimpleContainer inventory = new SimpleContainer(54);
        for (int i = 0; i < Math.min(54, entries.size()); i++) {
            inventory.setItem(i, VipStore.decode(entries.get(i).encodedStack(), staff.registryAccess()));
        }
        staff.openMenu(new SimpleMenuProvider((id, inv, player) -> new VaultViewMenu(id, inv, inventory),
                Component.literal("Cofre VIP de " + target.getName().getString() + " | " + entries.size() + " itens")));
    }

    VaultResult restoreVault(ServerPlayer staff, ServerPlayer target, int oneBasedSlot,
                             int days, boolean permanent) {
        VipStore current = store(staff.getServer());
        List<VipStore.VaultEntry> entries = current.data.vault.get(target.getUUID().toString());
        if (entries == null || oneBasedSlot < 1 || oneBasedSlot > entries.size()) return VaultResult.INVALID_SLOT;
        VipStore.VaultEntry entry = entries.get(oneBasedSlot - 1);
        if (entry.deleteAt() <= System.currentTimeMillis()) return VaultResult.EXPIRED_ARCHIVE;
        ItemStack stack = VipStore.decode(entry.encodedStack(), target.registryAccess());
        if (stack.isEmpty()) return VaultResult.INVALID_ITEM;
        if (permanent) VipItemData.makePermanent(stack);
        else {
            VipItemData.renew(stack, System.currentTimeMillis() + days * 86_400_000L);
            VipItemData.reidentify(stack);
        }
        if (!target.getInventory().add(stack)) target.drop(stack, false);
        entries.remove(oneBasedSlot - 1);
        current.addHistory(target.getUUID(), target.getName().getString(),
                permanent ? "COFRE_RESTAURADO_PERMANENTE" : "COFRE_RESTAURADO",
                stack.getHoverName().getString() + " | staff: " + staff.getName().getString());
        current.save();
        return VaultResult.SUCCESS;
    }

    boolean deleteVaultEntry(ServerPlayer staff, ServerPlayer target, int oneBasedSlot) {
        VipStore current = store(staff.getServer());
        List<VipStore.VaultEntry> entries = current.data.vault.get(target.getUUID().toString());
        if (entries == null || oneBasedSlot < 1 || oneBasedSlot > entries.size()) return false;
        VipStore.VaultEntry removed = entries.remove(oneBasedSlot - 1);
        current.addHistory(target.getUUID(), target.getName().getString(), "COFRE_EXCLUIDO",
                "kit: " + removed.kit() + " | staff: " + staff.getName().getString());
        current.save();
        return true;
    }

    enum VaultResult { SUCCESS, INVALID_SLOT, EXPIRED_ARCHIVE, INVALID_ITEM }

    boolean openKitPreview(ServerPlayer player, String kitName) {
        VipStore current = store(player.getServer());
        VipStore.Kit kit = current.data.kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        SimpleContainer preview = new SimpleContainer(54);
        player.openMenu(new SimpleMenuProvider((id, inv, ignored) -> new KitPreviewMenu(id, inv, preview, current, kit),
                Component.literal("Kit " + kit.name + " — " + kit.plan)));
        return true;
    }
}
