package com.securetrade;

import com.securetrade.client.TradeScreen;
import com.securetrade.menu.TradeMenu;
import com.securetrade.menu.TradeMenuType;
import com.securetrade.network.TradeStateSyncPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class FabricClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register Screen
        ScreenRegistry.register(TradeMenuType.get(), TradeScreen::new);

        // Register Client-Side Packet Receivers (S2C)
        ClientPlayNetworking.registerGlobalReceiver(FabricSecureTradeMod.TRADE_STATE_SYNC_ID,
                (client, handler, buf, responseSender) -> {
                    TradeStateSyncPacket packet = new TradeStateSyncPacket(buf);
                    client.execute(() -> {
                        if (client.player != null && client.player.currentScreenHandler instanceof TradeMenu) {
                            TradeMenu tradeMenu = (TradeMenu) client.player.currentScreenHandler;
                            tradeMenu.updateFields(packet.myLock(), packet.otherLock(), packet.countdownSeconds(), packet.myXP(), packet.otherXP());
                        }
                    });
                });
    }
}
