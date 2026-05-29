package com.securetrade.menu;

import net.minecraft.screen.ScreenHandlerType;

public class TradeMenuType {
    private static ScreenHandlerType<TradeMenu> tradeMenu;

    public static synchronized void set(ScreenHandlerType<TradeMenu> menuType) {
        if (menuType == null) {
            throw new IllegalArgumentException("Trade menu type cannot be null");
        }
        if (tradeMenu != null) {
            throw new IllegalStateException("Trade menu type is already registered");
        }
        tradeMenu = menuType;
    }

    public static ScreenHandlerType<TradeMenu> get() {
        if (tradeMenu == null) {
            throw new IllegalStateException("Trade menu type has not been registered yet");
        }
        return tradeMenu;
    }
}
