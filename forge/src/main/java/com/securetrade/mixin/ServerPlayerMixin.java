package com.securetrade.mixin;

import com.securetrade.escrow.TradeEscrow;
import com.securetrade.menu.TradeSessionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void securetrade$saveEscrow(CompoundTag output, CallbackInfo ci) {
        TradeEscrow.writePlayerData((ServerPlayer) (Object) this, output);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void securetrade$loadEscrow(CompoundTag input, CallbackInfo ci) {
        TradeEscrow.readPlayerData((ServerPlayer) (Object) this, input);
    }

    // The respawned player is a new entity; the offer must go back before the death drops are made,
    // otherwise it would end up in the inventory of an entity that is about to be discarded.
    @Inject(method = "die", at = @At("HEAD"))
    private void securetrade$cancelTradeOnDeath(DamageSource source, CallbackInfo ci) {
        TradeSessionManager.cancelForPlayer((ServerPlayer) (Object) this);
    }
}
