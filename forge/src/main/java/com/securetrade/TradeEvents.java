package com.securetrade;

import com.securetrade.menu.TradeSessionManager;
import com.securetrade.command.TradeCommand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = SecureTradeMod.MODID)
public class TradeEvents {
    private static int cleanupTicks = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TradeSessionManager.tick();
        cleanupTicks++;
        if (cleanupTicks >= 1200) {
            cleanupTicks = 0;
            TradeCommand.pruneExpired();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TradeSessionManager.cancelAllAndClear();
        TradeCommand.clearAll();
        TradeLogger.shutdown();
    }
}
