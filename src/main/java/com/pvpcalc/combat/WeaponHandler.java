package com.pvpcalc.combat;

import com.pvpcalc.core.Num;
import com.pvpcalc.core.Settings;
import com.pvpcalc.tracking.MatchDedupe;
import com.pvpcalc.tracking.MatchManager;
import com.pvpcalc.tracking.Opp;
import com.pvpcalc.tracking.PlayerFilter;
import com.pvpcalc.util.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Melee hits (from the mixin), swings, bow / crossbow shots and spear charges. Real players only. */
public final class WeaponHandler {
    private static final double SPEAR_MIN_RELATIVE_SPEED = 4.6; // blocks/s, approximate
    private static final int SPEAR_MIN_USE_TICKS = 8;
    private static final double SPEAR_REACH = 4.5;

    private record CrossbowSnap(boolean firework, int stars, int arrows) {}

    private static boolean bowDrawing;
    private static int bowTicks, bowSlot;
    private static ItemStack bowStack = ItemStack.EMPTY;
    private static CrossbowSnap prevMain, prevOff;
    private static final Set<Integer> CHARGE_HITS = new HashSet<>();
    private static float prevSwing;

    private WeaponHandler() {}

    private static String oppName(MinecraftClient mc) {
        PlayerEntity o = Opp.opponent(mc);
        return o == null ? "" : o.getName().getString();
    }

    // ---------------- melee ----------------

