package com.pvpcalc.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds the match texts (short chat line, full screen view, Discord text). Colour codes use the section sign. */
public final class Report {
    private Report() {}

    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("\u00a7.", "");
    }

    public static String num(double v) {
        return Num.fmt(v, Settings.i("decimals"));
    }

    public static String pct(double v) {
        return Num.fmt(Num.pct(v), 1) + "%";
    }

    public static String hp(double v) {
        String u = Settings.str("healthUnit");
        String hp = num(v) + " HP";
        String hearts = num(v / 2.0) + " \u2665";
        if (u.equals("hearts")) return hearts;
        if (u.equals("both")) return hp + " (" + hearts + ")";
        return hp;
    }

    public static String resultColor(String r) {
        return "WIN".equals(r) ? "\u00a7a" : ("LOSS".equals(r) ? "\u00a7c" : "\u00a77");
    }

    public static String resultName(String r) {
        return Lang.t("res." + r.toLowerCase());
    }

    /** "5 used / 3 left" style text for one consumable. idx: 0 totem, 1 gapple, 2 pearl, 3 potion, 4 xp. */
    public static String used(String kit, int idx, int used) {
        int total = KitDefs.res(kit)[idx];
        if (total < 0 || Settings.bool("smpMode")) return Lang.t("rep.used_only", used);
        return Lang.t("rep.used_left", used, Math.max(0, total - used));
    }

    public static String chatShort(MatchData m) {
        PlayerData p = m.primary();
        String opp = p == null ? "-" : p.name;
        int oppTotems = 0;
        for (PlayerData x : m.players.values()) oppTotems += x.totems;
        return Lang.t("rep.short", resultName(m.result), m.kit, opp, num(m.dealt), num(m.taken), m.hits, m.swings,
                pct(m.accuracy()), pct(m.critPct()), m.maxCombo, oppTotems);
    }

    public static List<String> overview(MatchData m) {
        List<String> l = new ArrayList<>();
        l.add("\u00a76\u00a7l" + Lang.t("rep.title", m.id));
        l.add(resultColor(m.result) + Lang.t("rep.result") + ": " + resultName(m.result)
                + "\u00a77   " + Lang.t("rep.kit") + ": \u00a7f" + m.kit
                + "\u00a77   " + Lang.t("rep.duration") + ": \u00a7f" + Num.fmt((m.endTick - m.startTick) / 20.0, 0) + " s");
        StringBuilder opp = new StringBuilder();
        for (PlayerData p : m.players.values()) { if (opp.length() > 0) opp.append(", "); opp.append(p.name); }
        l.add("\u00a77" + Lang.t("rep.opponents") + ": \u00a7f" + (opp.length() == 0 ? "-" : opp));
        l.add("\u00a7e" + Lang.t("rep.damage") + ": \u00a7f" + hp(m.dealt) + " \u00a77/ " + Lang.t("rep.taken") + " \u00a7f" + hp(m.taken)
                + "  \u00a77" + Lang.t("rep.maxhit") + ": \u00a7f" + hp(m.maxHit));
        l.add("\u00a7e" + Lang.t("rep.hits") + ": \u00a7f" + m.hits + "/" + m.swings + " (" + pct(m.accuracy()) + ")"
                + "  \u00a77" + Lang.t("rep.crit") + ": \u00a7f" + m.crits + " (" + pct(m.critPct()) + ")");
        l.add("\u00a7e" + Lang.t("rep.combos") + ": \u00a7f" + m.combos + "  \u00a77" + Lang.t("rep.maxcombo") + ": \u00a7f" + m.maxCombo);
        l.add("\u00a7e" + Lang.t("rep.mine") + ": \u00a7f" + Lang.t("rep.mine_vals", m.myGapples, m.myTotems));
        if (!Double.isNaN(m.oppHpLeft)) l.add("\u00a7c" + Lang.t("rep.opp_hp_left", hp(m.oppHpLeft)));
        if (!Double.isNaN(m.overkill)) l.add("\u00a7a" + Lang.t("rep.overkill", hp(m.overkill), m.lastHit));
        if (!m.deathInfo.isEmpty()) l.add("\u00a7c" + Lang.t("rep.death") + ": \u00a7f" + m.deathInfo);
        if (!m.lastHit.isEmpty() && Double.isNaN(m.overkill)) l.add("\u00a77" + Lang.t("rep.lasthit") + ": \u00a7f" + m.lastHit);
        l.add("\u00a7b" + Lang.t("rep.style") + ": \u00a7f" + Lang.t("style." + m.style())
                + "  \u00a77" + Lang.t("rep.spacing") + ": \u00a7f" + pct(m.spacingPct())
                + "  \u00a77" + Lang.t("rep.reaction") + ": \u00a7f" + (m.avgReaction() < 0 ? "-" : Num.fmt(m.avgReaction(), 2) + " s"));
        int ga = Settings.i("goalAccuracy"), gc = Settings.i("goalCrit");
        if (ga > 0) l.add("\u00a77" + Lang.t("rep.goal_acc", pct(m.accuracy()), ga, m.accuracy() >= ga ? "\u00a7a\u2714" : "\u00a7c\u2718"));
        if (gc > 0) l.add("\u00a77" + Lang.t("rep.goal_crit", pct(m.critPct()), gc, m.critPct() >= gc ? "\u00a7a\u2714" : "\u00a7c\u2718"));
        if (!m.winPrediction.isEmpty()) l.add("\u00a7d" + Lang.t("rep.prediction") + ": \u00a7f" + m.winPrediction);
        double hk = m.matchHack();
        l.add("\u00a7c" + Lang.t("rep.hack") + ": \u00a7f" + pct(hk) + "  \u00a78(" + Settings.str("disclaimer") + ")");
        l.add("\u00a76" + Lang.t("rep.tips"));
        for (String[] t : Tips.compute(m, ga, gc)) l.add("\u00a77 \u2022 \u00a7f" + Lang.t(t[0], t[1], t[2]));
        return l;
    }

    public static List<String> playerBlock(PlayerData p, MatchData m) {
        List<String> l = new ArrayList<>();
        l.add("\u00a7b" + Lang.t("rep.player") + ": \u00a7f" + p.name + (p.died ? " \u00a7c(" + Lang.t("rep.died") + ")" : ""));
        l.add("  \u00a77" + Lang.t("rep.damage") + ": \u00a7f" + hp(p.dealt) + " \u00a77/ " + Lang.t("rep.taken") + " \u00a7f" + hp(p.taken)
                + "  \u00a77" + Lang.t("rep.maxhit") + ": \u00a7f" + hp(p.maxHit));
        l.add("  \u00a77" + Lang.t("rep.hits") + ": \u00a7f" + p.hits + "  \u00a77" + Lang.t("rep.crit") + ": \u00a7f" + p.crits + " (" + pct(p.critPct()) + ")"
                + "  \u00a77" + Lang.t("rep.their_hits") + ": \u00a7f" + p.theirHits);
        l.add("  \u00a77" + Lang.t("rep.combos") + ": \u00a7f" + p.combos + "  \u00a77" + Lang.t("rep.maxcombo") + ": \u00a7f" + p.maxCombo
                + "  \u00a77" + Lang.t("rep.their_maxcombo") + ": \u00a7f" + p.theirMaxCombo);
        l.add("  \u00a7d" + Lang.t("rep.totems") + ": \u00a7f" + used(m.kit, 0, p.totems)
                + "  \u00a77" + Lang.t("rep.gapples") + ": \u00a7f" + used(m.kit, 1, p.gapples + p.enchGapples) + (p.enchGapples > 0 ? " (" + p.enchGapples + " ench)" : ""));
        l.add("  \u00a77" + Lang.t("rep.pearls") + ": \u00a7f" + used(m.kit, 2, p.pearls)
                + "  \u00a77" + Lang.t("rep.xp") + ": \u00a7f" + used(m.kit, 4, p.xp)
                + "  \u00a77" + Lang.t("rep.potions") + ": \u00a7f" + used(m.kit, 3, p.potions));
        l.add("  \u00a77" + Lang.t("rep.avgdist") + ": \u00a7f" + num(p.avgDist()) + "  \u00a77" + Lang.t("rep.spacing") + ": \u00a7f" + pct(p.spacingPct()));
        l.add("  \u00a7c" + Lang.t("rep.hack") + ": \u00a7f" + pct(p.hackPct) + (p.hackTop.isEmpty() ? "" : " (" + Lang.t("chk." + p.hackTop) + ")"));
        return l;
    }

    public static List<String> full(MatchData m, Set<String> expanded) {
        List<String> l = overview(m);
        l.add("");
        l.add("\u00a76" + Lang.t("rep.players"));
        for (PlayerData p : m.players.values()) {
            if (expanded == null || expanded.contains(p.uuid)) l.addAll(playerBlock(p, m));
            else l.add("\u00a7b\u25b6 " + p.name + " \u00a77(" + hp(p.dealt) + " / " + hp(p.taken) + ")");
        }
        return l;
    }

    public static String discord(MatchData m, boolean anonymous) {
        StringBuilder sb = new StringBuilder();
        sb.append("**PvPCalc #").append(m.id).append("** - ").append(resultName(m.result)).append(" - ").append(m.kit).append('\n');
        for (PlayerData p : m.players.values()) {
            String n = anonymous ? "Player" + (Math.abs(p.uuid.hashCode()) % 1000) : p.name;
            sb.append("> ").append(n).append(": ").append(num(p.dealt)).append(" / ").append(num(p.taken)).append(" HP, ")
                    .append(p.hits).append(" hits (").append(pct(p.critPct())).append(" crit), combo ").append(p.maxCombo)
                    .append(", totems ").append(p.totems).append(", gaps ").append(p.gapples + p.enchGapples).append('\n');
        }
        sb.append(Lang.t("rep.hits")).append(": ").append(m.hits).append('/').append(m.swings).append(" (").append(pct(m.accuracy())).append(")\n");
        sb.append(Settings.str("disclaimer"));
        return sb.toString();
    }

    public static String plain(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String s : lines) sb.append(strip(s)).append('\n');
        return sb.toString();
    }
}
