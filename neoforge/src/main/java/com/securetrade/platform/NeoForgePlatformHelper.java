package com.securetrade.platform;

import com.securetrade.TradeConfig;
import com.securetrade.TradeItemValidator;
import com.securetrade.network.TradeBlacklistWarningPacket;
import com.securetrade.network.TradeLockPacket;
import com.securetrade.network.TradeStateSyncPacket;
import com.securetrade.network.TradeXPChangePacket;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public void sendLockPacket(boolean locked) {
        PacketDistributor.sendToServer(new TradeLockPacket(locked));
    }

    @Override
    public void sendXPChangePacket(long xpPoints) {
        PacketDistributor.sendToServer(new TradeXPChangePacket(xpPoints));
    }

    @Override
    public void sendStateSync(ServerPlayer player, boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName) {
        PacketDistributor.sendToPlayer(player, new TradeStateSyncPacket(myLock, otherLock, countdownSeconds, myXP, otherXP, otherTotalXP, partnerName));
    }

    @Override
    public void sendBlacklistWarning(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new TradeBlacklistWarningPacket());
    }

    @Override
    public boolean containsPlatformContainerItems(ItemStack stack, List<String> blacklist, int depth) {
        return containsNeoForgeItemHandlerItems(stack, blacklist, depth)
                || containsSophisticatedBackpackItems(stack, blacklist, depth);
    }

    private static boolean containsNeoForgeItemHandlerItems(ItemStack stack, List<String> blacklist, int depth) {
        try {
            Class<?> capabilitiesClass = Class.forName("net.neoforged.neoforge.capabilities.Capabilities$ItemHandler");
            Class<?> itemCapabilityClass = Class.forName("net.neoforged.neoforge.capabilities.ItemCapability");
            Field itemHandlerField = capabilitiesClass.getField("ITEM");
            Object itemHandlerCapability = itemHandlerField.get(null);
            Method getCapability = stack.getClass().getMethod("getCapability", itemCapabilityClass);
            Object handler = getCapability.invoke(stack, itemHandlerCapability);
            return TradeItemValidator.containsHandlerItems(handler, blacklist, depth);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean containsSophisticatedBackpackItems(ItemStack stack, List<String> blacklist, int depth) {
        try {
            Class<?> wrapperClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
            Method fromExistingData = wrapperClass.getMethod("fromExistingData", ItemStack.class);
            Object optionalWrapper = fromExistingData.invoke(null, stack);
            if (!(optionalWrapper instanceof Optional<?> optional) || optional.isEmpty()) {
                return false;
            }

            Object wrapper = optional.get();
            Method getInventoryHandler = wrapper.getClass().getMethod("getInventoryHandler");
            Object handler = getInventoryHandler.invoke(wrapper);
            return TradeItemValidator.containsHandlerItems(handler, blacklist, depth);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public double getMaxTradeDistance() {
        return TradeConfig.MAX_TRADE_DISTANCE.get();
    }

    @Override
    public int getRequestTimeoutSeconds() {
        return TradeConfig.REQUEST_TIMEOUT_SECONDS.get();
    }

    @Override
    public int getTradeCooldownSeconds() {
        return TradeConfig.TRADE_COOLDOWN_SECONDS.get();
    }

    @Override
    public int getCountdownSeconds() {
        return TradeConfig.COUNTDOWN_SECONDS.get();
    }

    @Override
    public boolean isLoggingEnabled() {
        return TradeConfig.ENABLE_TRADE_LOGGING.get();
    }

    @Override
    public java.util.List<String> getBlacklistedItems() {
        return java.util.List.copyOf(TradeConfig.BLACKLISTED_ITEMS.get());
    }

    @Override
    public java.util.List<String> getAllowedDimensions() {
        return java.util.List.copyOf(TradeConfig.ALLOWED_DIMENSIONS.get());
    }

    @Override
    public java.util.List<String> getBlockedDimensions() {
        return java.util.List.copyOf(TradeConfig.BLOCKED_DIMENSIONS.get());
    }

    @Override
    public int getMaxHistoryEntries() {
        return TradeConfig.MAX_HISTORY_ENTRIES.get();
    }
}
