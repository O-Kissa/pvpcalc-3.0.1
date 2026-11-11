package com.pvpcalc.combat;

import com.pvpcalc.core.DamageMath;
import com.pvpcalc.core.Num;
import com.pvpcalc.core.Report;
import com.pvpcalc.core.Settings;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Melee damage using the attacker's real inventory/effects and the target's real armor, honouring the settings
 * (1.8 vs 1.9+, difficulty, shield assumption, crit handling, effects, cooldown assumption).
 */
public final class DamageCalculator {
    public enum Kind { SWORD, AXE, MACE, SPEAR, OTHER }

    public record Result(String details, double raw, double dmg, boolean crit, boolean blocked, String label, double charge) {}

    private static final TagKey<EntityType<?>> SENSITIVE_SMITE = tag("sensitive_to_smite");
    private static final TagKey<EntityType<?>> SENSITIVE_BANE = tag("sensitive_to_bane_of_arthropods");
    private static final TagKey<EntityType<?>> SENSITIVE_IMPALING = tag("sensitive_to_impaling");

    private DamageCalculator() {}

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of("minecraft", path));
    }

    public static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    // ---------------- item helpers ----------------

    public static String itemPath(ItemStack s) {
        return s.isEmpty() ? "" : Registries.ITEM.getId(s.getItem()).getPath();
    }

    public static Kind kindOf(ItemStack s) {
        String p = itemPath(s);
        if (p.endsWith("_sword")) return Kind.SWORD;
        if (p.endsWith("_axe")) return Kind.AXE;
        if (p.equals("mace")) return Kind.MACE;
        if (p.endsWith("_spear")) return Kind.SPEAR;
        return Kind.OTHER;
    }

    public static int enchantLevel(ItemStack stack, RegistryKey<Enchantment> key) {
        if (stack == null || stack.isEmpty()) return 0;
        ItemEnchantmentsComponent c = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        for (RegistryEntry<Enchantment> e : c.getEnchantments()) {
            if (e.matchesKey(key)) return c.getLevel(e);
        }
        return 0;
    }

    public static String enchantList(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        ItemEnchantmentsComponent c = s.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        StringBuilder sb = new StringBuilder();
        for (RegistryEntry<Enchantment> e : c.getEnchantments()) {
            String n = e.getKey().map(k -> k.getValue().getPath()).orElse("?");
            if (sb.length() > 0) sb.append(", ");
            sb.append(n).append(' ').append(c.getLevel(e));
        }
        return sb.toString();
    }

    /** Fallback weapon damage by item (used when the attribute of another player is not synced). */
    public static double itemBase(ItemStack s) {
        String p = itemPath(s);
        Kind k = kindOf(s);
        if (k == Kind.SWORD) return swordTier(p);
        if (k == Kind.AXE) return p.startsWith("netherite") ? 10 : (p.startsWith("wooden") || p.startsWith("golden") ? 7 : 9);
        if (k == Kind.MACE) return 6;
        if (p.equals("trident")) return 9;
        return 1;
    }

    private static double swordTier(String p) {
        if (p.startsWith("netherite")) return 8;
        if (p.startsWith("diamond")) return 7;
        if (p.startsWith("iron")) return 6;
        if (p.startsWith("stone")) return 5;
        return 4;
    }

    public static double enchantBonus(ItemStack weapon, Entity target, boolean v18) {
        double bonus = DamageMath.sharpness(enchantLevel(weapon, Enchantments.SHARPNESS), v18);
        if (target != null) {
            int smite = enchantLevel(weapon, Enchantments.SMITE);
            if (smite > 0 && target.getType().isIn(SENSITIVE_SMITE)) bonus += 2.5 * smite;
            int bane = enchantLevel(weapon, Enchantments.BANE_OF_ARTHROPODS);
            if (bane > 0 && target.getType().isIn(SENSITIVE_BANE)) bonus += 2.5 * bane;
            int imp = enchantLevel(weapon, Enchantments.IMPALING);
            if (imp > 0 && target.getType().isIn(SENSITIVE_IMPALING)) bonus += 2.5 * imp;
        }
        return bonus;
    }

    // ---------------- effects ----------------

    public static int strengthLevel(LivingEntity e) {
        StatusEffectInstance s = e.getStatusEffect(StatusEffects.STRENGTH);
        return s == null ? 0 : s.getAmplifier() + 1;
    }

    public static int weaknessLevel(LivingEntity e) {
        StatusEffectInstance s = e.getStatusEffect(StatusEffects.WEAKNESS);
        return s == null ? 0 : s.getAmplifier() + 1;
    }

    /** +3 per Strength level, -4 per Weakness level (the ATTACK_DAMAGE attribute already contains this). */
    public static double effectBonus(LivingEntity e) {
        return 3.0 * strengthLevel(e) - 4.0 * weaknessLevel(e);
    }

    private static boolean ownEffects() {
        String m = Settings.str("countEffects");
        return m.equals("both") || m.equals("own");
    }

    private static boolean oppEffects() {
        String m = Settings.str("countEffects");
        return m.equals("both") || m.equals("opponent");
    }

    public static boolean isShieldBlocking(LivingEntity target) {
        return target.isBlocking();
    }

    // ---------------- melee ----------------

    private static boolean critNow(LivingEntity a, Entity target, boolean charged, boolean v18) {
        String mode = Settings.str("critHandling");
        if (!(target instanceof LivingEntity)) return false;
        if (mode.equals("off")) return false;
        if (mode.equals("always")) return true;
        return charged && a.fallDistance > 0.0 && !a.isOnGround() && !a.isClimbing() && !a.isTouchingWater()
                && !a.hasStatusEffect(StatusEffects.BLINDNESS) && !a.hasVehicle() && (v18 || !a.isSprinting());
    }

    /** Seconds between two full-strength hits. */
    public static double intervalSec(LivingEntity attacker, Kind kind) {
        if (Settings.is("combatSystem", "1.8")) return 1.0 / Math.max(1, Settings.i("cps18"));
        if (attacker instanceof PlayerEntity pe) {
            double perTick = pe.getAttackCooldownProgressPerTick();
            if (perTick > 0) return 1.0 / (perTick * 20.0);
        }
        switch (kind) {
            case SWORD: return 0.625;
            case AXE: return 1.0;
            case MACE: return 1.7;
            case SPEAR: return 0.95;
            default: return 0.5;
        }
    }

    public static Result melee(LivingEntity attacker, LivingEntity target) {
        ItemStack weapon = attacker.getMainHandStack();
        Kind kind = kindOf(weapon);
        boolean v18 = Settings.is("combatSystem", "1.8");
        String name;
        switch (kind) {
            case SWORD: name = "Sword"; break;
            case AXE: name = "Axe"; break;
            case MACE: name = "Mace"; break;
            case SPEAR: name = "Spear"; break;
            default: name = weapon.isEmpty() ? "Fist" : weapon.getName().getString();
        }

        Gear tg = Gear.of(target);
        boolean blocked = tg.blocking || Settings.bool("assumeShield");

        int str = ownEffects() ? strengthLevel(attacker) : 0;
        int weak = ownEffects() ? weaknessLevel(attacker) : 0;
        double attr = attacker.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        double base = Math.max(attr - effectBonus(attacker), itemBase(weapon));
        if (v18 && kind == Kind.AXE) base = Math.min(base, Math.max(1.0, swordTier(itemPath(weapon)) - 1.0));

        double h = 1.0;
        if (!v18 && attacker instanceof PlayerEntity pe && !Settings.is("cooldownAssumption", "full")) {
            h = Num.clamp(pe.getAttackCooldownProgress(0.5F), 0.0, 1.0);
        }
        boolean charged = h > 0.9 || v18;
        boolean crit = critNow(attacker, target, charged, v18);
        boolean sprintKb = attacker.isSprinting() && charged;

        double smash = 0.0;
        if (kind == Kind.MACE && !v18 && attacker instanceof PlayerEntity pe) smash = MaceCalculator.smashBonus(pe, weapon);

        DamageMath.Melee m = new DamageMath.Melee();
        m.v18 = v18; m.base = base; m.strength = str; m.weakness = weak; m.charge = h; m.crit = crit;
        m.ench = enchantBonus(weapon, target, v18); m.smash = smash;
        double raw = DamageMath.meleeRaw(m);

        DamageMath.Def def = tg.def(blocked, oppEffects(), false);
        def.breach = enchantLevel(weapon, Enchantments.BREACH);
        double dmg = DamageMath.applyDefense(raw, def, v18);

        Vec3d v = attacker.getVelocity();
        boolean sweep = kind == Kind.SWORD && !v18 && charged && !crit && !sprintKb && attacker.isOnGround()
                && 0.6 * Math.hypot(v.x, v.z) < attacker.getMovementSpeed();

        String label;
        if (kind == Kind.MACE && smash > 0) label = "Mace smash (fall " + fmt(MaceCalculator.fallDistance((PlayerEntity) attacker)) + " blocks" + (crit ? ", critical" : "") + ")";
        else if (kind == Kind.SPEAR) label = crit ? "Spear jab critical" : "Spear jab";
        else if (crit) label = kind == Kind.SWORD ? "Critical hit" : name + " critical hit";
        else if (sweep) label = "Sweep attack";
        else if (sprintKb) label = name + " sprint/knockback hit";
        else label = name + " hit";

        StringBuilder sb = new StringBuilder(label).append(": ").append(Report.num(dmg)).append(" damage");
        if (blocked) sb.append(" (blocked)");
        else if (Math.abs(raw - dmg) > 0.05) sb.append(" (raw ").append(Report.num(raw)).append(")");
        if (sweep) {
            double ratio = enchantLevel(weapon, Enchantments.SWEEPING_EDGE) / (enchantLevel(weapon, Enchantments.SWEEPING_EDGE) + 1.0);
            sb.append(" (splash ").append(Report.num(1.0 + ratio * base)).append(")");
        }
        if (!charged) sb.append(" [charge ").append(Math.round(h * 100)).append("%]");
        if (str > 0) sb.append(" (Strength ").append(str == 1 ? "I" : (str == 2 ? "II" : String.valueOf(str))).append(")");
        return new Result(sb.toString(), raw, dmg, crit, blocked, label, h);
    }
}
