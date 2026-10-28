package com.securetrade.mixin;

import com.securetrade.menu.TradeSessionManager;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    // Leaving the End replaces the player entity without a death. The offer has to be back in the
    // old entity's inventory before that inventory is copied to the new one.
    @Inject(method = "respawnPlayer", at = @At("HEAD"))
    private void securetrade$cancelTradeOnRespawn(ServerPlayerEntity player, boolean alive,
                                                  CallbackInfoReturnable<ServerPlayerEntity> cir) {
        TradeSessionManager.cancelForPlayer(player);
    }
}
