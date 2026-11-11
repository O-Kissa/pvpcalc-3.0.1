package com.pvpcalc.tracking;

import com.pvpcalc.core.Settings;
import com.pvpcalc.util.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public final class TotemTracker {
    private TotemTracker() {}

    public static void onEntityStatus(EntityStatusS2CPacket packet) {
        Hooks.status++;
        if (!Settings.bool("enabled")) return;
        if (packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) return;
        Entity e = packet.getEntity(world);
        if (!(e instanceof PlayerEntity p)) return;
        if (p == mc.player) {
            if (!MatchDedupe.seen("mytotem", 10)) MatchManager.onMyPop();
            return;
        }
        if (!PlayerFilter.real(mc, p)) return;
        if (MatchDedupe.seen("totem:" + p.getId(), 10)) return;
        MatchManager.onTotemPop(p);
    }
}
