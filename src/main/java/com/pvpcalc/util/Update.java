package com.pvpcalc.util;

import com.pvpcalc.PvPCalcClient;
import com.pvpcalc.core.Lang;
import com.pvpcalc.core.MiniJson;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Notice-only update check against the public GitHub release API. Nothing is downloaded or installed. */
public final class Update {
    private static final String URL = "https://api.github.com/repos/O-Kissa/pvpcalc/releases/latest";
    private static boolean running;

    private Update() {}

    static int[] parts(String v) {
        String s = v.trim().toLowerCase();
        if (s.startsWith("v")) s = s.substring(1);
        String[] a = s.split("[^0-9]+");
        int[] out = new int[3];
        int k = 0;
        for (String x : a) {
            if (x.isEmpty()) continue;
            if (k >= 3) break;
            try { out[k++] = Integer.parseInt(x); } catch (NumberFormatException ignored) { k++; }
        }
        return out;
    }

    public static boolean newer(String latest, String current) {
        int[] a = parts(latest), b = parts(current);
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }

    public static void check(boolean verbose) {
        if (running) return;
        running = true;
        Thread t = new Thread(() -> {
            try {
                HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                HttpRequest r = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(6))
                        .header("Accept", "application/vnd.github+json").header("User-Agent", "PvPCalc").GET().build();
                HttpResponse<String> res = c.send(r, HttpResponse.BodyHandlers.ofString());
                String msg;
                if (res.statusCode() == 200) {
                    Map<String, Object> m = MiniJson.parseObject(res.body());
                    String tag = String.valueOf(m.get("tag_name"));
                    String cur = Dirs.version();
                    msg = newer(tag, cur) ? Lang.t("upd.available", tag, cur) : (verbose ? Lang.t("upd.latest", cur) : null);
                } else {
                    msg = verbose ? Lang.t("upd.none") : null;
                }
                if (msg != null) {
                    final String text = msg;
                    MinecraftClient.getInstance().execute(() -> com.pvpcalc.util.Chat.reply(Text.literal(text)));
                }
            } catch (Throwable ex) {
                if (verbose) MinecraftClient.getInstance().execute(() -> Chat.reply("upd.fail"));
                PvPCalcClient.LOGGER.debug("Update check failed", ex);
            } finally {
                running = false;
            }
        }, "pvpcalc-update");
        t.setDaemon(true);
        t.start();
    }
}
