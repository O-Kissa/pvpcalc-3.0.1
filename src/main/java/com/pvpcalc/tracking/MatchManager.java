package com.pvpcalc.tracking;

import com.pvpcalc.core.CsvUtil;
import com.pvpcalc.core.Lang;
import com.pvpcalc.core.MatchData;
import com.pvpcalc.core.Num;
import com.pvpcalc.core.PlayerData;
import com.pvpcalc.core.Report;
import com.pvpcalc.core.Settings;
import com.pvpcalc.hack.HackDetector;
import com.pvpcalc.logging.CsvStore;
import com.pvpcalc.util.Chat;
import com.pvpcalc.util.Clock;
import com.pvpcalc.util.Sounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Match lifecycle (start marker, end detection, summary) and every per-match counter. */
public final class MatchManager {
    private static MatchData cur, last;
    private static long lastActivity;
    private static final Map<String, Long> LAST_MY_HIT = new HashMap<>();
    private static final Map<String, Long> LAST_THEIR_HIT = new HashMap<>();
    private static double prevHp = -1;
    private static boolean myDeadHandled;
    private static long dangerTick = -1, myPopTick = -1, lastHurtMeTick = -1000, lastHitTick = -1000;
    private static boolean popSawEmpty;
    private static String lastTargetUuid;
    private static double lastHitHpBefore = -1;
    private static String pendingResult, pendingReason;
    private static long pendingDeadline;
    private static String prevDistUuid;
    private static double prevDist = -1;
    /** Set for exactly one tick when my own HP dropped (read by the hack detector for reach checks). */
    public static boolean meHurt;
    public static double meHurtAmount;
    public static String lastPrediction = "";

    private MatchManager() {}

    public static boolean inMatch() { return cur != null; }
    public static MatchData current() { return cur; }
    public static MatchData last() { return last; }
    public static long lastHitTick() { return lastHitTick; }
    public static String lastTargetUuid() { return lastTargetUuid; }

    public static String kitContext() {
        String k = cur != null ? cur.kit : Settings.str("kit");
        return (k == null || k.isEmpty()) ? "NONE" : k;
    }

    private static boolean hasActivity(MatchData m) {
        return m.hits > 0 || m.swings > 0 || m.taken > 0 || !m.players.isEmpty();
    }

    private static MatchData begin(String kit, boolean marker) {
        cur = new MatchData();
        cur.id = StatsStore.p.history.size() + 1;
        cur.kit = (kit == null || kit.isEmpty()) ? "NONE" : kit;
        cur.startTick = Clock.tick();
        lastActivity = Clock.tick();
        LAST_MY_HIT.clear();
        LAST_THEIR_HIT.clear();
        dangerTick = -1;
        myPopTick = -1;
        pendingResult = null;
        prevDist = -1;
        if (marker) CsvStore.eventRaw(CsvUtil.MARKER, cur.kit, "", "", "");
        return cur;
    }

    /** A kit was selected (or a new match requested): close the old match, write the marker. */
    public static void startMatch(String kit) {
        if (!Settings.bool("enabled")) return;
        if (cur != null && hasActivity(cur)) end("UNKNOWN", "new match");
        cur = null;
        begin(kit, true);
    }

    public static MatchData ensure() {
        if (cur == null) begin(Settings.str("kit"), true);
        return cur;
    }

    private static void touch() {
        lastActivity = Clock.tick();
    }

    // ---------------- events ----------------

    public static void onSwing() {
        if (!Settings.bool("enabled")) return;
        ensure().swings++;
        touch();
    }

