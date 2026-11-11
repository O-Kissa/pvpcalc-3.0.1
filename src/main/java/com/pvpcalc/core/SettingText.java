package com.pvpcalc.core;

/** Display names and tooltips of settings (from the language files). */
public final class SettingText {
    private SettingText() {}

    public static String name(Settings.Def d) {
        if (d.id.startsWith("chk.")) {
            String[] p = d.id.split("\\.");
            return Lang.t("s.chk." + p[2], Lang.t("chk." + p[1]));
        }
        String n = Lang.t("s." + d.id);
        return n.equals("s." + d.id) ? d.id : n;
    }

    public static String desc(Settings.Def d) {
        String k = d.id.startsWith("chk.") ? "s.chk." + d.id.split("\\.")[2] + ".d" : "s." + d.id + ".d";
        String desc = Lang.has(k) ? Lang.t(k) : "";
        String range = d.type == Settings.Type.INT || d.type == Settings.Type.DOUBLE ? "  [" + (int) d.min + " - " + (int) d.max + "]" : "";
        return (desc.isEmpty() ? "" : desc + "\n") + Lang.t("gui.default") + ": " + Settings.showDefault(d.id) + range;
    }
}
