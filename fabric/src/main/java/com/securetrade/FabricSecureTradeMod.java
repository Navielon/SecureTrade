package com.securetrade;

import com.securetrade.command.TradeCommand;
import com.securetrade.menu.TradeMenu;
import com.securetrade.menu.TradeMenuType;
import com.securetrade.menu.TradeSessionManager;
import com.securetrade.network.TradeLockPacket;
import com.securetrade.network.TradeXPChangePacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

public class FabricSecureTradeMod implements ModInitializer {
    public static final String MODID = "securetrade";
    public static final ResourceLocation TRADE_LOCK_ID = new ResourceLocation(MODID, "trade_lock");
    public static final ResourceLocation TRADE_XP_CHANGE_ID = new ResourceLocation(MODID, "trade_xp_change");
    public static final ResourceLocation TRADE_STATE_SYNC_ID = new ResourceLocation(MODID, "trade_state_sync");

    private int cleanupTicks = 0;

    @Override
    public void onInitialize() {
        // Load configuration
        FabricTradeConfig.load();

        // Register MenuType
        TradeMenuType.set(Registry.register(
                Registry.MENU,
                new ResourceLocation(MODID, "trade_menu"),
                new MenuType<>(TradeMenu::new)
        ));

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                TradeCommand.register(dispatcher));

        // Register Server-Side Packet Receivers (C2S)
        ServerPlayNetworking.registerGlobalReceiver(TRADE_LOCK_ID,
                (server, player, handler, buf, responseSender) -> {
                    TradeLockPacket packet = new TradeLockPacket(buf);
                    server.execute(() -> {
                        if (player.containerMenu instanceof TradeMenu menu) {
                            menu.setLocked(player, packet.locked());
                        }
                    });
                });

        ServerPlayNetworking.registerGlobalReceiver(TRADE_XP_CHANGE_ID,
                (server, player, handler, buf, responseSender) -> {
                    TradeXPChangePacket packet = new TradeXPChangePacket(buf);
                    server.execute(() -> {
                        if (player.containerMenu instanceof TradeMenu menu) {
                            menu.setOfferedXP(player, packet.xpPoints());
                        }
                    });
                });

        // Register Ticking Event
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TradeSessionManager.tick();
            cleanupTicks++;
            if (cleanupTicks >= 1200) {
                cleanupTicks = 0;
                TradeCommand.pruneExpired();
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TradeSessionManager.cancelAllAndClear();
            TradeCommand.clearAll();
            TradeLogger.shutdown();
        });
    }
}
