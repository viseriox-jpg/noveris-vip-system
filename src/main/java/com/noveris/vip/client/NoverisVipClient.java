package com.noveris.vip.client;

import com.noveris.vip.NoverisVipSystem;
import com.noveris.vip.VipMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = NoverisVipSystem.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NoverisVipClient {
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(VipMenus.KIT_EDITOR.get(), KitEditorScreen::new);
        event.register(VipMenus.VIP_VIEW.get(), VipViewScreen::new);
    }

    private NoverisVipClient() {}
}
