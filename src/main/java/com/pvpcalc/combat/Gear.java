package com.pvpcalc.combat;

import com.pvpcalc.core.DamageMath;
import com.pvpcalc.core.Predict;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Snapshot of everything visible about a living entity's gear and state (own inventory or another player). */
public final class Gear {
    public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final String[] PIECE_KEYS = {"head", "chest", "legs", "feet"};

    public final LivingEntity entity;
    public final String name;
    public final ItemStack weapon;
    public double armor, toughness, epf, epfBlast, health, maxHealth, absorption;
    public int resist, totemsHeld, totemsTotal;
    public boolean blocking, hasMending;

    private Gear(LivingEntity e) {
        entity = e;
        name = e.getName().getString();
        weapon = e.getMainHandStack();
        health = Math.max(0.0, e.getHealth());
        maxHealth = Math.max(1.0, e.getMaxHealth());
        absorption = Math.max(0.0, e.getAbsorptionAmount());
        blocking = e.isBlocking();
        armor = e.getAttributeValue(EntityAttributes.ARMOR);
        toughness = e.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        boolean anyArmor = false;
        double fbArmor = 0, fbTough = 0;
        for (int i = 0; i < ARMOR.length; i++) {
            ItemStack s = e.getEquippedStack(ARMOR[i]);
            if (s.isEmpty()) continue;
            anyArmor = true;
            int prot = DamageCalculator.enchantLevel(s, Enchantments.PROTECTION);
            int blast = DamageCalculator.enchantLevel(s, Enchantments.BLAST_PROTECTION);
            epf += prot;
            epfBlast += prot + 2.0 * blast;
            if (DamageCalculator.enchantLevel(s, Enchantments.MENDING) > 0) hasMending = true;
            String path = DamageCalculator.itemPath(s);
            fbArmor += armorPoints(path, i);
            if (path.startsWith("netherite")) fbTough += 3; else if (path.startsWith("diamond")) fbTough += 2;
        }
        if (armor <= 0.0 && anyArmor) { // attributes not synced: estimate from the armor items
            armor = fbArmor;
            toughness = fbTough;
        }
        if (e.hasStatusEffect(StatusEffects.RESISTANCE)) resist = e.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1;
        totemsHeld = (e.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) ? 1 : 0) + (e.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) ? 1 : 0);
        totemsTotal = totemsHeld;
        if (e instanceof ClientPlayerEntity) {
            PlayerEntity p = (PlayerEntity) e;
            int n = 0;
            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack st = p.getInventory().getStack(i);
                if (st.isOf(Items.TOTEM_OF_UNDYING)) n += st.getCount();
            }
            totemsTotal = Math.max(totemsHeld, n);
        }
    }

    public static Gear of(LivingEntity e) {
        return new Gear(e);
    }

    private static double armorPoints(String path, int slot) {
        int[] t;
        if (path.startsWith("netherite") || path.startsWith("diamond")) t = new int[]{3, 8, 6, 3};
        else if (path.startsWith("iron")) t = new int[]{2, 6, 5, 2};
        else if (path.startsWith("chainmail")) t = new int[]{2, 5, 4, 1};
        else if (path.startsWith("golden")) t = new int[]{2, 5, 3, 1};
        else if (path.startsWith("leather")) t = new int[]{1, 3, 2, 1};
        else if (path.startsWith("turtle")) t = new int[]{2, 0, 0, 0};
        else return 1.0;
        return t[slot];
    }

    public DamageMath.Def def(boolean blocking, boolean useEffects, boolean blast) {
        DamageMath.Def d = new DamageMath.Def();
        d.armor = armor;
        d.toughness = toughness;
        d.epf = blast ? epfBlast : epf;
        d.resist = useEffects ? resist : 0;
        d.absorption = absorption;
        d.health = health;
        d.blocking = blocking;
        return d;
    }

    /** HP that must be removed to kill: health + absorption (+ roughly one extra life per held totem when asked). */
    public double effectiveHp(boolean withTotems) {
        double hp = health + absorption;
        if (withTotems) hp += Predict.TOTEM_EXTRA_HP * totemsHeld;
        return hp;
    }

    // ---------------- armor durability ----------------

    /** Remaining durability of one armor slot in percent, or -1 when the slot has no damageable armor. */
    public double piecePct(int i) {
        ItemStack s = entity.getEquippedStack(ARMOR[i]);
        if (s.isEmpty() || !s.isDamageable() || s.getMaxDamage() <= 0) return -1.0;
        return 100.0 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage();
    }

    /** Overall remaining durability (weighted by max durability), or -1 if no armor. */
    public double durabilityPct() {
        double rem = 0, max = 0;
        for (EquipmentSlot sl : ARMOR) {
            ItemStack s = entity.getEquippedStack(sl);
            if (s.isEmpty() || !s.isDamageable() || s.getMaxDamage() <= 0) continue;
            rem += s.getMaxDamage() - s.getDamage();
            max += s.getMaxDamage();
        }
        return max <= 0 ? -1.0 : 100.0 * rem / max;
    }

    /** Index of the armor piece with the lowest durability (the one an XP bottle would help first), or -1. */
    public int lowestPiece() {
        int best = -1;
        double bv = 1e9;
        for (int i = 0; i < ARMOR.length; i++) {
            double v = piecePct(i);
            if (v >= 0 && v < bv) { bv = v; best = i; }
        }
        return best;
    }
}
