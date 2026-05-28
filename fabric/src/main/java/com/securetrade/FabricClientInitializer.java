package com.securetrade;

import com.securetrade.client.TradeScreen;
import com.securetrade.menu.TradeMenu;
import com.securetrade.menu.TradeMenuType;
import com.securetrade.network.TradeStateSyncPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class FabricClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register Screen
        MenuScreens.register(TradeMenuType.get(), TradeScreen::new);

        // Register Client-Side Packet Receivers
        ClientPlayNetworking.registerGlobalReceiver(TradeStateSyncPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null && context.client().player.containerMenu instanceof TradeMenu tradeMenu) {
                    tradeMenu.updateFields(payload.myLock(), payload.otherLock(), payload.countdownSeconds(), payload.myXP(), payload.otherXP());
                }
            });
        });
    }
}
