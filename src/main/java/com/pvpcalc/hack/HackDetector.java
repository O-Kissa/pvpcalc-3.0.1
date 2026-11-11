package com.pvpcalc.hack;

import com.pvpcalc.core.Exempt;
import com.pvpcalc.core.HackModel;
import com.pvpcalc.core.KitDefs;
import com.pvpcalc.core.Lang;
import com.pvpcalc.core.Limits;
import com.pvpcalc.core.MatchData;
import com.pvpcalc.core.Num;
import com.pvpcalc.core.Persist;
import com.pvpcalc.core.PlayerData;
import com.pvpcalc.core.Settings;
import com.pvpcalc.core.Track;
import com.pvpcalc.logging.CsvStore;
import com.pvpcalc.tracking.MatchManager;
import com.pvpcalc.tracking.Opp;
import com.pvpcalc.tracking.PlayerFilter;
import com.pvpcalc.tracking.StatsStore;
import com.pvpcalc.util.Chat;
import com.pvpcalc.util.Clock;
import com.pvpcalc.util.Guard;
import com.pvpcalc.util.Hooks;
import com.pvpcalc.util.Sounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Client-side movement/click analysis of nearby real players. Everything shown is an estimate, not proof.
 * Works with UUIDs, so a name change does not mix results up.
 */
public final class HackDetector {
    private static final class St {
        final String uuid;
        String name;
        final Track track = new Track();
        final HackModel model = new HackModel();
        final Map<String, String> info = new HashMap<>();
        final Map<String, Double> lastAlert = new HashMap<>();
        float prevSwing;
        long lastSwingTick = -100, windUntil, explUntil, pearlUntil, lungeUntil, seenTick, maxYTick;
        double lastRowSec = -1000, lastLagSec = -1000, maxY = -1e9, peak;
        String peakTop = "";
        boolean summarized;
        St(String uuid, String name) { this.uuid = uuid; this.name = name; }
        boolean samplesOk() { return model.samples >= Settings.i("hack.minSamples"); }
    }

    private static final Map<String, St> STATES = new LinkedHashMap<>();
    private static final Set<String> TRACKED = new LinkedHashSet<>();
    private static long graceUntil, skipUntil;
    private static boolean spNotice;
    private static final List<Vec3d> WINDS = new ArrayList<>(), ROCKETS = new ArrayList<>(), PEARLS = new ArrayList<>();

    private HackDetector() {}

    // ---------------- public API ----------------

    /** Dimension change, respawn or join: ignore speed/teleport style results for a few ticks. */
    public static void grace() {
        graceUntil = Clock.tick() + 60;
    }

    public static void resetState() {
        STATES.clear();
        TRACKED.clear();
        WINDS.clear(); ROCKETS.clear(); PEARLS.clear();
        spNotice = false;
        grace();
    }

    public static void clearHistoryOnly() {
        for (St s : STATES.values()) s.track.consumeErrors();
        STATES.clear();
        grace();
    }

    public static void onDisconnect() {
        for (St s : new ArrayList<>(STATES.values())) summary(s);
        resetState();
    }

    public static boolean track(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = Opp.byName(mc, name);
        if (p == null || !PlayerFilter.real(mc, p)) return false;
        TRACKED.add(p.getUuid().toString());
        return true;
    }

    public static boolean untrack(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String uuid = null;
        for (St s : STATES.values()) if (s.name.equalsIgnoreCase(name)) uuid = s.uuid;
        if (uuid == null) {
            PlayerEntity p = Opp.byName(mc, name);
            if (p != null) uuid = p.getUuid().toString();
        }
        if (uuid == null || !TRACKED.remove(uuid)) return false;
        St s = STATES.get(uuid);
        if (s != null) summary(s);
        return true;
    }

    public static void untrackAll() {
        for (String u : new ArrayList<>(TRACKED)) {
            St s = STATES.get(u);
            if (s != null) summary(s);
        }
        TRACKED.clear();
    }

    public static void onMyHit(PlayerEntity target) {
        if (!Settings.bool("enabled") || !Settings.bool("hack.enabled")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!PlayerFilter.real(mc, target)) return;
        String uuid = target.getUuid().toString();
        if (Settings.bool("hack.autoTrackHit") && TRACKED.size() < Settings.i("hack.maxAnalyzed")) TRACKED.add(uuid);
        St st = STATES.get(uuid);
        if (st != null) st.track.noteHit(Clock.tick(), target.getX(), target.getZ());
    }

