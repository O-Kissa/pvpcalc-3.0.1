package com.pvpcalc.tracking;

import com.pvpcalc.core.Settings;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.text.TranslatableTextContent;

/** Reads vanilla death messages (translation key + arguments, so it works in any language). */
public final class DeathAnalyzer {
    private DeathAnalyzer() {}

    private static String arg(Object[] a, int i) {
        if (a == null || i >= a.length || a[i] == null) return "";
        return a[i] instanceof Text t ? t.getString() : String.valueOf(a[i]);
    }

    public static void onMessage(Text msg) {
        if (!Settings.bool("enabled") || msg == null) return;
        TextContent c = msg.getContent();
        if (!(c instanceof TranslatableTextContent t)) return;
        String key = t.getKey();
        if (key == null || !key.startsWith("death.")) return;
        Object[] a = t.getArgs();
        String victim = arg(a, 0);
        if (victim.isEmpty()) return;
        String killer = arg(a, 1);
        String item = arg(a, 2);
        MatchManager.onDeathMessage(key, victim, killer, item, msg.getString());
    }
}
