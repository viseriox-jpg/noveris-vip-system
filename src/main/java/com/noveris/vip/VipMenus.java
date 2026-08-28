package com.noveris.vip;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class VipMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, NoverisVipSystem.MOD_ID);

    public static final Supplier<MenuType<KitEditorMenu>> KIT_EDITOR = MENUS.register("kit_editor",
            () -> new MenuType<>(KitEditorMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<MenuType<VaultViewMenu>> VIP_VIEW = MENUS.register("vip_view",
            () -> new MenuType<>(VaultViewMenu::new, FeatureFlags.DEFAULT_FLAGS));

    static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }

    private VipMenus() {}
}
