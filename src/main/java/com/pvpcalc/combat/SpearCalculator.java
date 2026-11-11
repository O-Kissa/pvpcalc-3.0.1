package com.pvpcalc.combat;

import com.pvpcalc.core.Settings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Spear charge damage.
 *  - native: floor(tierMultiplier * relativeSpeed[blocks/s] along the view vector) + enchant/effect bonuses
 *  - custom "Minemen-style": (attack damage + enchants) * (1 + factor * |player velocity [blocks/tick]|)
 * The Minemen formula is not public; the factor is configurable (config.json: minemenSpearVelocityFactor).
 */
public final class SpearCalculator {
    public record Result(double nativeDamage, double customDamage, double relativeSpeed, double velocityMagnitude) {}

    private static final Map<Integer, Vec3d> LAST_POS = new HashMap<>();
    private static final Map<Integer, Vec3d> VELOCITY = new HashMap<>();

    private SpearCalculator() {}

    public static boolean isSpear(ItemStack s) {
        return DamageCalculator.kindOf(s) == DamageCalculator.Kind.SPEAR;
    }

    /** Charge damage multipliers per tier (approximate wiki values). */
    public static double tierMultiplier(ItemStack s) {
        String p = DamageCalculator.itemPath(s);
        if (p.startsWith("netherite")) return 1.2;
        if (p.startsWith("diamond")) return 1.075;
        if (p.startsWith("iron")) return 0.95;
        if (p.startsWith("stone") || p.startsWith("copper")) return 0.82;
        return 0.7; // wooden / golden
    }

    /** Tracks per-tick velocity (blocks/tick) of all living entities from position deltas. */
    public static void trackTick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null) {
            reset();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Entity e : w.getEntities()) {
            if (!(e instanceof LivingEntity)) continue;
            Vec3d now = new Vec3d(e.getX(), e.getY(), e.getZ());
            Vec3d last = LAST_POS.put(e.getId(), now);
            if (last != null) VELOCITY.put(e.getId(), now.subtract(last));
            seen.add(e.getId());
        }
        LAST_POS.keySet().retainAll(seen);
        VELOCITY.keySet().retainAll(seen);
    }

    public static Vec3d velocityOf(Entity e) {
        return VELOCITY.getOrDefault(e.getId(), Vec3d.ZERO);
    }

    public static Result charge(ClientPlayerEntity p, LivingEntity target, ItemStack spear) {
        Vec3d look = p.getRotationVec(1.0F).normalize();
        Vec3d self = p.getVelocity(); // blocks/tick
        double rel = Math.max(0.0, self.subtract(velocityOf(target)).dotProduct(look) * 20.0);

        double extras = DamageCalculator.enchantBonus(spear, target, false) + DamageCalculator.effectBonus(p);
        double nat = Math.max(0.0, Math.floor(tierMultiplier(spear) * rel) + extras);

        double velMag = self.length();
        double base = p.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) + DamageCalculator.enchantBonus(spear, target, false);
        double custom = base * (1.0 + Settings.dbl("spearFactor") * velMag);
        return new Result(nat, custom, rel, velMag);
    }

    public static void reset() {
        LAST_POS.clear();
        VELOCITY.clear();
    }
}
