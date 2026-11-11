package com.pvpcalc.mixin;

import com.pvpcalc.PvPCalcClient;
import com.pvpcalc.combat.ExplosionCalculator;
import com.pvpcalc.tracking.TotemTracker;
import com.pvpcalc.util.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All injections use require = 0: if a target is missing in some version the game still starts. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onEntityStatus", at = @At("HEAD"), require = 0)
    private void pvpcalc$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        // Packet handlers run first on the netty thread, then again on the client thread.
        if (!MinecraftClient.getInstance().isOnThread()) return;
        try {
            TotemTracker.onEntityStatus(packet);
        } catch (Throwable t) {
            PvPCalcClient.LOGGER.error("PvPCalc totem hook failed", t);
        }
    }

    @Inject(method = "onExplosion", at = @At("HEAD"), require = 0)
    private void pvpcalc$onExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        if (!MinecraftClient.getInstance().isOnThread()) return;
        try {
            ExplosionCalculator.onExplosion(packet.center());
        } catch (Throwable t) {
            PvPCalcClient.LOGGER.error("PvPCalc explosion hook failed", t);
        }
    }
}
