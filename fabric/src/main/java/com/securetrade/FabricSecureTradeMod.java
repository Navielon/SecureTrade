package com.securetrade;

import com.securetrade.command.TradeCommand;
import com.securetrade.menu.TradeMenu;
import com.securetrade.menu.TradeMenuType;
import com.securetrade.menu.TradeSessionManager;
import com.securetrade.network.TradeBlacklistWarningPacket;
import com.securetrade.network.TradeLockPacket;
import com.securetrade.network.TradeStateSyncPacket;
import com.securetrade.network.TradeXPChangePacket;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public class FabricSecureTradeMod implements ModInitializer {
    public static final String MODID = "securetrade";
    private int cleanupTicks = 0;

    @Override
    public void onInitialize() {
        FabricTradeConfig.load();

        TradeMenuType.set(Registry.register(
                BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(MODID, "trade_menu"),
                new MenuType<>(TradeMenu::new, FeatureFlags.DEFAULT_FLAGS)
        ));
        SecureTradeSounds.register((id, sound) -> Registry.register(BuiltInRegistries.SOUND_EVENT, id, sound));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TradeCommand.register(dispatcher);
        });

        PayloadTypeRegistry.serverboundPlay().register(TradeLockPacket.TYPE, TradeLockPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TradeXPChangePacket.TYPE, TradeXPChangePacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TradeStateSyncPacket.TYPE, TradeStateSyncPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TradeBlacklistWarningPacket.TYPE, TradeBlacklistWarningPacket.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TradeLockPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof TradeMenu menu) {
                    menu.setLocked(context.player(), payload.locked());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TradeXPChangePacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof TradeMenu menu) {
                    menu.setOfferedXP(context.player(), payload.xpPoints());
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
