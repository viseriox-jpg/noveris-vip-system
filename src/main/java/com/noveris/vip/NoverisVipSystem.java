package com.noveris.vip;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(NoverisVipSystem.MOD_ID)
public final class NoverisVipSystem {
    public static final String MOD_ID = "noveris_vip_system";
    static final Logger LOGGER = LogUtils.getLogger();
    public NoverisVipSystem(IEventBus modBus) {
        VipMenus.register(modBus);
        NeoForge.EVENT_BUS.register(new VipEvents());
    }
}
