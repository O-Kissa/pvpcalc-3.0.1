package com.pvpcalc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Kit names, menu groups and (editable) resource counts per kit. */
public final class KitDefs {
    public static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    /** kit -> {totems, gapples, pearls, potions, xpBottles}; -1 = unlimited. Editable in kits.json. */
    public static final Map<String, int[]> RES = new LinkedHashMap<>();

    static {
        GROUPS.put("Netherite PvP", List.of("NETHERITE POT", "SMP"));
        GROUPS.put("Diamond PvP", List.of("DIAMOND POT", "DIAMOND CRYSTAL", "DIAMOND SMP"));
        GROUPS.put("Other PvP Modes", List.of("UHC", "HOPLITE", "MANHUNT", "AXE", "SWORD"));
        GROUPS.put("Additional PvP Modes", List.of("SPEAR MACE", "MACE", "ELYTRA MACE", "SPEAR ELYTRA", "CRYSTAL", "BOW", "CREEPER"));
        // Estimates – real server kits differ. Edit config/pvpcalc/kits.json to match your server.
        RES.put("NETHERITE POT", new int[]{5, 10, 8, 16, 16});
        RES.put("SMP", new int[]{-1, -1, -1, -1, -1});
        RES.put("DIAMOND POT", new int[]{5, 8, 8, 16, 16});
        RES.put("DIAMOND CRYSTAL", new int[]{4, 6, 4, 8, 16});
        RES.put("DIAMOND SMP", new int[]{-1, -1, -1, -1, -1});
        RES.put("UHC", new int[]{0, 6, 0, 0, 0});
        RES.put("HOPLITE", new int[]{0, 3, 0, 0, 0});
        RES.put("MANHUNT", new int[]{0, 3, 8, 0, 0});
        RES.put("AXE", new int[]{1, 6, 4, 4, 0});
        RES.put("SWORD", new int[]{1, 6, 4, 4, 0});
        RES.put("SPEAR MACE", new int[]{3, 6, 4, 6, 0});
        RES.put("MACE", new int[]{3, 6, 4, 6, 0});
        RES.put("ELYTRA MACE", new int[]{3, 6, 4, 6, 0});
        RES.put("SPEAR ELYTRA", new int[]{2, 6, 4, 4, 0});
        RES.put("CRYSTAL", new int[]{4, 6, 4, 8, 16});
        RES.put("BOW", new int[]{0, 6, 0, 0, 0});
        RES.put("CREEPER", new int[]{1, 4, 0, 0, 0});
    }

    private KitDefs() {}

    public static List<String> all() {
        List<String> all = new ArrayList<>();
        GROUPS.values().forEach(all::addAll);
        return all;
    }

    /** Case/whitespace-insensitive lookup. */
    public static Optional<String> resolve(String input) {
        if (input == null) return Optional.empty();
        String n = input.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return all().stream().filter(k -> k.equals(n)).findFirst();
    }

    public static int[] res(String kit) {
        int[] r = RES.get(kit == null ? "" : kit);
        return r == null ? new int[]{-1, -1, -1, -1, -1} : r;
    }

    public static boolean isMaceKit(String kit) { return kit != null && kit.contains("MACE"); }
    public static boolean isElytraKit(String kit) { return kit != null && kit.contains("ELYTRA"); }
    public static boolean isSpearKit(String kit) { return kit != null && kit.contains("SPEAR"); }
}