    public static void onMyHit(PlayerEntity target, String details, double dmg, boolean crit) {
        if (!Settings.bool("enabled")) return;
        MatchData m = ensure();
        touch();
        String uuid = target.getUuid().toString();
        PlayerData pd = m.player(uuid, target.getName().getString());
        long now = Clock.tick();
        lastHitHpBefore = target.getHealth() + target.getAbsorptionAmount();
        m.hits++; pd.hits++;
        if (crit) { m.crits++; pd.crits++; }
        m.dealt += dmg; pd.dealt += dmg;
        m.maxHit = Math.max(m.maxHit, dmg); pd.maxHit = Math.max(pd.maxHit, dmg);
        Long lt = LAST_MY_HIT.get(uuid);
        boolean cont = lt != null && now - lt <= 40 && lastHurtMeTick < lt;
        pd.combo = cont ? pd.combo + 1 : 1;
        if (pd.combo == 2) { pd.combos++; m.combos++; }
        pd.maxCombo = Math.max(pd.maxCombo, pd.combo);
        m.maxCombo = Math.max(m.maxCombo, pd.combo);
        LAST_MY_HIT.put(uuid, now);
        LAST_THEIR_HIT.remove(uuid);
        lastTargetUuid = uuid;
        lastHitTick = now;
        m.lastHit = details;
        m.lastHitDmg = dmg;
        if (Settings.bool("soundKill") && dmg >= lastHitHpBefore && lastHitHpBefore > 0) Sounds.play(Settings.str("killSound"), 1.0F, 1.0F);
        CsvStore.event("DAMAGE", pd.name, details, dmg);
        HackDetector.onMyHit(target);
    }

    /** Potential damage rows (bow, crossbow, explosions): logged but not added to the totals. */
    public static void logPotential(String player, String details, double value) {
        if (!Settings.bool("enabled")) return;
        ensure();
        touch();
        CsvStore.event("DAMAGE", player, details, value);
    }

