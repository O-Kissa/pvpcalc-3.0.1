package com.pvpcalc.combat;

import com.pvpcalc.core.DamageMath;
import com.pvpcalc.core.Report;
import com.pvpcalc.core.Settings;
import com.pvpcalc.hack.HackDetector;
import com.pvpcalc.tracking.MatchDedupe;
import com.pvpcalc.tracking.MatchManager;
import com.pvpcalc.tracking.PlayerFilter;
import com.pvpcalc.util.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla explosion damage (before armor, enchantments and difficulty scaling):
 *   q = 2*power; d = dist/q; impact = (1-d)*exposure; damage = floor((impact^2+impact)/2 * 7 * q + 1)
 *
 * The explosion packet in 1.21.11 only carries the center, so the source (End Crystal / TNT minecart / Creeper /
 * Respawn Anchor) is inferred from entity snapshots taken every tick (and the block-center position for anchors).
 */
public final class ExplosionCalculator {
    private record Snap(EntityType<?> type, Vec3d pos, double speed, boolean charged, long tick) {}
    private record Source(String label, double minPower, double maxPower) {}

    private static final Map<Integer, Snap> SNAPS = new HashMap<>();

    private ExplosionCalculator() {}

    public static void snapshot(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null || !Settings.bool("enabled")) {
            SNAPS.clear();
            return;
        }
        long now = w.getTime();
        Set<Integer> keep = new HashSet<>();
        for (Entity e : w.getEntities()) {
            EntityType<?> t = e.getType();
            if (t == EntityType.END_CRYSTAL || t == EntityType.TNT_MINECART || t == EntityType.CREEPER) {
                boolean charged = e instanceof CreeperEntity c && c.isCharged();
                Vec3d v = SpearCalculator.velocityOf(e);
                SNAPS.put(e.getId(), new Snap(t, new Vec3d(e.getX(), e.getY(), e.getZ()), Math.hypot(v.x, v.z), charged, now));
                keep.add(e.getId());
            }
        }
        // keep recently-removed entities for a few ticks: the removal packet may precede the explosion packet
        final long cutoff = now - 10;
        SNAPS.entrySet().removeIf(en -> !keep.contains(en.getKey()) && en.getValue().tick() < cutoff);
    }

    private static double finalDamage(Gear g, double raw) {
        double scaled = DamageMath.difficulty(raw, Settings.str("difficulty"));
        String eff = Settings.str("countEffects");
        boolean useEff = eff.equals("both") || eff.equals("opponent");
        return DamageMath.applyDefense(scaled, g.def(false, useEff, true), Settings.is("combatSystem", "1.8"));
    }

    public static void onExplosion(Vec3d center) {
        Hooks.explosion++;
        if (!Settings.bool("enabled") || center == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) return;
        String key = "expl:" + Math.round(center.x * 2) + ":" + Math.round(center.y * 2) + ":" + Math.round(center.z * 2);
        if (MatchDedupe.seen(key, 3)) return;
        HackDetector.onExplosion(center);

        Source src = classify(center);
        if (src == null) return;
        for (PlayerEntity p : world.getPlayers()) {
            if (p == mc.player || !PlayerFilter.real(mc, p)) continue;
            double hi = damage(world, center, src.maxPower(), p);
            if (hi <= 0.0) continue;
            double lo = damage(world, center, src.minPower(), p);
            Gear g = Gear.of(p);
            double fHi = finalDamage(g, hi), fLo = finalDamage(g, lo);
            String range = Math.abs(fHi - fLo) < 0.05 ? Report.num(fHi) : Report.num(fLo) + "-" + Report.num(fHi);
            MatchManager.logPotential(g.name, src.label() + ": " + range + " damage (raw " + Report.num(hi) + ")", fHi);
        }
    }

    private static Source classify(Vec3d center) {
        Integer bestId = null;
        double best = 2.25; // 1.5 blocks squared
        for (Map.Entry<Integer, Snap> en : SNAPS.entrySet()) {
            double d = en.getValue().pos().squaredDistanceTo(center);
            if (d < best) {
                best = d;
                bestId = en.getKey();
            }
        }
        if (bestId != null) {
            Snap s = SNAPS.remove(bestId);
            if (s.type() == EntityType.END_CRYSTAL) return new Source("End Crystal", 6.0, 6.0);
            if (s.type() == EntityType.TNT_MINECART) {
                return new Source("TNT Minecart", 4.0, 4.0 + 1.5 * Math.min(s.speed(), 5.0));
            }
            double p = s.charged() ? 6.0 : 3.0;
            return new Source("Creeper", p, p);
        }
        // Respawn Anchor / bed explode at an exact block center
        if (isHalf(center.x) && isHalf(center.y) && isHalf(center.z)) {
            return new Source("Respawn Anchor (inferred)", 5.0, 5.0);
        }
        return null;
    }

    private static boolean isHalf(double v) {
        return Math.abs((v - Math.floor(v)) - 0.5) < 1.0E-3;
    }

    public static double damage(ClientWorld world, Vec3d center, double power, Entity target) {
        double q = power * 2.0;
        double d = Math.sqrt(target.squaredDistanceTo(center)) / q;
        if (d > 1.0) return 0.0;
        double impact = (1.0 - d) * exposure(world, center, target);
        return (int) ((impact * impact + impact) / 2.0 * 7.0 * q + 1.0);
    }

    /** Fraction of box sample points with an unobstructed line to the explosion center (vanilla algorithm). */
    public static double exposure(ClientWorld world, Vec3d source, Entity entity) {
        Box box = entity.getBoundingBox();
        double dx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double dy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double dz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double ox = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0;
        double oz = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
        if (dx < 0.0 || dy < 0.0 || dz < 0.0) return 0.0;
        int hit = 0, total = 0;
        for (double x = 0.0; x <= 1.0; x += dx) {
            for (double y = 0.0; y <= 1.0; y += dy) {
                for (double z = 0.0; z <= 1.0; z += dz) {
                    Vec3d p = new Vec3d(
                            MathHelper.lerp(x, box.minX, box.maxX) + ox,
                            MathHelper.lerp(y, box.minY, box.maxY),
                            MathHelper.lerp(z, box.minZ, box.maxZ) + oz);
                    RaycastContext ctx = new RaycastContext(p, source, RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE, entity);
                    if (world.raycast(ctx).getType() == HitResult.Type.MISS) hit++;
                    total++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) hit / total;
    }

    public static void reset() {
        SNAPS.clear();
    }
}
