package com.securetrade;

import com.mojang.logging.LogUtils;
import com.securetrade.command.TradeCommand;
import com.securetrade.menu.TradeMenu;
import com.securetrade.menu.TradeMenuType;
import com.securetrade.network.TradeNetwork;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(SecureTradeMod.MODID)
public class SecureTradeMod {
    public static final String MODID = "securetrade";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<MenuType<TradeMenu>> TRADE_MENU = MENUS.register("trade_menu", () -> {
        MenuType<TradeMenu> type = new MenuType<>(TradeMenu::new);
        TradeMenuType.set(type);
        return type;
    });

    static {
        SecureTradeSounds.register((id, sound) -> SOUNDS.register(id.getPath(), () -> sound));
    }

    public SecureTradeMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TradeConfig.SPEC);

        MENUS.register(modEventBus);
        SOUNDS.register(modEventBus);

        TradeNetwork.register();

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        TradeCommand.register(event.getDispatcher());
    }
}
