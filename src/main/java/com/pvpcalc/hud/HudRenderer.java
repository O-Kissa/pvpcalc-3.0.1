package com.pvpcalc.hud;

import com.pvpcalc.combat.DamageCalculator;
import com.pvpcalc.combat.Gear;
import com.pvpcalc.combat.MaceCalculator;
import com.pvpcalc.core.DamageMath;
import com.pvpcalc.core.KitDefs;
import com.pvpcalc.core.Lang;
import com.pvpcalc.core.MatchData;
import com.pvpcalc.core.Num;
import com.pvpcalc.core.PlayerData;
import com.pvpcalc.core.Predict;
import com.pvpcalc.core.Report;
import com.pvpcalc.core.Settings;
import com.pvpcalc.hack.HackDetector;
import com.pvpcalc.tracking.MatchManager;
import com.pvpcalc.tracking.Opp;
import com.pvpcalc.util.Clock;
import com.pvpcalc.util.Guard;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Corner panel (damage, durability, counters, prediction...) and the opponent HP line above the armor bar. */
public final class HudRenderer {
    private record Line(String text, int rgb) {}

    private static List<Line> lines = new ArrayList<>();
    private static String oppHp = "";
    private static int oppHpRgb = 0xFFFFFF;
    private static String oppName = "";

