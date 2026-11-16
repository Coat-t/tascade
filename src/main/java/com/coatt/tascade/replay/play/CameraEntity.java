package com.coatt.tascade.replay.play;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.StatHandler;
import net.minecraft.tag.Tag;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public final class CameraEntity extends ClientPlayerEntity {
    public boolean pov;
    public boolean runnerSneaking;
    public boolean runnerOnFire;
    public boolean runnerUsingItem;
    public Hand runnerActiveHand = Hand.MAIN_HAND;
    public int runnerItemUseTimeLeft;
    public float runnerAttackCooldown = 1f;

    public CameraEntity(MinecraftClient client, ClientWorld world, ClientPlayNetworkHandler networkHandler,
                        StatHandler stats, ClientRecipeBook recipeBook) {
        super(client, world, networkHandler, stats, recipeBook, false, false);
        this.noClip = true;
    }

    @Override
    public void tick() {
        this.age++;
        this.lastHandSwingProgress = this.handSwingProgress;
        this.tickHandSwing();
        this.getItemCooldownManager().update();

        this.prevHorizontalSpeed = this.horizontalSpeed;
        this.prevStrideDistance = this.strideDistance;
        double dx = this.getX() - this.lastTickX;
        double dz = this.getZ() - this.lastTickZ;
        float moved = MathHelper.sqrt(dx * dx + dz * dz);
        this.horizontalSpeed += moved * 0.6F;
        float stride = pov && this.onGround ? Math.min(0.1F, moved) : 0F;
        this.strideDistance += (stride - this.strideDistance) * 0.4F;
        this.lastTickX = this.getX();
        this.lastTickZ = this.getZ();

        this.lastRenderYaw = this.renderYaw;
        this.lastRenderPitch = this.renderPitch;
        this.renderPitch = this.renderPitch + (this.pitch - this.renderPitch) * 0.5F;
        this.renderYaw = this.renderYaw + (this.yaw - this.renderYaw) * 0.5F;
    }

    private double lastTickX;
    private double lastTickZ;

    @Override
    public void tickMovement() {
    }

    @Override
    public void changeLookDirection(double cursorDeltaX, double cursorDeltaY) {
        if (!pov) super.changeLookDirection(cursorDeltaX, cursorDeltaY);
    }

    @Override
    public boolean isSpectator() {
        return !pov;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public boolean isSneaking() {
        return pov && runnerSneaking;
    }

    @Override
    public boolean isInSneakingPose() {
        return pov && runnerSneaking;
    }

    @Override
    public boolean isOnFire() {
        return pov && runnerOnFire;
    }

    @Override
    public boolean isSubmergedIn(Tag<Fluid> tag) {
        if (!pov) return false;
        double eyeY = this.getEyeY() - 0.11111111F;
        BlockPos pos = new BlockPos(this.getX(), eyeY, this.getZ());
        FluidState fluid = this.world.getFluidState(pos);
        return fluid.isIn(tag) && pos.getY() + fluid.getHeight(this.world, pos) > eyeY;
    }

    @Override
    public float getAttackCooldownProgress(float baseTime) {
        return pov ? runnerAttackCooldown : 1f;
    }

    @Override
    public boolean isUsingItem() {
        return pov && runnerUsingItem;
    }

    @Override
    public Hand getActiveHand() {
        return runnerActiveHand;
    }

    @Override
    public ItemStack getActiveItem() {
        return isUsingItem() ? this.getStackInHand(runnerActiveHand) : ItemStack.EMPTY;
    }

    @Override
    public int getItemUseTimeLeft() {
        return isUsingItem() ? runnerItemUseTimeLeft : 0;
    }

    @Override
    public boolean showsDeathScreen() {
        return false;
    }

    @Override
    public void requestRespawn() {
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void pushAwayFrom(Entity entity) {
    }

    @Override
    public boolean shouldRenderName() {
        return false;
    }

    public void place(double x, double y, double z, float yaw, float pitch) {
        this.setPos(x, y, z);
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.lastRenderX = x;
        this.lastRenderY = y;
        this.lastRenderZ = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.prevYaw = yaw;
        this.prevPitch = pitch;
        this.headYaw = yaw;
        this.prevHeadYaw = yaw;
        this.bodyYaw = yaw;
        this.prevBodyYaw = yaw;
    }
}
