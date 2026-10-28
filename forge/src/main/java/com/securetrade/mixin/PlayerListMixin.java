package com.securetrade.mixin;

import com.securetrade.menu.TradeSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    // Leaving the End replaces the player entity without a death. The offer has to be back in the
    // old entity's inventory before that inventory is copied to the new one.
    @Inject(method = "respawn", at = @At("HEAD"))
    private void securetrade$cancelTradeOnRespawn(ServerPlayer player, boolean keepAllPlayerData,
                                                  CallbackInfoReturnable<ServerPlayer> cir) {
        TradeSessionManager.cancelForPlayer(player);
    }
}
