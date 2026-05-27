package com.securetrade;

import com.mojang.logging.LogUtils;
import com.securetrade.command.TradeCommand;
import com.securetrade.menu.TradeMenu;
import com.securetrade.network.TradeNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;

@Mod(SecureTradeMod.MODID)
public class SecureTradeMod {
    public static final String MODID = "securetrade";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TradeMenu>> TRADE_MENU = MENUS.register("trade_menu", () -> IMenuTypeExtension.create((windowId, inv, data) -> new TradeMenu(windowId, inv)));

    public SecureTradeMod(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, TradeConfig.SPEC);

        MENUS.register(modEventBus);
        
        modEventBus.addListener(this::registerNetworking);
        
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        TradeNetwork.register(event.registrar(MODID));
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        TradeCommand.register(event.getDispatcher());
    }
}
