package com.coatt.tascade.mixin.access;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayerEntity.class)
public interface ServerPlayerEntityAccessor {
    @Accessor("joinInvulnerabilityTicks")
    void tascade$setJoinInvulnerabilityTicks(int ticks);
}