    private HudRenderer() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("pvpcalc", "hud"),
                (ctx, tick) -> Guard.run("hud", () -> draw(ctx)));
    }

    private static int pctColor(double pct) {
        if (!Settings.bool("hud.colorCode")) return parseColor();
        if (pct < Settings.i("hud.green")) return 0x55FF55;
        if (pct < Settings.i("hud.yellow")) return 0xFFFF55;
        return 0xFF5555;
    }

    private static int parseColor() {
        try {
            return Integer.parseInt(Settings.str("hud.color").replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    private static boolean visibleByRule(ClientPlayerEntity me) {
        String rule = Settings.str("hud.rule");
        if (rule.equals("weapon")) return DamageCalculator.kindOf(me.getMainHandStack()) != DamageCalculator.Kind.OTHER;
        if (rule.equals("hit")) return Clock.tick() - MatchManager.lastHitTick() < 100;
        return true;
    }

    /** Recomputes the cached lines (called a few times per second, not every frame). */
    public static void update(MinecraftClient mc) {
        List<Line> out = new ArrayList<>();
        oppHp = "";
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.world == null || !Settings.bool("enabled")) { lines = out; return; }
        PlayerEntity opp = Opp.opponent(mc);
        LivingEntity tgt = Settings.is("target", "self") ? me : opp;
        int base = parseColor();
        boolean mini = Settings.bool("hud.minimized");

        if (opp != null) {
            Gear og = Gear.of(opp);
            oppName = og.name;
            if (Settings.bool("hud.oppHp")) {
                oppHp = og.name + ": " + Report.hp(og.health) + (og.absorption > 0 ? " +" + Report.num(og.absorption) : "");
                oppHpRgb = pctColor(100.0 - 100.0 * og.health / og.maxHealth);
            }
            out.add(new Line(og.name, 0xFFFF55));
        }
        if (tgt != null && !mini) {
            DamageCalculator.Result r = DamageCalculator.melee(me, tgt);
            Gear tg = Gear.of(tgt);
            int htk = DamageMath.hitsToKill(tg.effectiveHp(false), r.dmg());
            if (Settings.bool("hud.dmg")) out.add(new Line(Lang.t("hud.dmg", Report.num(r.dmg())), base));
            if (Settings.bool("hud.htk")) out.add(new Line(Lang.t("hud.htk", htk == Integer.MAX_VALUE ? "-" : String.valueOf(htk)), base));
            if (Settings.bool("hud.ttk")) {
                double t = Predict.ttk(tg.effectiveHp(false), r.dmg(), DamageCalculator.intervalSec(me, DamageCalculator.kindOf(me.getMainHandStack())));
                out.add(new Line(Lang.t("hud.ttk", t >= 1e9 ? "-" : Num.fmt(t, 1)), base));
            }
        }
        if (opp != null && !mini) {
            Gear og = Gear.of(opp);
            if (Settings.bool("hud.durability")) {
                double d = og.durabilityPct();
                if (d >= 0) {
                    int low = og.lowestPiece();
                    String lowTxt = low >= 0 ? " (" + Lang.t("hud.lowest", Lang.t("piece." + Gear.PIECE_KEYS[low]), Num.fmt(og.piecePct(low), 0)) + (og.hasMending ? ", XP" : "") + ")" : "";
                    out.add(new Line(Lang.t("hud.armor", Num.fmt(d, 0)) + lowTxt, pctColor(100.0 - d)));
                }
            }
            MatchData m = MatchManager.current();
            PlayerData pd = m == null ? null : m.players.get(opp.getUuid().toString());
            if (Settings.bool("hud.counters") && pd != null) {
                String kit = m.kit;
                out.add(new Line(Lang.t("hud.counters", Report.used(kit, 0, pd.totems), Report.used(kit, 1, pd.gapples + pd.enchGapples),
                        Report.used(kit, 2, pd.pearls), Report.used(kit, 4, pd.xp)), base));
            }
            if (Settings.bool("hud.mace")) {
                double fall = MaceCalculator.fallNeeded(me, opp);
                if (!Double.isNaN(fall) || DamageCalculator.kindOf(me.getMainHandStack()) == DamageCalculator.Kind.MACE) {
                    out.add(new Line(Double.isNaN(fall) ? Lang.t("hud.mace_none") : Lang.t("hud.mace", Num.fmt(fall, 1)), 0xFFAA00));
                }
            }
            if (Settings.bool("hud.prediction")) {
                String p = prediction(me, opp, og, m, pd);
                MatchManager.lastPrediction = p;
                out.add(new Line(Lang.t("hud.pred", p), 0xFF55FF));
            }
            if (Settings.bool("hud.hack") && Settings.bool("hack.enabled")) {
                String u = opp.getUuid().toString();
                if (HackDetector.samplesEnough(u)) {
                    double c = HackDetector.chanceOf(u);
                    out.add(new Line(Lang.t("hud.hack", Num.fmt(c, 1), HackDetector.confidenceOf(u)), pctColor(c)));
                }
            }
            if (Settings.bool("hud.cps")) out.add(new Line(Lang.t("hud.cps", HackDetector.cpsOf(opp.getUuid().toString())), base));
            if (Settings.bool("hud.distance")) out.add(new Line(Lang.t("hud.dist", Num.fmt(opp.distanceTo(me), 1)), base));
        } else if (mini && opp != null && Settings.bool("hud.hack")) {
            String u = opp.getUuid().toString();
            if (HackDetector.samplesEnough(u)) out.add(new Line(Lang.t("hud.hack", Num.fmt(HackDetector.chanceOf(u), 1), HackDetector.confidenceOf(u)), pctColor(HackDetector.chanceOf(u))));
        }
        lines = out;
    }

    private static String prediction(ClientPlayerEntity me, PlayerEntity opp, Gear og, MatchData m, PlayerData pd) {
        Gear mg = Gear.of(me);
        DamageCalculator.Result mine = DamageCalculator.melee(me, opp);
        DamageCalculator.Result theirs = DamageCalculator.melee(opp, me);
        int pops = pd == null ? 0 : pd.totems;
        int res = KitDefs.res(m == null ? Settings.str("kit") : m.kit)[0];
        int oppTotems = (Settings.bool("smpMode") || res < 0) ? og.totemsHeld : Math.max(og.totemsHeld, res - pops);
        double myHp = mg.health + mg.absorption + Predict.TOTEM_EXTRA_HP * Math.max(mg.totemsTotal, mg.totemsHeld);
        double oppHpTotal = og.health + og.absorption + Predict.TOTEM_EXTRA_HP * oppTotems;
        double ttkMe = Predict.ttk(oppHpTotal, mine.dmg(), DamageCalculator.intervalSec(me, DamageCalculator.kindOf(me.getMainHandStack())));
        double ttkOpp = Predict.ttk(myHp, theirs.dmg(), DamageCalculator.intervalSec(opp, DamageCalculator.kindOf(opp.getMainHandStack())));
        double pct = Predict.winPct(ttkMe, ttkOpp);
        return Num.fmt(pct, 0) + "% (TTK " + (ttkMe >= 1e9 ? "-" : Num.fmt(ttkMe, 1)) + "s / " + (ttkOpp >= 1e9 ? "-" : Num.fmt(ttkOpp, 1)) + "s)";
    }

    private static int argb(int rgb) {
        int a = (int) Math.round(255.0 * Num.clamp(Settings.i("hud.opacity"), 10, 100) / 100.0);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static void draw(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.options.hudHidden || !Settings.bool("enabled")) return;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();

        if (Settings.bool("hud.oppHp") && !oppHp.isEmpty()) {
            ctx.drawTextWithShadow(tr, oppHp, sw / 2 - 91, sh - 61, argb(oppHpRgb));
        }
        if (!Settings.bool("hud.visible") || lines.isEmpty() || !visibleByRule(me)) return;
        int w = 0;
        for (Line l : lines) w = Math.max(w, tr.getWidth(l.text()));
        int h = lines.size() * 10;
        int x = Settings.i("hud.x"), y = Settings.i("hud.y");
        String c = Settings.str("hud.corner");
        int px = c.endsWith("r") ? sw - w - x : x;
        int py = c.startsWith("b") ? sh - h - y : y;
        for (Line l : lines) {
            ctx.drawTextWithShadow(tr, l.text(), px, py, argb(l.rgb()));
            py += 10;
        }
    }
}
