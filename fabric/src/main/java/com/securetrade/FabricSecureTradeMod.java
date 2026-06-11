package com.securetrade;

import com.securetrade.command.TradeCommand;
import com.securetrade.menu.TradeMenu;
import com.securetrade.menu.TradeMenuType;
import com.securetrade.menu.TradeSessionManager;
import com.securetrade.network.TradeLockPacket;
import com.securetrade.network.TradeXPChangePacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.Identifier;

public class FabricSecureTradeMod implements ModInitializer {
    public static final String MODID = "securetrade";
    public static final Identifier TRADE_LOCK_ID = new Identifier(MODID, "trade_lock");
    public static final Identifier TRADE_XP_CHANGE_ID = new Identifier(MODID, "trade_xp_change");
    public static final Identifier TRADE_STATE_SYNC_ID = new Identifier(MODID, "trade_state_sync");
    public static final Identifier TRADE_BLACKLIST_WARNING_ID = new Identifier(MODID, "trade_blacklist_warning");
    private int cleanupTicks = 0;

    @Override
    public void onInitialize() {
        FabricTradeConfig.load();

        TradeMenuType.set(net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry.registerSimple(
                new Identifier(MODID, "trade_menu"),
                TradeMenu::new
        ));
        SecureTradeSounds.register((id, sound) -> Registry.register(Registry.SOUND_EVENT, id, sound));

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            TradeCommand.register(dispatcher);
        });

        ServerPlayNetworking.registerGlobalReceiver(TRADE_LOCK_ID,
                (server, player, handler, buf, responseSender) -> {
                    TradeLockPacket packet = new TradeLockPacket(buf);
                    server.execute(() -> {
                        if (player.currentScreenHandler instanceof TradeMenu) {
                            TradeMenu menu = (TradeMenu) player.currentScreenHandler;
                            menu.setLocked(player, packet.locked());
                        }
                    });
                });

        ServerPlayNetworking.registerGlobalReceiver(TRADE_XP_CHANGE_ID,
                (server, player, handler, buf, responseSender) -> {
                    TradeXPChangePacket packet = new TradeXPChangePacket(buf);
                    server.execute(() -> {
                        if (player.currentScreenHandler instanceof TradeMenu) {
                            TradeMenu menu = (TradeMenu) player.currentScreenHandler;
                            menu.setOfferedXP(player, packet.xpPoints());
                        }
                    });
                });

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

