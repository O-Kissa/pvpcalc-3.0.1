package com.pvpcalc.tracking;

import com.pvpcalc.core.Settings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Golden apples / potions (use-time heuristic: the server does not announce them) and thrown pearls,
 * XP bottles and splash potions (owner of the projectile entity).
 */
public final class ConsumableTracker {
    private static final int MIN_TICKS = 30; // eating / drinking takes 32 ticks

    private record Using(String kind, int lastUseTime) {}

    private static final Map<Integer, Using> USING = new HashMap<>();
    private static final Set<Integer> SEEN = new HashSet<>();
    private static final Map<Integer, Integer> PENDING = new HashMap<>();
    private static boolean meWasEating;

    private ConsumableTracker() {}

    private static String kindOf(ItemStack a) {
        if (a.isOf(Items.GOLDEN_APPLE)) return "gapple";
        if (a.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return "egapple";
        if (a.isOf(Items.POTION)) return "potion";
        return null;
    }

    public static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null || mc.player == null || !Settings.bool("enabled")) {
            reset();
            return;
        }
        Set<Integer> present = new HashSet<>();
        for (PlayerEntity p : w.getPlayers()) {
            boolean me = p == mc.player;
            if (!me && !PlayerFilter.real(mc, p)) continue;
            present.add(p.getId());
            ItemStack active = p.isUsingItem() ? p.getActiveItem() : ItemStack.EMPTY;
            String k = kindOf(active);
            Using prev = USING.get(p.getId());
            if (k != null) {
                USING.put(p.getId(), new Using(k, p.getItemUseTime()));
                if (me && (k.equals("gapple") || k.equals("egapple")) && !meWasEating) {
                    meWasEating = true;
                    MatchManager.onMyEatStart();
                }
            } else if (prev != null) {
                USING.remove(p.getId());
                if (prev.lastUseTime() >= MIN_TICKS && !MatchDedupe.seen("use:" + p.getId(), 40)) {
                    MatchManager.onConsume(p, prev.kind());
                }
            }
            if (me && k == null) meWasEating = false;
        }
        USING.keySet().retainAll(present);

        Set<Integer> alive = new HashSet<>();
        for (Entity e : w.getEntities()) {
            EntityType<?> t = e.getType();
            String kind = t == EntityType.ENDER_PEARL ? "pearl" : (t == EntityType.EXPERIENCE_BOTTLE ? "xp"
                    : (t == EntityType.SPLASH_POTION ? "potion" : null));
            if (kind == null) continue;
            alive.add(e.getId());
            if (SEEN.contains(e.getId())) continue;
            Entity owner = e instanceof ProjectileEntity pe ? pe.getOwner() : null;
            if (owner instanceof PlayerEntity po) {
                SEEN.add(e.getId());
                PENDING.remove(e.getId());
                if (po == mc.player || PlayerFilter.real(mc, po)) MatchManager.onConsume(po, kind);
            } else {
                int tries = PENDING.merge(e.getId(), 1, Integer::sum);
                if (tries > 5) { SEEN.add(e.getId()); PENDING.remove(e.getId()); } // owner never became known
            }
        }
        SEEN.retainAll(alive);
        PENDING.keySet().retainAll(alive);
    }

    public static void reset() {
        USING.clear();
        SEEN.clear();
        PENDING.clear();
        meWasEating = false;
    }
}
