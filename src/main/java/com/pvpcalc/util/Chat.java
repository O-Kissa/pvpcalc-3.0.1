package com.pvpcalc.util;

import com.pvpcalc.core.Lang;
import com.pvpcalc.core.Settings;
import com.pvpcalc.tracking.MatchManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All chat output: prefix, repeat limiting, quiet mode, clickable links. */
public final class Chat {
    private static final Map<String, Long> LAST = new HashMap<>();
    private static final List<Text> QUIET = new ArrayList<>();

    private Chat() {}

    public static MutableText prefix() {
        return Text.literal(Settings.str("chatPrefix") + " ").formatted(Formatting.AQUA);
    }

    public static void send(Text t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(t, false);
    }

    private static boolean limited(String key) {
        int cd = Settings.i("messageCooldown") * 1000;
        if (cd <= 0) return false;
        long now = System.currentTimeMillis();
        if (LAST.size() > 200) LAST.clear();
        Long last = LAST.get(key);
        if (last != null && now - last < cd) return true;
        LAST.put(key, now);
        return false;
    }

    /** Message with prefix; the same text is not repeated within the cooldown. */
    public static void info(Text body) {
        if (limited(body.getString())) return;
        send(prefix().append(body));
    }

    public static void say(String key, Object... args) {
        info(Text.literal(Lang.t(key, args)));
    }

    /** Always shown (no repeat limit): command results the user asked for. */
    public static void reply(Text body) {
        send(prefix().append(body));
    }

    public static void reply(String key, Object... args) {
        reply(Text.literal(Lang.t(key, args)));
    }

    /** Alerts wait until the match is over when quiet mode is on. */
    public static void alert(Text body) {
        if (Settings.bool("quietMode") && MatchManager.inMatch()) {
            if (QUIET.size() < 50) QUIET.add(body);
            return;
        }
        info(body);
    }

    public static void flushQuiet() {
        List<Text> copy = new ArrayList<>(QUIET);
        QUIET.clear();
        for (Text t : copy) send(prefix().append(t));
    }

    public static void clearQuiet() {
        QUIET.clear();
    }

    public static MutableText link(String label, String command, String hover) {
        Style st = Style.EMPTY.withColor(Formatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(hover)));
        return Text.literal("[" + label + "]").setStyle(st);
    }

    public static MutableText hover(String text, String tip, Formatting color) {
        Style st = Style.EMPTY.withColor(color).withHoverEvent(new HoverEvent.ShowText(Text.literal(tip)));
        return Text.literal(text).setStyle(st);
    }

    public static void reset() {
        LAST.clear();
        QUIET.clear();
    }
}