    public static void onPop(PlayerEntity p) {
        St st = STATES.get(p.getUuid().toString());
        if (st != null) st.track.notePop(Clock.tick());
    }

    public static void onExplosion(Vec3d c) {
        long until = Clock.tick() + 40;
        for (St s : STATES.values()) {
            PlayerEntity p = Opp.byUuid(MinecraftClient.getInstance(), s.uuid);
            if (p != null && p.squaredDistanceTo(c) < 15 * 15) s.explUntil = until;
        }
    }

    public static int cpsOf(String uuid) {
        St s = STATES.get(uuid);
        return s == null ? 0 : s.track.cpsAt(Clock.tick());
    }

    public static boolean samplesEnough(String uuid) {
        St s = STATES.get(uuid);
        return s != null && s.samplesOk();
    }

    public static String confidenceOf(String uuid) {
        St s = STATES.get(uuid);
        return s == null ? "" : confidence(s);
    }

    public static double chanceOf(String uuid) {
        St s = STATES.get(uuid);
        return s == null ? 0.0 : s.model.total(Clock.seconds(), HackModel.Cfg.fromSettings());
    }

    public static void fill(PlayerData pd) {
        St s = STATES.get(pd.uuid);
        if (s == null) return;
        HackModel.Cfg cfg = HackModel.Cfg.fromSettings();
        pd.hackPct = s.samplesOk() ? s.model.total(Clock.seconds(), cfg) : 0.0;
        pd.hackSamples = s.model.samples;
        pd.hackTop = s.model.top(Clock.seconds(), cfg);
    }

    public static void matchEnded(MatchData m) {
        for (PlayerData pd : m.players.values()) {
            St s = STATES.get(pd.uuid);
            if (s != null) summary(s);
        }
    }

    public static List<String> trackedNames() {
        List<String> l = new ArrayList<>();
        for (String u : TRACKED) {
            St s = STATES.get(u);
            l.add(s != null ? s.name : u.substring(0, 8));
        }
        return l;
    }

    public static boolean isWhitelisted(MinecraftClient mc, PlayerEntity p) {
        if (mc.player != null && Settings.bool("hack.autoWhitelistTeam") && mc.player.isTeammate(p)) return true;
        String wl = Settings.str("hack.whitelist").toLowerCase(Locale.ROOT);
        if (wl.isEmpty()) return false;
        String name = p.getName().getString().toLowerCase(Locale.ROOT), uuid = p.getUuid().toString();
        for (String t : wl.split(",")) {
            String x = t.trim();
            if (!x.isEmpty() && (x.equals(name) || x.equals(uuid))) return true;
        }
        return false;
    }

    // ---------------- status text ----------------

    public static List<String> statusLines() {
        List<String> l = new ArrayList<>();
        HackModel.Cfg cfg = HackModel.Cfg.fromSettings();
        double now = Clock.seconds();
        l.add("\u00a76" + Lang.t("hack.status_head", Settings.bool("hack.enabled") ? "ON" : "OFF", Settings.str("hack.sensitivity"),
                Settings.i("hack.alertThreshold"), Num.fmt1(Hooks.tps())));
        if (TRACKED.isEmpty()) l.add("\u00a77" + Lang.t("hack.none_tracked"));
        for (String u : TRACKED) {
            St s = STATES.get(u);
            if (s == null) { l.add("\u00a77 - " + u.substring(0, 8) + " (" + Lang.t("hack.not_near") + ")"); continue; }
            l.add(line(s, now, cfg));
        }
        return l;
    }

    public static List<String> checkLines(String name) {
        List<String> l = new ArrayList<>();
        HackModel.Cfg cfg = HackModel.Cfg.fromSettings();
        double now = Clock.seconds();
        St st = null;
        for (St s : STATES.values()) if (s.name.equalsIgnoreCase(name)) st = s;
        if (st == null) { l.add("\u00a7c" + Lang.t("hack.no_data", name)); return l; }
        l.add(line(st, now, cfg));
        for (String c : Settings.CHECKS) {
            double v = st.model.chance(c, now, cfg);
            int n = st.model.violationCount(c);
            if (v > 0 || n > 0) l.add("\u00a77   " + Lang.t("chk." + c) + ": \u00a7f" + Num.fmt1(v) + "% \u00a78(" + n + " " + Lang.t("hack.flags") + ")"
                    + (st.info.containsKey(c) ? " \u00a77" + st.info.get(c) : ""));
        }
        return l;
    }