    public static void onPlayerAttack(ClientPlayerEntity self, Entity target) {
        Hooks.attack++;
        if (!Settings.bool("enabled") || target == null || target == self) return;
        if (!(target instanceof PlayerEntity pe)) return;               // mobs are always ignored
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!PlayerFilter.real(mc, pe)) return;                         // NPCs, bots, spectators ignored
        if (MatchDedupe.seen("atk:" + target.getId(), 1)) return;
        DamageCalculator.Result r = DamageCalculator.melee(self, pe);
        MatchManager.onMyHit(pe, r.details(), r.dmg(), r.crit());
    }

    // ---------------- per tick ----------------

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ClientWorld world = mc.world;
        if (p == null || world == null || !Settings.bool("enabled")) {
            reset();
            return;
        }
        tickSwing(mc, p);
        tickBow(mc, p);
        tickCrossbow(mc, p);
        tickSpear(mc, p, world);
    }

    private static void tickSwing(MinecraftClient mc, ClientPlayerEntity p) {
        float prog = p.getHandSwingProgress(1.0F);
        boolean started = prog > 0.0F && (prevSwing <= 0.0F || prog < prevSwing - 0.001F);
        prevSwing = prog;
        if (started && DamageCalculator.kindOf(p.getMainHandStack()) != DamageCalculator.Kind.OTHER
                && Opp.nearest(mc, 6.0) != null) {
            MatchManager.onSwing();
        }
    }

    private static void tickBow(MinecraftClient mc, ClientPlayerEntity p) {
        ItemStack active = p.isUsingItem() ? p.getActiveItem() : ItemStack.EMPTY;
        if (active.isOf(Items.BOW)) {
            bowDrawing = true;
            bowTicks = p.getItemUseTime();
            bowSlot = p.getInventory().getSelectedSlot();
            bowStack = active.copy();
        } else if (bowDrawing) {
            bowDrawing = false;
            boolean holding = p.getMainHandStack().isOf(Items.BOW) || p.getOffHandStack().isOf(Items.BOW);
            if (holding && p.getInventory().getSelectedSlot() == bowSlot) logBow(mc, bowTicks + 1, bowStack);
        }
    }

    private static void logBow(MinecraftClient mc, int useTicks, ItemStack bow) {
        double f = useTicks / 20.0;
        double pull = Math.min(1.0, (f * f + f * 2.0) / 3.0);
        if (pull < 0.1) return;
        int power = DamageCalculator.enchantLevel(bow, Enchantments.POWER);
        double baseDamage = 2.0 + (power > 0 ? power * 0.5 + 0.5 : 0.0);
        int i = (int) Math.ceil(pull * 3.0 * baseDamage);
        boolean crit = pull >= 1.0;
        double hi = crit ? i + i / 2 + 1 : i;
        String dmg = crit ? Num.fmt(i, 1) + "-" + Num.fmt(hi, 1) : Num.fmt(i, 1);
        MatchManager.logPotential(oppName(mc), "Bow shot: " + dmg + " damage (draw " + Math.round(pull * 100) + "%, "
                + useTicks + " ticks" + (power > 0 ? ", Power " + power : "") + (crit ? ", critical" : "") + ")", i);
    }

    private static CrossbowSnap snap(ItemStack s) {
        if (!s.isOf(Items.CROSSBOW)) return null;
        ChargedProjectilesComponent c = s.get(DataComponentTypes.CHARGED_PROJECTILES);
        if (c == null || c.isEmpty()) return null;
        List<ItemStack> list = c.getProjectiles();
        boolean fw = false;
        int stars = 0;
        for (ItemStack st : list) {
            if (st.isOf(Items.FIREWORK_ROCKET)) {
                fw = true;
                FireworksComponent comp = st.get(DataComponentTypes.FIREWORKS);
                stars = comp == null ? 0 : comp.explosions().size();
            }
        }
        return new CrossbowSnap(fw, stars, list.size());
    }

    private static void tickCrossbow(MinecraftClient mc, ClientPlayerEntity p) {
        CrossbowSnap main = snap(p.getMainHandStack());
        CrossbowSnap off = snap(p.getOffHandStack());
        if (prevMain != null && main == null && p.getMainHandStack().isOf(Items.CROSSBOW)) logCrossbow(mc, prevMain);
        if (prevOff != null && off == null && p.getOffHandStack().isOf(Items.CROSSBOW)) logCrossbow(mc, prevOff);
        prevMain = main;
        prevOff = off;
    }

    private static void logCrossbow(MinecraftClient mc, CrossbowSnap s) {
        if (s.firework()) {
            double dmg = s.stars() > 0 ? 5.0 + 2.0 * s.stars() : 0.0;
            MatchManager.logPotential(oppName(mc), "Crossbow firework: " + Num.fmt(dmg, 1) + " damage (" + s.stars() + " stars)", dmg);
        } else {
            int i = (int) Math.ceil(3.15 * 2.0);
            MatchManager.logPotential(oppName(mc), "Crossbow arrow: " + Num.fmt(i, 1) + "-" + Num.fmt(i + i / 2 + 1, 1) + " damage (critical)", i);
        }
    }

    private static void tickSpear(MinecraftClient mc, ClientPlayerEntity p, ClientWorld world) {
        ItemStack active = p.isUsingItem() ? p.getActiveItem() : ItemStack.EMPTY;
        if (!SpearCalculator.isSpear(active)) {
            CHARGE_HITS.clear();
            return;
        }
        if (p.getItemUseTime() < SPEAR_MIN_USE_TICKS) return;
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(SPEAR_REACH));
        Box search = p.getBoundingBox().stretch(look.multiply(SPEAR_REACH)).expand(1.0);
        for (Entity e : world.getOtherEntities(p, search, x -> x instanceof PlayerEntity && x.isAlive() && !x.isSpectator())) {
            PlayerEntity pe = (PlayerEntity) e;
            if (!PlayerFilter.real(mc, pe) || CHARGE_HITS.contains(e.getId())) continue;
            Box b = e.getBoundingBox().expand(e.getTargetingMargin());
            if (!b.contains(eye) && b.raycast(eye, end).isEmpty()) continue;
            SpearCalculator.Result r = SpearCalculator.charge(p, pe, active);
            if (r.relativeSpeed() < SPEAR_MIN_RELATIVE_SPEED) continue;
            CHARGE_HITS.add(e.getId());
            if (DamageCalculator.isShieldBlocking(pe)) {
                MatchManager.onMyHit(pe, "Spear charge blocked by shield: 0.0 damage", 0.0, false);
            } else {
                MatchManager.onMyHit(pe, "Spear charge: native " + Num.fmt(r.nativeDamage(), 1) + " / Minemen-style " + Num.fmt(r.customDamage(), 1)
                        + " damage (rel. speed " + Num.fmt(r.relativeSpeed(), 1) + " b/s, velocity "
                        + String.format(Locale.ROOT, "%.2f", r.velocityMagnitude()) + " b/tick)", r.nativeDamage(), false);
            }
        }
    }

    public static void reset() {
        bowDrawing = false;
        bowStack = ItemStack.EMPTY;
        prevMain = null;
        prevOff = null;
        prevSwing = 0.0F;
        CHARGE_HITS.clear();
    }
}
