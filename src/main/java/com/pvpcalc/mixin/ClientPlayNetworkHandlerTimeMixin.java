package com.pvpcalc.mixin;

import com.pvpcalc.PvPCalcClient;
import com.pvpcalc.util.Hooks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Separate class so that a problem here can never disable the totem / explosion hooks. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerTimeMixin {
    /** The server sends a time update every 20 server ticks; the interval between them gives the TPS. */
    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"), require = 0)
    private void pvpcalc$onTime(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            Hooks.onTimeUpdate();
        } catch (Throwable t) {
            PvPCalcClient.LOGGER.error("PvPCalc TPS hook failed", t);
        }
    }
}
