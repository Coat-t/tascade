package com.coatt.tascade.mixin.access;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("LIVING_FLAGS")
    static TrackedData<Byte> tascade$livingFlags() {
        throw new AssertionError();
    }

    @Accessor("serverHeadYaw")
    double tascade$getServerHeadYaw();

    @Accessor("headTrackingIncrements")
    void tascade$setHeadTrackingIncrements(int increments);

    @Accessor("bodyTrackingIncrements")
    void tascade$setBodyTrackingIncrements(int increments);

    @Accessor("jumpingCooldown")
    int tascade$getJumpingCooldown();
}
