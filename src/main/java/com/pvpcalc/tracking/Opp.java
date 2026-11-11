package com.pvpcalc.tracking;

import com.pvpcalc.core.Settings;
import com.pvpcalc.util.Clock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/** Which player is "the opponent": the one I look at, else the one I hit last, else the nearest real player. */
public final class Opp {
    private Opp() {}

    public static PlayerEntity looked(MinecraftClient mc, double max) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null) return null;
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(max));
        Box search = p.getBoundingBox().stretch(look.multiply(max)).expand(1.0);
        PlayerEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : w.getOtherEntities(p, search, x -> x instanceof PlayerEntity && x.isAlive())) {
            PlayerEntity pe = (PlayerEntity) e;
            if (!PlayerFilter.real(mc, pe)) continue;
            Box b = e.getBoundingBox().expand(e.getTargetingMargin());
            Optional<Vec3d> hit = b.raycast(eye, end);
            if (hit.isPresent() || b.contains(eye)) {
                double d = hit.map(eye::squaredDistanceTo).orElse(0.0);
                if (d < bd) { bd = d; best = pe; }
            }
        }
        return best;
    }

    public static PlayerEntity nearest(MinecraftClient mc, double radius) {
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.world == null) return null;
        PlayerEntity best = null;
        double bd = radius * radius;
        for (PlayerEntity pe : mc.world.getPlayers()) {
            if (!PlayerFilter.real(mc, pe)) continue;
            double d = pe.squaredDistanceTo(me);
            if (d < bd) { bd = d; best = pe; }
        }
        return best;
    }

    public static PlayerEntity byUuid(MinecraftClient mc, String uuid) {
        if (mc.world == null || uuid == null) return null;
        for (PlayerEntity pe : mc.world.getPlayers()) if (pe.getUuid().toString().equals(uuid)) return pe;
        return null;
    }

    public static PlayerEntity byName(MinecraftClient mc, String name) {
        if (mc.world == null || name == null) return null;
        for (PlayerEntity pe : mc.world.getPlayers()) {
            if (pe.getName().getString().equalsIgnoreCase(name.trim()) && pe != mc.player) return pe;
        }
        return null;
    }

    /** Looked-at player, else last hit (10 s), else nearest within 16 blocks. */
    public static PlayerEntity opponent(MinecraftClient mc) {
        PlayerEntity l = looked(mc, 30.0);
        if (l != null) return l;
        if (Clock.tick() - MatchManager.lastHitTick() < 200) {
            PlayerEntity last = byUuid(mc, MatchManager.lastTargetUuid());
            if (last != null && last.isAlive()) return last;
        }
        return nearest(mc, 16.0);
    }

    /** Target for damage calculation according to the "target" setting (me, or the opponent). */
    public static LivingEntity target(MinecraftClient mc) {
        if (Settings.is("target", "self")) return mc.player;
        return opponent(mc);
    }
}
