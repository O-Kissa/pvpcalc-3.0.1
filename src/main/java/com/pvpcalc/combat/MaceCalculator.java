package com.pvpcalc.combat;

import com.pvpcalc.core.DamageMath;
import com.pvpcalc.core.Settings;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Native 1.21.11 mace smash bonus and the "how far must I fall" solver. */
public final class MaceCalculator {
    private MaceCalculator() {}

    public static double fallDistance(PlayerEntity p) {
        return p.fallDistance;
    }

    /** Vanilla: fallDistance > 1.5 and not gliding. */
    public static boolean canSmash(PlayerEntity p) {
        return fallDistance(p) > 1.5 && !p.isGliding();
    }

    public static double smashBonus(PlayerEntity p, ItemStack mace) {
        if (!canSmash(p)) return 0.0;
        return DamageMath.maceBonus(fallDistance(p), DamageCalculator.enchantLevel(mace, Enchantments.DENSITY));
    }

    /**
     * Blocks I must fall to kill (or pop) this target with the mace in my hand, using their real armor, Protection,
     * Resistance, absorption and my Density/Breach/Strength. NaN when no mace is held or the kill is impossible.
     */
    public static double fallNeeded(PlayerEntity me, LivingEntity target) {
        ItemStack w = me.getMainHandStack();
        if (DamageCalculator.kindOf(w) != DamageCalculator.Kind.MACE) return Double.NaN;
        Gear tg = Gear.of(target);
        boolean blocked = tg.blocking || Settings.bool("assumeShield");
        if (blocked) return Double.NaN;
        DamageMath.Def def = tg.def(false, !Settings.is("countEffects", "own") && !Settings.is("countEffects", "ignore"), false);
        def.breach = DamageCalculator.enchantLevel(w, Enchantments.BREACH);
        int density = DamageCalculator.enchantLevel(w, Enchantments.DENSITY);
        int str = DamageCalculator.strengthLevel(me), weak = DamageCalculator.weaknessLevel(me);
        double base = Math.max(me.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) - DamageCalculator.effectBonus(me), 6.0);
        double ench = DamageCalculator.enchantBonus(w, target, false);
        boolean crit = !Settings.is("critHandling", "off");
        return DamageMath.fallNeeded(h -> {
            DamageMath.Melee m = new DamageMath.Melee();
            m.base = base; m.strength = str; m.weakness = weak; m.charge = 1.0; m.crit = crit;
            m.ench = ench; m.smash = DamageMath.maceBonus(h, density);
            return DamageMath.applyDefense(DamageMath.meleeRaw(m), def, false);
        }, tg.health + tg.absorption);
    }
}
