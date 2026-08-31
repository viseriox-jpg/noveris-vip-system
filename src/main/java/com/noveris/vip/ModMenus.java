package com.noveris.vip;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NoverisVipSystem.MOD_ID);

    static final Supplier<MenuType<NoverisVaultMenu>> VAULT = MENUS.register(
            "vault", () -> new MenuType<>(NoverisVaultMenu::new, FeatureFlags.DEFAULT_FLAGS));

    static void register(IEventBus bus) {
        MENUS.register(bus);
    }

    private ModMenus() {}
}