    private static String confidence(St s) {
        int games = games(s.uuid);
        boolean high = s.model.highConfidence(Settings.i("hack.minSamples")) && games >= 2;
        return Lang.t(high ? "hack.conf_high" : "hack.conf_low", games);
    }

    private static int games(String uuid) {
        Map<String, Persist.VsRec> m = StatsStore.p.vs.get(uuid);
        int n = 0;
        if (m != null) for (Persist.VsRec r : m.values()) n += r.wins + r.losses;
        return n;
    }

    private static String line(St s, double now, HackModel.Cfg cfg) {
        double c = s.model.total(now, cfg);
        String col = c >= Settings.i("hack.alertThreshold") ? "\u00a7c" : (c >= 40 ? "\u00a7e" : "\u00a7a");
        String shown = s.samplesOk() ? col + Num.fmt1(c) + "%" : "\u00a77" + Lang.t("hack.collecting", s.model.samples, Settings.i("hack.minSamples"));
        return "\u00a7f " + s.name + ": " + shown + " \u00a78(" + confidence(s) + ")" + (StatsStore.isRepeatSuspect(s.uuid) ? " \u00a7c[" + Lang.t("hack.repeat") + "]" : "");
    }

    // ---------------- per tick ----------------

    private static boolean serverBlocked(MinecraftClient mc) {
        ServerInfo si = mc.getCurrentServerEntry();
        String rules = Settings.str("serverRules").toLowerCase(Locale.ROOT);
        if (si == null || rules.isEmpty()) return false;
        String addr = si.address.toLowerCase(Locale.ROOT);
        for (String t : rules.split(",")) if (!t.trim().isEmpty() && addr.contains(t.trim())) return true;
        return false;
    }

    public static void tick(MinecraftClient mc) {
        if (!Settings.bool("enabled") || !Settings.bool("hack.enabled") || Guard.isOff("hack")) return;
        ClientPlayerEntity me = mc.player;
        ClientWorld w = mc.world;
        if (me == null || w == null) return;
        if (mc.isIntegratedServerRunning()) {
            if (!spNotice) { spNotice = true; Chat.say("hack.singleplayer"); }
            return;
        }
        if (serverBlocked(mc)) return;
        long now = Clock.tick();
        if (now < skipUntil) return;
        int every = Settings.bool("performanceMode") ? 3 : 1;
        if (now % every != 0) return;
        long t0 = System.nanoTime();

        scan(w);
        List<PlayerEntity> list = select(mc);
        for (PlayerEntity p : list) {
            final PlayerEntity pp = p;
            try {
                analyze(mc, pp, pp == me);
            } catch (RuntimeException ex) {
                Guard.count("hack", ex);
            }
        }
        if (MatchManager.meHurt) {
            try { reachCheck(mc, me); } catch (RuntimeException ex) { Guard.count("hack", ex); }
        }
        if (now % 20 == 0) {
            for (St s : new ArrayList<>(STATES.values())) {
                try { evaluate(mc, s); } catch (RuntimeException ex) { Guard.count("hack", ex); }
            }
            cleanup(now);
        }
        double ms = (System.nanoTime() - t0) / 1.0e6;
        if (ms > Settings.i("tickBudgetMs")) skipUntil = now + 10; // too slow: back off briefly
    }

    private static void scan(ClientWorld w) {
        WINDS.clear(); ROCKETS.clear(); PEARLS.clear();
        for (Entity e : w.getEntities()) {
            EntityType<?> t = e.getType();
            if (t == EntityType.WIND_CHARGE || t == EntityType.BREEZE_WIND_CHARGE) WINDS.add(new Vec3d(e.getX(), e.getY(), e.getZ()));
            else if (t == EntityType.FIREWORK_ROCKET) ROCKETS.add(new Vec3d(e.getX(), e.getY(), e.getZ()));
            else if (t == EntityType.ENDER_PEARL) PEARLS.add(new Vec3d(e.getX(), e.getY(), e.getZ()));
        }
    }

