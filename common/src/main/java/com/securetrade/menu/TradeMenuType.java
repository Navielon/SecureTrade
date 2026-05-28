package com.securetrade.menu;

import net.minecraft.world.inventory.MenuType;

public class TradeMenuType {
    private static MenuType<TradeMenu> tradeMenu;

    public static synchronized void set(MenuType<TradeMenu> menuType) {
        if (menuType == null) {
            throw new IllegalArgumentException("Trade menu type cannot be null");
        }
        if (tradeMenu != null) {
            throw new IllegalStateException("Trade menu type is already registered");
        }
        tradeMenu = menuType;
    }

    public static MenuType<TradeMenu> get() {
        if (tradeMenu == null) {
            throw new IllegalStateException("Trade menu type has not been registered yet");
        }
        return tradeMenu;
    }
}
