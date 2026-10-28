package com.securetrade.mixin;

import com.securetrade.menu.TradeSessionManager;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    // Leaving the End replaces the player entity without a death. The offer has to be back in the
    // old entity's inventory before that inventory is copied to the new one.
    @Inject(method = "respawn", at = @At("HEAD"))
    private void securetrade$cancelTradeOnRespawn(ServerPlayerEntity player, boolean keepAllPlayerData,
                                                  CallbackInfoReturnable<ServerPlayerEntity> cir) {
        TradeSessionManager.cancelForPlayer(player);
    }
}
