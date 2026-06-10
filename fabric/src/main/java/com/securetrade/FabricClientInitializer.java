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
        MenuScreens.register(TradeMenuType.get(), TradeScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(FabricSecureTradeMod.TRADE_STATE_SYNC_ID,
                (client, handler, buf, responseSender) -> {
                    TradeStateSyncPacket packet = new TradeStateSyncPacket(buf);
                    client.execute(() -> {
                        if (client.player != null && client.player.containerMenu instanceof TradeMenu tradeMenu) {
                            tradeMenu.updateFields(packet.myLock(), packet.otherLock(), packet.countdownSeconds(), packet.myXP(), packet.otherXP(), packet.otherTotalXP(), packet.partnerName());
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(FabricSecureTradeMod.TRADE_BLACKLIST_WARNING_ID,
                (client, handler, buf, responseSender) -> client.execute(() -> {
                    if (client.player != null && client.player.containerMenu instanceof TradeMenu tradeMenu) {
                        tradeMenu.showBlacklistWarning();
                    }
                }));
    }
}
