package com.securetrade.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerList.class)
public interface PlayerListInvoker {
    @Invoker("save")
    void securetrade$save(ServerPlayerEntity player);
}
