package com.securetrade;

import com.securetrade.menu.TradeSessionManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SecureTradeMod.MODID)
public class TradeEvents {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TradeSessionManager.tick();
    }
}
