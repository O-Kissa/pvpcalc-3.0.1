package com.pvpcalc.hack;

import com.pvpcalc.combat.DamageCalculator;
import net.minecraft.item.ItemStack;

/** Tiny helpers so the detector does not depend on the whole damage engine. */
final class DamageCalcBridge {
    private DamageCalcBridge() {}

    static boolean isSpear(ItemStack s) {
        return DamageCalculator.kindOf(s) == DamageCalculator.Kind.SPEAR;
    }

    static boolean isMace(ItemStack s) {
        return DamageCalculator.kindOf(s) == DamageCalculator.Kind.MACE;
    }
}