    private static List<PlayerEntity> select(MinecraftClient mc) {
        List<PlayerEntity> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int max = Settings.i("hack.maxAnalyzed");
        for (String u : TRACKED) {
            PlayerEntity p = Opp.byUuid(mc, u);
            if (p != null && !isWhitelisted(mc, p) && seen.add(u)) out.add(p);
        }
        if (Settings.bool("hack.autoTrackNearest")) {
            double r = Settings.i("hack.autoTrackRadius");
            List<PlayerEntity> near = new ArrayList<>();
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (PlayerFilter.real(mc, p) && p.distanceTo(mc.player) <= r && !isWhitelisted(mc, p)) near.add(p);
            }
            near.sort((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)));
            for (PlayerEntity p : near) if (out.size() < max && seen.add(p.getUuid().toString())) out.add(p);
        }
        while (out.size() > max) out.remove(out.size() - 1);
        if (Settings.bool("hack.training") && mc.player != null) out.add(mc.player);
        return out;
    }

    private static boolean near(List<Vec3d> l, PlayerEntity p, double r) {
        for (Vec3d v : l) if (p.squaredDistanceTo(v) < r * r) return true;
        return false;
    }

    private static int exemptMask(MinecraftClient mc, PlayerEntity p, St st, long now, boolean self) {
        int m = 0;
        if (p.isGliding()) m |= Exempt.ELYTRA;
        if (p.hasVehicle()) m |= Exempt.VEHICLE;
        if (p.isTouchingWater()) m |= Exempt.WATER;
        if (p.isInLava()) m |= Exempt.LAVA;
        if (p.isClimbing()) m |= Exempt.CLIMB;
        if (p.hasStatusEffect(StatusEffects.LEVITATION)) m |= Exempt.LEVITATION;
        if (p.hasStatusEffect(StatusEffects.SLOW_FALLING)) m |= Exempt.SLOWFALL;
        if (PlayerFilter.creativeOrSpectator(mc, p)) m |= Exempt.GAMEMODE;
        if (near(WINDS, p, 7)) st.windUntil = now + 60;
        if (near(ROCKETS, p, 6)) m |= Exempt.FIREWORK;
        if (near(PEARLS, p, 4)) st.pearlUntil = now + 60;
        if (now < st.windUntil) m |= Exempt.WIND;
        if (now < st.pearlUntil) m |= Exempt.PEARL;
        if (now < st.explUntil) m |= Exempt.EXPLOSION;
        if (now < graceUntil) m |= Exempt.RESPAWN;
        if (p.isUsingItem() && DamageCalcBridge.isSpear(p.getActiveItem())) st.lungeUntil = now + 30;
        if (now < st.lungeUntil) m |= Exempt.LUNGE;
        try {
            BlockPos bp = p.getBlockPos();
            BlockState below = mc.world.getBlockState(bp.down());
            BlockState at = mc.world.getBlockState(bp);
            if (below.isOf(Blocks.SLIME_BLOCK) || below.isOf(Blocks.HAY_BLOCK) || below.isOf(Blocks.HONEY_BLOCK)) m |= Exempt.SOFT | Exempt.BLOCK;
            if (at.isOf(Blocks.COBWEB) || below.isOf(Blocks.ICE) || below.isOf(Blocks.PACKED_ICE) || below.isOf(Blocks.BLUE_ICE)
                    || below.isOf(Blocks.SOUL_SAND) || at.isOf(Blocks.SWEET_BERRY_BUSH)) m |= Exempt.BLOCK;
        } catch (RuntimeException ignored) {
            // chunk not loaded: treat as unknown block, no exemption
        }
        int ping = PlayerFilter.ping(mc, p);
        if (Settings.bool("hack.pingComp") && ping > Settings.i("hack.pingLimit")) m |= Exempt.LAG;
        if (Settings.bool("hack.tpsComp") && Hooks.tps() < 12.0) m |= Exempt.LAG;
        return m & Exempt.mask(Settings.str("hack.falsePositives"));
    }

    private static Limits limits(MinecraftClient mc, PlayerEntity p) {
        int ping = PlayerFilter.ping(mc, p);
        Limits L = Limits.from(MatchManager.kitContext(), Settings.is("combatSystem", "1.8"), Hooks.tps(), ping < 0 ? 100 : ping);
        for (String c : Settings.CHECKS) if (Guard.isOff("check:" + c)) L.disable(c);
        return L;
    }

    private static double weight(String c) {
        return Settings.dbl("chk." + c + ".weight");
    }

    private static void analyze(MinecraftClient mc, PlayerEntity p, boolean self) {
        long now = Clock.tick();
        String uuid = p.getUuid().toString();
        St st = STATES.computeIfAbsent(uuid, k -> new St(uuid, p.getName().getString()));
        st.name = p.getName().getString();
        st.seenTick = now;
        st.summarized = false;

        float prog = p.getHandSwingProgress(1.0F);
        boolean swing = prog > 0.0F && (st.prevSwing <= 0.0F || prog < st.prevSwing - 0.001F);
        st.prevSwing = prog;
        if (swing) st.lastSwingTick = now;

        if (p.getY() > st.maxY || now - st.maxYTick > 40) { st.maxY = p.getY(); st.maxYTick = now; }

        Track.Sample s = new Track.Sample();
        s.tick = now;
        s.x = p.getX(); s.y = p.getY(); s.z = p.getZ();
        s.yaw = p.getHeadYaw(); s.pitch = p.getPitch();
        s.onGround = p.isOnGround();
        s.swing = swing;
        s.hurt = p.hurtTime;
        s.offTotem = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        StatusEffectInstance sp = p.getStatusEffect(StatusEffects.SPEED);
        if (sp != null) s.speedAmp = sp.getAmplifier();
        StatusEffectInstance jb = p.getStatusEffect(StatusEffects.JUMP_BOOST);
        if (jb != null) s.jumpAmp = jb.getAmplifier();
        s.exempt = exemptMask(mc, p, st, now, self);

        HackModel.Cfg cfg = HackModel.Cfg.fromSettings();
        List<Track.Viol> vs = st.track.push(s, limits(mc, p));
        st.model.samples++;
        for (String c : st.track.consumeErrors()) Guard.count("check:" + c, new RuntimeException("check " + c + " failed"));
        double sec = Clock.seconds();
        for (Track.Viol v : vs) {
            double pts = v.points;
            if (Settings.bool("hack.baseline") && v.check.equals("autoclicker")) pts *= st.model.cpsBase.factor(st.track.lastCps, 30);
            st.model.violation(v.check, pts, weight(v.check), sec, cfg);
            st.info.put(v.check, v.info);
        }
        if (swing && st.track.lastCps > 0) st.model.cpsBase.add(st.track.lastCps);
        if (!self) st.model.speedBase.add(st.track.lastSpeed);
    }

    private static void reachCheck(MinecraftClient mc, ClientPlayerEntity me) {
        long now = Clock.tick();
        HackModel.Cfg cfg = HackModel.Cfg.fromSettings();
        St best = null;
        PlayerEntity bp = null;
        double bd = 1e9;
        for (St s : STATES.values()) {
            if (now - s.lastSwingTick > 3) continue;
            PlayerEntity p = Opp.byUuid(mc, s.uuid);
            if (p == null || p == me || isWhitelisted(mc, p)) continue;
            double d = distanceToBox(p.getEyePos(), me.getBoundingBox());
            if (d < bd) { bd = d; best = s; bp = p; }
        }
        if (best == null || bp == null || bd > 8.0) return;
        int ping = PlayerFilter.ping(mc, bp);
        Limits L = limits(mc, bp);
        int ex = exemptMask(mc, bp, best, now, false);
        Track.Viol v = best.track.reach(bd, ping < 0 ? 100 : ping, L, ex);
        if (v != null) { best.model.violation("reach", v.points, weight("reach"), Clock.seconds(), cfg); best.info.put("reach", v.info); }
        if (DamageCalcBridge.isMace(bp.getMainHandStack())) {
            Track.Viol m = best.track.maceHit(MatchManager.meHurtAmount, Math.max(0.0, best.maxY - bp.getY()), ex, L);
            if (m != null) { best.model.violation("mace", m.points, weight("mace"), Clock.seconds(), cfg); best.info.put("mace", m.info); }
        }
    }

    private static double distanceToBox(Vec3d p, Box b) {
        double dx = Math.max(Math.max(b.minX - p.x, 0.0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0.0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0.0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---------------- alerts ----------------

    private static double alertLimit(String check) {
        int own = Settings.i("chk." + check + ".alert");
        return own > 0 ? own : Settings.i("hack.alertThreshold");
    }

    private static void evaluate(MinecraftClient mc, St s) {
        boolean selfState = mc.player != null && s.uuid.equals(mc.player.getUuid().toString());
        HackModel.Cfg cfg = HackModel.Cfg.fromSettings();
        double now = Clock.seconds();
        double total = s.model.total(now, cfg);
        s.peak = Math.max(s.peak, total);
        if (selfState || !s.samplesOk()) return;
        int minRow = Settings.i("hack.minRowSeconds");

        PlayerEntity p = Opp.byUuid(mc, s.uuid);
        int ping = p == null ? -1 : PlayerFilter.ping(mc, p);
        boolean lag = (Settings.bool("hack.pingComp") && ping > Settings.i("hack.pingLimit")) || (Settings.bool("hack.tpsComp") && Hooks.tps() < 14.0);
        if (lag && now - s.lastLagSec >= minRow && s.model.violationsTotal > 0) {
            s.lastLagSec = now;
            if (Settings.bool("hack.logCsv")) CsvStore.event("HACK LAG", s.name, Lang.t("hack.lag_row", ping, Num.fmt1(Hooks.tps())), Double.NaN);
        }

        String topCheck = null;
        double topVal = 0;
        for (String c : Settings.CHECKS) {
            double v = s.model.chance(c, now, cfg);
            if (v >= alertLimit(c) && v > topVal) { topVal = v; topCheck = c; }
        }
        if (topCheck == null && total >= Settings.i("hack.alertThreshold")) { topCheck = s.model.top(now, cfg); topVal = total; }
        if (topCheck == null || topCheck.isEmpty()) return;
        Double last = s.lastAlert.get(topCheck);
        if (last != null && now - last < minRow) return;
        s.lastAlert.put(topCheck, now);
        alert(s, topCheck, Math.max(topVal, total), false, now - s.lastRowSec >= minRow);
        if (now - s.lastRowSec >= minRow) s.lastRowSec = now;
        s.peakTop = topCheck;
    }

    private static void alert(St s, String check, double pct, boolean sim, boolean writeRow) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String mode = Settings.str("hack.alertMode");
        String reason = s.info.getOrDefault(check, "");
        String name = Lang.t("chk." + check);
        String conf = confidence(s);
        boolean repeat = StatsStore.isRepeatSuspect(s.uuid);
        String body = Lang.t("hack.alert", s.name, name, Num.fmt1(pct), reason, conf, Settings.str("disclaimer")) + (repeat ? " [" + Lang.t("hack.repeat") + "]" : "");
        boolean quiet = Settings.bool("quietMode") && MatchManager.inMatch() && !sim;
        if (!mode.equals("off")) {
            if (mode.equals("both") || mode.equals("chat")) Chat.alert(Text.literal(body).formatted(Formatting.RED));
            if (!quiet) {
                if (Settings.bool("hack.centerText") && mc.inGameHud != null) {
                    mc.inGameHud.setTitleTicks(5, 50, 10);
                    mc.inGameHud.setTitle(Text.literal(name.toUpperCase(Locale.ROOT)).formatted(Formatting.RED, Formatting.BOLD));
                    mc.inGameHud.setSubtitle(Text.literal(s.name + " - " + Num.fmt1(pct) + "% " + reason).formatted(Formatting.YELLOW));
                }
                if (mode.equals("both") || mode.equals("sound")) Sounds.play(Settings.str("hack.alertSound"), 1.0F, 1.0F);
            }
        }
        if (!sim && writeRow && Settings.bool("hack.logCsv") && s.model.changed("alert", (int) pct)) {
            CsvStore.event("HACK ALERT", s.name, name + ": " + reason + " | " + conf + " | " + Settings.str("disclaimer")
                    + (repeat ? " | repeat suspect" : ""), pct);
        }
    }

    private static void summary(St s) {
        if (s.summarized || s.model.samples <= 0) return;
        s.summarized = true;
        if (!Settings.bool("enabled") || !Settings.bool("hack.logCsv") || !s.samplesOk()) return;
        HackModel.Cfg cfg = HackModel.Cfg.fromSettings();
        double now = Clock.seconds();
        double total = s.model.total(now, cfg);
        double peak = Math.max(s.peak, total);
        String top = s.model.top(now, cfg);
        if (top.isEmpty()) top = s.peakTop;
        CsvStore.event("HACK SUMMARY", s.name, Lang.t("hack.summary_row", Num.fmt1(peak), top.isEmpty() ? "-" : Lang.t("chk." + top),
                s.model.violationsTotal, confidence(s), s.model.samples), Math.max(peak, total));
    }

    private static void cleanup(long now) {
        List<String> drop = new ArrayList<>();
        for (St s : STATES.values()) if (now - s.seenTick > 20 * 60 && !TRACKED.contains(s.uuid)) drop.add(s.uuid);
        for (String u : drop) {
            summary(STATES.get(u));
            STATES.remove(u);
        }
    }

    // ---------------- simulation ----------------

    private static void feed(St s, Track.Sample smp, Limits L, double sec, HackModel.Cfg cfg) {
        for (Track.Viol v : s.track.push(smp, L)) {
            s.model.violation(v.check, v.points, weight(v.check), sec, cfg);
            s.info.put(v.check, v.info);
        }
        s.model.samples++;
    }

    private static Track.Sample smp(long t, double x, double y, double z, double yaw, boolean ground, boolean swing) {
        Track.Sample s = new Track.Sample();
        s.tick = t; s.x = x; s.y = y; s.z = z; s.yaw = yaw; s.onGround = ground; s.swing = swing;
        return s;
    }

    /** Feeds synthetic data through the real pipeline so you can see an alert without a real cheater. */
    public static boolean simulate(String scenario) {
        String sc = scenario.toLowerCase(Locale.ROOT);
        List<String> valid = List.of("speed", "autoclicker", "fly", "aimbot", "reach", "velocity", "nofall", "autototem", "mace");
        if (!valid.contains(sc)) return false;
        St s = new St("sim-" + sc, "SimBot");
        s.model.samples = 999;
        Limits L = Limits.from(MatchManager.kitContext(), Settings.is("combatSystem", "1.8"), 20.0, 30.0);
        L.enableAll();
        HackModel.Cfg cfg = HackModel.Cfg.fromSettings();
        double t0 = Clock.seconds();
        double x = 0, yaw = 0;
        for (int t = 0; t < 400; t++) {
            double sec = t0 + t / 20.0;
            Track.Sample smp;
            switch (sc) {
                case "speed": x += 0.75; smp = smp(t, x, 64, 0, 0, true, false); break;
                case "autoclicker": smp = smp(t, 0, 64, 0, 0, true, t % 4 != 3); break;
                case "fly": smp = smp(t, 0, 80, 0, 0, false, false); break;
                case "aimbot": if (t % 10 == 0) yaw += 150; smp = smp(t, 0, 64, 0, yaw, true, t % 10 == 1); break;
                case "nofall": smp = smp(t, 0, t % 60 < 20 ? 100 - (t % 60) * 1.5 : 70, 0, 0, t % 60 >= 20, false); break;
                case "autototem": {
                    smp = smp(t, 0, 64, 0, 0, true, false);
                    if (t % 40 == 0) s.track.notePop(t);
                    smp.offTotem = t % 40 >= 1;
                    break;
                }
                default: smp = smp(t, 0, 64, 0, 0, true, false);
            }
            if (sc.equals("velocity") && t % 30 == 0) s.track.noteHit(t, 0, 0);
            feed(s, smp, L, sec, cfg);
            if (sc.equals("reach") && t % 20 == 0) {
                Track.Viol v = s.track.reach(4.8, 40, L, 0);
                if (v != null) { s.model.violation("reach", v.points, weight("reach"), sec, cfg); s.info.put("reach", v.info); }
            }
            if (sc.equals("mace") && t % 20 == 0) {
                Track.Viol v = s.track.maceHit(14.0, 0.1, 0, L);
                if (v != null) { s.model.violation("mace", v.points, weight("mace"), sec, cfg); s.info.put("mace", v.info); }
            }
        }
        double end = t0 + 20.0;
        double pct = s.model.total(end, cfg);
        String top = s.model.top(end, cfg);
        Chat.reply("hack.sim_result", sc, Num.fmt1(pct), top.isEmpty() ? "-" : Lang.t("chk." + top), s.model.violationsTotal);
        if (!top.isEmpty()) alert(s, top, pct, true, false);
        return true;
    }
}
