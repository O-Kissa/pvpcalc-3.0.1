package com.pvpcalc.util;

import com.pvpcalc.config.ConfigIo;
import com.pvpcalc.core.Lang;
import com.pvpcalc.core.Settings;
import com.pvpcalc.input.KeyBindings;
import com.pvpcalc.logging.CsvStore;
import com.pvpcalc.tracking.StatsStore;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /pvpcalc check: tells what works and what does not, with a hint how to fix it. */
public final class SelfCheck {
    private SelfCheck() {}

    private static void add(List<String> l, boolean ok, String key, Object... args) {
        l.add((ok ? "\u00a7a\u2714 " : "\u00a7c\u2718 ") + Lang.t(key + (ok ? ".ok" : ".bad"), args));
    }

    public static List<String> run() {
        List<String> l = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        l.add("\u00a76" + Lang.t("chk_.title", Dirs.version()));
        add(l, Lang.has("rep.title") && Lang.hasFi("rep.title"), "chk_.lang");
        add(l, Files.exists(ConfigIo.configFile()) && !Settings.dirty, "chk_.config");
        add(l, CsvStore.writable(), "chk_.csv", Dirs.dir());
        int bad = 0;
        for (Settings.Def d : Settings.ALL) {
            Object v = Settings.get(d.id);
            if (v == null) bad++;
        }
        add(l, bad == 0, "chk_.settings", bad);
        add(l, StatsStore.p != null, "chk_.stats");
        add(l, !KeyBindings.lines().isEmpty(), "chk_.keys");
        add(l, mc.getNetworkHandler() != null, "chk_.world");
        l.add((com.pvpcalc.util.Hooks.attack > 0 ? "\u00a7a\u2714 " : "\u00a77\u2022 ") + Lang.t(Hooks.attack > 0 ? "chk_.hook_attack.ok" : "chk_.hook_attack.wait"));
        l.add((Hooks.status > 0 ? "\u00a7a\u2714 " : "\u00a77\u2022 ") + Lang.t(Hooks.status > 0 ? "chk_.hook_status.ok" : "chk_.hook_status.wait"));
        l.add((Hooks.timeUpdate > 0 ? "\u00a7a\u2714 " : "\u00a77\u2022 ") + Lang.t(Hooks.timeUpdate > 0 ? "chk_.hook_time.ok" : "chk_.hook_time.wait"));
        boolean noErr = Guard.errors().isEmpty();
        add(l, noErr, "chk_.errors");
        if (!noErr) for (Map.Entry<String, Integer> e : Guard.errors().entrySet())
            l.add("\u00a77   " + e.getKey() + ": " + e.getValue() + (Guard.isOff(e.getKey()) ? " (" + Lang.t("chk_.off") + ")" : ""));
        return l;
    }
}
