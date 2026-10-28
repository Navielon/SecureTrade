package com.securetrade.mixin;

import com.securetrade.escrow.TradeEscrow;
import com.securetrade.menu.TradeSessionManager;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void securetrade$saveEscrow(NbtCompound output, CallbackInfo ci) {
        TradeEscrow.writePlayerData((ServerPlayerEntity) (Object) this, output);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void securetrade$loadEscrow(NbtCompound input, CallbackInfo ci) {
        TradeEscrow.readPlayerData((ServerPlayerEntity) (Object) this, input);
    }

    // The respawned player is a new entity; the offer must go back before the death drops are made,
    // otherwise it would end up in the inventory of an entity that is about to be discarded.
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void securetrade$cancelTradeOnDeath(DamageSource source, CallbackInfo ci) {
        TradeSessionManager.cancelForPlayer((ServerPlayerEntity) (Object) this);
    }
}