    public static void onTotemPop(PlayerEntity p) {
        if (!Settings.bool("enabled") || !Settings.bool("trackTotems")) return;
        MatchData m = ensure();
        touch();
        PlayerData pd = m.player(p.getUuid().toString(), p.getName().getString());
        pd.totems++;
        boolean opp = lastTargetUuid == null || lastTargetUuid.equals(pd.uuid) || m.players.size() <= 1;
        CsvStore.event("TOTEM POP", pd.name, opp ? "Opponent used Totem of Undying" : "Player " + pd.name + " used Totem of Undying", pd.totems);
        String msg = Lang.t("msg.popped", pd.name);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(msg).formatted(Formatting.GOLD), false);
        if (Settings.bool("soundPop")) Sounds.play(Settings.str("popSound"), 1.0F, 1.2F);
        HackDetector.onPop(p);
    }

    public static void onMyPop() {
        if (!Settings.bool("enabled") || !Settings.bool("trackTotems")) return;
        MatchData m = ensure();
        touch();
        m.myTotems++;
        myPopTick = Clock.tick();
        popSawEmpty = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        CsvStore.event("TOTEM POP", mc.player == null ? "me" : mc.player.getName().getString(), "You used Totem of Undying", m.myTotems);
    }

    /** kind: gapple, egapple, pearl, xp, potion. */
    public static void onConsume(PlayerEntity p, String kind) {
        if (!Settings.bool("enabled")) return;
        String flag = kind.equals("pearl") ? "trackPearls" : (kind.equals("xp") ? "trackXp" : (kind.equals("potion") ? "trackPotions" : "trackGapples"));
        if (!Settings.bool(flag)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean me = mc.player != null && p == mc.player;
        MatchData m = ensure();
        touch();
        String what = kind.equals("gapple") ? "Golden Apple" : kind.equals("egapple") ? "Enchanted Golden Apple"
                : kind.equals("pearl") ? "Ender Pearl" : kind.equals("xp") ? "XP Bottle" : "Potion";
        String event = kind.equals("pearl") ? "PEARL" : kind.equals("xp") ? "XP BOTTLE" : kind.equals("potion") ? "POTION" : "GOLDEN APPLE";
        int count;
        String name = p.getName().getString();
        if (me) {
            if (kind.startsWith("gapple") || kind.equals("egapple")) m.myGapples++;
            count = m.myGapples;
            CsvStore.event(event, name, "You consumed " + what, count);
            return;
        }
        PlayerData pd = m.player(p.getUuid().toString(), name);
        switch (kind) {
            case "gapple": pd.gapples++; count = pd.gapples; break;
            case "egapple": pd.enchGapples++; count = pd.enchGapples; break;
            case "pearl": pd.pearls++; count = pd.pearls; break;
            case "xp": pd.xp++; count = pd.xp; break;
            default: pd.potions++; count = pd.potions;
        }
        boolean throwing = kind.equals("pearl") || kind.equals("xp");
        CsvStore.event(event, name, "Opponent " + (throwing ? "threw " : "consumed ") + what, count);
    }

    /** I started eating a golden apple: measures reaction time after "danger" (HP <= 6). */
    public static void onMyEatStart() {
        if (cur != null && dangerTick >= 0) {
            cur.reactions.add((Clock.tick() - dangerTick) / 20.0);
            dangerTick = -1;
        }
    }

    private static void onMyHurt(double amount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity att = Opp.nearest(mc, 8.0);
        if (cur == null && att == null) return;
        MatchData m = ensure();
        touch();
        long now = Clock.tick();
        lastHurtMeTick = now;
        m.taken += amount;
        if (att != null) {
            String uuid = att.getUuid().toString();
            PlayerData pd = m.player(uuid, att.getName().getString());
            pd.taken += amount;
            pd.theirHits++;
            Long lt = LAST_THEIR_HIT.get(uuid);
            boolean cont = lt != null && now - lt <= 40;
            pd.theirCombo = cont ? pd.theirCombo + 1 : 1;
            pd.theirMaxCombo = Math.max(pd.theirMaxCombo, pd.theirCombo);
            LAST_THEIR_HIT.put(uuid, now);
            LAST_MY_HIT.remove(uuid);
            CsvStore.event("DAMAGE TAKEN", pd.name, "Took " + Report.num(amount) + " damage", amount);
        }
    }

    // ---------------- death messages ----------------

    public static void onDeathMessage(String key, String victim, String killer, String item, String full) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        MatchData target = cur != null ? cur : (last != null && Clock.tick() - last.endTick < 100 ? last : null);
        if (target == null) return;
        boolean me = victim.equalsIgnoreCase(mc.player.getName().getString());
        String info;
        if (me) {
            info = killer.isEmpty() ? Lang.t("death.me_env", Report.strip(full))
                    : (item.isEmpty() ? Lang.t("death.me_killer", killer) : Lang.t("death.me_killer_item", killer, item));
        } else {
            PlayerData pd = null;
            for (PlayerData x : target.players.values()) if (x.name.equalsIgnoreCase(victim)) pd = x;
            if (pd == null) return;
            pd.died = true;
            info = Lang.t("death.opp", victim, killer.isEmpty() ? "?" : killer, item.isEmpty() ? "-" : item);
        }
        target.deathInfo = info;
        if (target == cur) end(me ? "LOSS" : "WIN", "death message");
    }

    // ---------------- tick ----------------

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity me = mc.player;
        ClientWorld w = mc.world;
        meHurt = false;
        if (me == null || w == null) return;
        long now = Clock.tick();
        double hp = me.getHealth() + me.getAbsorptionAmount();
        if (prevHp >= 0 && hp < prevHp - 0.01 && me.isAlive() && Settings.bool("enabled")) {
            meHurt = true;
            meHurtAmount = prevHp - hp;
            onMyHurt(meHurtAmount);
        }
        prevHp = hp;
        if (me.getHealth() <= 6.0F && dangerTick < 0) dangerTick = now;
        if (me.getHealth() > 12.0F) dangerTick = -1;

        if (me.isDead() || me.getHealth() <= 0.0F) {
            if (!myDeadHandled) {
                myDeadHandled = true;
                if (cur != null) schedule("LOSS", "death", 15);
            }
        } else {
            myDeadHandled = false;
        }
        if (pendingResult != null && now >= pendingDeadline) end(pendingResult, pendingReason);
        if (cur == null) return;

        // opponent death / HP
        for (PlayerData pd : cur.players.values()) {
            PlayerEntity e = Opp.byUuid(mc, pd.uuid);
            if (e == null) continue;
            pd.hpEnd = e.getHealth() + e.getAbsorptionAmount();
            if (!pd.died && (e.isDead() || e.getHealth() <= 0.0F) && !me.isDead()) {
                pd.died = true;
                pd.hpEnd = 0;
                schedule("WIN", "opponent death", 15);
            }
        }

        // distance / playstyle
        PlayerEntity opp = cur.primary() == null ? null : Opp.byUuid(mc, cur.primary().uuid);
        if (opp != null && opp.isAlive()) {
            double d = opp.distanceTo(me);
            if (d <= 10.0) {
                PlayerData pd = cur.player(opp.getUuid().toString(), null);
                pd.distSum += d;
                pd.distTicks++;
                if (d >= 2.5 && d <= 3.5) pd.spacingTicks++;
                if (prevDistUuid != null && prevDistUuid.equals(pd.uuid) && prevDist >= 0) {
                    if (d < prevDist - 0.02) pd.approach++; else if (d > prevDist + 0.02) pd.retreat++;
                }
                prevDist = d;
                prevDistUuid = pd.uuid;
            } else {
                prevDist = -1;
            }
        }

        // totem re-equip reaction
        if (myPopTick >= 0) {
            boolean has = me.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || me.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
            if (!has) popSawEmpty = true;
            else if (popSawEmpty) { cur.reactions.add((now - myPopTick) / 20.0); myPopTick = -1; }
            if (myPopTick >= 0 && now - myPopTick > 200) myPopTick = -1;
        }

        int sec = Settings.i("matchInactivity");
        if (sec > 0 && now - lastActivity > sec * 20L && hasActivity(cur) && pendingResult == null) end("UNKNOWN", "inactive");
        if (now % 20 == 0) writeLive();
    }

    private static void schedule(String result, String reason, int delay) {
        if (pendingResult != null) return;
        pendingResult = result;
        pendingReason = reason;
        pendingDeadline = Clock.tick() + delay;
    }

    // ---------------- end ----------------

    public static void end(String result, String reason) {
        MatchData m = cur;
        if (m == null || m.closed) return;
        pendingResult = null;
        m.closed = true;
        m.result = result;
        m.endTick = Clock.tick();
        PlayerData prim = m.primary();
        if (prim != null) {
            if ("LOSS".equals(result) && prim.hpEnd >= 0) m.oppHpLeft = prim.hpEnd;
            if ("WIN".equals(result) && m.lastHitDmg > 0 && lastHitHpBefore > 0) m.overkill = Math.max(0, m.lastHitDmg - lastHitHpBefore);
        }
        m.winPrediction = lastPrediction;
        for (PlayerData pd : m.players.values()) HackDetector.fill(pd);
        HackDetector.matchEnded(m);
        writeMatchRows(m);
        StatsStore.recordMatch(m);
        last = m;
        cur = null;
        LAST_MY_HIT.clear();
        LAST_THEIR_HIT.clear();
        if (Settings.bool("enabled") && Settings.bool("autoReport") && hasActivity(m)) {
            Chat.send(Chat.prefix().append(Text.literal(Report.chatShort(m))).append(Text.literal(" "))
                    .append(Chat.link(Lang.t("btn.open"), "/pvpcalc matchover", Lang.t("tip.open_summary")))
                    .append(Text.literal(" ")).append(Chat.link(Lang.t("btn.copy"), "/pvpcalc report copy", Lang.t("tip.copy_report"))));
            if (!m.deathInfo.isEmpty()) Chat.reply(Text.literal(m.deathInfo).formatted(Formatting.RED));
        }
        Chat.flushQuiet();
        CsvStore.flush();
    }

    private static void writeMatchRows(MatchData m) {
        if (!Settings.bool("enabled")) return;
        StringBuilder opp = new StringBuilder();
        for (PlayerData p : m.players.values()) { if (opp.length() > 0) opp.append(" + "); opp.append(p.name); }
        String t = CsvStore.MATCHES;
        CsvStore.appendRow(t, CsvStore.MATCHES_HEAD, String.valueOf(m.id), m.kit, m.result, opp.toString(),
                CsvStore.num(t, m.dealt), CsvStore.num(t, m.taken), String.valueOf(m.swings), String.valueOf(m.hits),
                CsvStore.num(t, m.accuracy()), String.valueOf(m.crits), CsvStore.num(t, m.critPct()), CsvStore.num(t, m.maxHit),
                String.valueOf(m.combos), String.valueOf(m.maxCombo), String.valueOf(m.myGapples), String.valueOf(m.myTotems),
                m.avgReaction() < 0 ? "" : CsvStore.num(t, m.avgReaction()), m.style(), CsvStore.num(t, m.spacingPct()),
                m.deathInfo, CsvStore.num(t, m.matchHack()));
        String pt = CsvStore.PLAYERS;
        for (PlayerData p : m.players.values()) {
            CsvStore.appendRow(pt, CsvStore.PLAYERS_HEAD, String.valueOf(m.id), m.kit, p.name, CsvStore.num(pt, p.dealt), CsvStore.num(pt, p.taken),
                    String.valueOf(p.hits), String.valueOf(p.crits), CsvStore.num(pt, p.critPct()), String.valueOf(p.theirHits),
                    String.valueOf(p.combos), String.valueOf(p.maxCombo), String.valueOf(p.theirMaxCombo), String.valueOf(p.totems),
                    String.valueOf(p.gapples), String.valueOf(p.enchGapples), String.valueOf(p.pearls), String.valueOf(p.xp),
                    String.valueOf(p.potions), CsvStore.num(pt, p.avgDist()), CsvStore.num(pt, p.hackPct), p.died ? "yes" : "no");
        }
    }

    /** live_match.csv: current totals per player, refreshed once a second when something changed. */
    private static void writeLive() {
        MatchData m = cur;
        if (m == null || !Settings.bool("enabled")) return;
        List<String[]> rows = new ArrayList<>();
        String t = CsvStore.LIVE;
        rows.add(new String[]{String.valueOf(m.id), m.kit, "ALL", CsvStore.num(t, m.dealt), CsvStore.num(t, m.taken), String.valueOf(m.hits),
                CsvStore.num(t, m.critPct()), "", String.valueOf(m.myGapples), "", "", String.valueOf(m.maxCombo), CsvStore.num(t, m.matchHack())});
        for (PlayerData p : m.players.values()) {
            rows.add(new String[]{String.valueOf(m.id), m.kit, p.name, CsvStore.num(t, p.dealt), CsvStore.num(t, p.taken), String.valueOf(p.hits),
                    CsvStore.num(t, p.critPct()), String.valueOf(p.totems), String.valueOf(p.gapples + p.enchGapples), String.valueOf(p.pearls),
                    String.valueOf(p.xp), String.valueOf(p.maxCombo), CsvStore.num(t, HackDetector.chanceOf(p.uuid))});
        }
        CsvStore.writeTable(t, CsvStore.LIVE_HEAD, rows);
    }

    public static void onDisconnect() {
        if (cur != null && hasActivity(cur)) end("UNKNOWN", "disconnect"); else cur = null;
        prevHp = -1;
        myDeadHandled = false;
        dangerTick = -1;
        myPopTick = -1;
        pendingResult = null;
        meHurt = false;
        lastTargetUuid = null;
        lastHitTick = -1000;
        LAST_MY_HIT.clear();
        LAST_THEIR_HIT.clear();
    }

    public static double pctOf(double v) { return Num.pct(v); }
}
