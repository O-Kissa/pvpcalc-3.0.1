package com.pvpcalc.tracking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.GameMode;

import java.util.Locale;

/** Only real players count: NPCs (not in the tab list / non-random UUID), spectators and bots are ignored. */
public final class PlayerFilter {
    private PlayerFilter() {}

    public static boolean real(MinecraftClient mc, PlayerEntity p) {
        if (mc == null || mc.player == null || p == null || p == mc.player) return false;
        if (p.isSpectator()) return false;
        ClientPlayNetworkHandler h = mc.getNetworkHandler();
        if (h == null) return false;
        PlayerListEntry e = h.getPlayerListEntry(p.getUuid());
        if (e == null) return false;                       // NPCs are normally not in the player list
        if (e.getGameMode() == GameMode.SPECTATOR) return false;
        if (p.getUuid().version() != 4) return false;      // plugin NPCs usually use another UUID version
        String n = p.getName().getString().toLowerCase(Locale.ROOT);
        return !(n.startsWith("npc_") || n.startsWith("[npc") || n.startsWith("bot_") || n.contains("citizen"));
    }

    public static int ping(MinecraftClient mc, PlayerEntity p) {
        try {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            return e == null ? -1 : e.getLatency();
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    public static boolean creativeOrSpectator(MinecraftClient mc, PlayerEntity p) {
        try {
            PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            return e != null && (e.getGameMode() == GameMode.CREATIVE || e.getGameMode() == GameMode.SPECTATOR);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
