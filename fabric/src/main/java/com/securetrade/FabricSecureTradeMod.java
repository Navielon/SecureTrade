package com.securetrade;

import com.securetrade.command.TradeCommand;
import com.securetrade.menu.TradeMenu;
import com.securetrade.menu.TradeMenuType;
import com.securetrade.menu.TradeSessionManager;
import com.securetrade.network.TradeLockPacket;
import com.securetrade.network.TradeStateSyncPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import com.securetrade.client.TradeScreen;

public class FabricSecureTradeMod implements ModInitializer {
    public static final String MODID = "securetrade";

    @Override
    public void onInitialize() {
        // Load configuration
        FabricTradeConfig.load();

        // Register MenuType
        TradeMenuType.TRADE_MENU = Registry.register(
                BuiltInRegistries.MENU,
                ResourceLocation.fromNamespaceAndPath(MODID, "trade_menu"),
                new MenuType<>(TradeMenu::new, FeatureFlags.DEFAULT_FLAGS)
        );

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TradeCommand.register(dispatcher);
        });

        // Register Network Packets
        PayloadTypeRegistry.playC2S().register(TradeLockPacket.TYPE, TradeLockPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(TradeStateSyncPacket.TYPE, TradeStateSyncPacket.STREAM_CODEC);

        // Register Server-Side Packet Receivers
        ServerPlayNetworking.registerGlobalReceiver(TradeLockPacket.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof TradeMenu menu) {
                    menu.setLocked(context.player(), payload.locked());
                }
            });
        });

        // Register Ticking Event
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TradeSessionManager.tick();
        });
    }
}
