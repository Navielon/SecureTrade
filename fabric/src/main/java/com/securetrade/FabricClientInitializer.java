package com.securetrade;

import com.securetrade.client.TradeScreen;
import com.securetrade.menu.TradeMenuType;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class FabricClientInitializer implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register Screen
        MenuScreens.register(TradeMenuType.TRADE_MENU, TradeScreen::new);
    }
}
