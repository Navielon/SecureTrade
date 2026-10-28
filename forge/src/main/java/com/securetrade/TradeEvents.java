package com.securetrade;

import com.securetrade.menu.TradeSessionManager;
import com.securetrade.command.TradeCommand;
import com.securetrade.escrow.TradeEscrow;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.entity.player.ServerPlayerEntity;

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
    public static void onServerStarted(FMLServerStartedEvent event) {
        TradeEscrow.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(FMLServerStoppedEvent event) {
        TradeEscrow.onServerStopped();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            TradeEscrow.onPlayerJoin((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        TradeSessionManager.cancelAllAndClear();
        TradeCommand.clearAll();
        TradePreferencesManager.clear();
        TradeHistoryManager.shutdown();
        TradeLogger.shutdown();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            TradeSessionManager.cancelForPlayer((ServerPlayerEntity) event.getPlayer());
        }
    }
}
