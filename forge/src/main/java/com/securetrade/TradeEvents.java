package com.securetrade;

import com.securetrade.menu.TradeSessionManager;
import com.securetrade.command.TradeCommand;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = SecureTradeMod.MODID)
public class TradeEvents {
    private static int cleanupTicks = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        TradeSessionManager.tick();
        cleanupTicks++;
        if (cleanupTicks >= 1200) {
            cleanupTicks = 0;
            TradeCommand.pruneExpired();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        TradeSessionManager.cancelAllAndClear();
        TradeCommand.clearAll();
        TradeLogger.shutdown();
    }
}
