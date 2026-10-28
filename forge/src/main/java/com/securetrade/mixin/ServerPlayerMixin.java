package com.securetrade.mixin;

import com.securetrade.escrow.TradeEscrow;
import com.securetrade.menu.TradeSessionManager;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void securetrade$saveEscrow(CompoundNBT output, CallbackInfo ci) {
        TradeEscrow.writePlayerData((ServerPlayerEntity) (Object) this, output);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void securetrade$loadEscrow(CompoundNBT input, CallbackInfo ci) {
        TradeEscrow.readPlayerData((ServerPlayerEntity) (Object) this, input);
    }

    // The respawned player is a new entity; the offer must go back before the death drops are made,
    // otherwise it would end up in the inventory of an entity that is about to be discarded.
    @Inject(method = "die", at = @At("HEAD"))
    private void securetrade$cancelTradeOnDeath(DamageSource source, CallbackInfo ci) {
        TradeSessionManager.cancelForPlayer((ServerPlayerEntity) (Object) this);
    }
}
