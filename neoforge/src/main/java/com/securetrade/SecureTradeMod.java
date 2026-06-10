package com.securetrade;

import com.securetrade.command.TradeCommand;
import com.securetrade.menu.TradeMenu;
import com.securetrade.menu.TradeMenuType;
import com.securetrade.network.TradeNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(SecureTradeMod.MODID)
public class SecureTradeMod {
    public static final String MODID = "securetrade";
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TradeMenu>> TRADE_MENU = MENUS.register("trade_menu", () -> {
        MenuType<TradeMenu> type = IMenuTypeExtension.create((windowId, inv, data) -> new TradeMenu(windowId, inv));
        TradeMenuType.set(type);
        return type;
    });
    public static final DeferredHolder<SoundEvent, SoundEvent> TRADE_ITEM_ADD =
            SOUNDS.register("trade_item_add", () -> SecureTradeSounds.TRADE_ITEM_ADD);
    public static final DeferredHolder<SoundEvent, SoundEvent> TRADE_ITEM_BLOCKED =
            SOUNDS.register("trade_item_blocked", () -> SecureTradeSounds.TRADE_ITEM_BLOCKED);
    public static final DeferredHolder<SoundEvent, SoundEvent> TRADE_CANCEL =
            SOUNDS.register("trade_cancel", () -> SecureTradeSounds.TRADE_CANCEL);
    public static final DeferredHolder<SoundEvent, SoundEvent> TRADE_COUNTDOWN_TICK =
            SOUNDS.register("trade_countdown_tick", () -> SecureTradeSounds.TRADE_COUNTDOWN_TICK);
    public static final DeferredHolder<SoundEvent, SoundEvent> TRADE_REQUEST_SENT =
            SOUNDS.register("trade_request_sent", () -> SecureTradeSounds.TRADE_REQUEST_SENT);
    public static final DeferredHolder<SoundEvent, SoundEvent> TRADE_SUCCESS =
            SOUNDS.register("trade_success", () -> SecureTradeSounds.TRADE_SUCCESS);

    public SecureTradeMod(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, TradeConfig.SPEC);

        MENUS.register(modEventBus);
        SOUNDS.register(modEventBus);
        
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
