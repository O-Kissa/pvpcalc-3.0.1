package com.pvpcalc.mixin;

import com.pvpcalc.combat.WeaponHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the local player's melee attack (runs before vanilla resets the cooldown). */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), require = 0)
    private void pvpcalc$onAttack(Entity target, CallbackInfo ci) {
        // Only the local client player; integrated-server ServerPlayerEntity is ignored.
        if ((Object) this instanceof ClientPlayerEntity self) {
            try {
                WeaponHandler.onPlayerAttack(self, target);
            } catch (Throwable t) {
                com.pvpcalc.PvPCalcClient.LOGGER.error("PvPCalc attack hook failed", t);
            }
        }
    }
}
