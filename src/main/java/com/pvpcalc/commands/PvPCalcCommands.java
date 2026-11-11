package com.pvpcalc.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.pvpcalc.PvPCalcClient;
import com.pvpcalc.combat.DamageCalculator;
import com.pvpcalc.combat.Gear;
import com.pvpcalc.combat.MaceCalculator;
import com.pvpcalc.config.ConfigIo;
import com.pvpcalc.core.CsvUtil;
import com.pvpcalc.core.DamageMath;
import com.pvpcalc.core.Fuzzy;
import com.pvpcalc.core.KitDefs;
import com.pvpcalc.core.Lang;
import com.pvpcalc.core.MatchData;
import com.pvpcalc.core.Num;
import com.pvpcalc.core.Persist;
import com.pvpcalc.core.Predict;
import com.pvpcalc.core.Report;
import com.pvpcalc.core.SettingText;
import com.pvpcalc.core.Settings;
import com.pvpcalc.gui.MatchSummaryScreen;
import com.pvpcalc.gui.SettingsScreen;
import com.pvpcalc.hack.HackDetector;
import com.pvpcalc.input.KeyBindings;
import com.pvpcalc.logging.CsvStore;
import com.pvpcalc.tracking.MatchManager;
import com.pvpcalc.tracking.Opp;
import com.pvpcalc.tracking.PlayerFilter;
import com.pvpcalc.tracking.StatsStore;
import com.pvpcalc.util.Chat;
import com.pvpcalc.util.Dirs;
import com.pvpcalc.util.Music;
import com.pvpcalc.util.SelfCheck;
import com.pvpcalc.util.Update;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * All /pvpcalc commands. Command words work in English and Finnish, in any letter case; an unknown word gets a
 * "did you mean" hint. /pvp and /hack are short aliases.
 */
public final class PvPCalcCommands {
    private static final Map<String, String> FI = new HashMap<>();
    private static CommandDispatcher<FabricClientCommandSource> dispatcher;
    private static boolean redispatching;
    private static long resetRequest = -1;

    static {
        String[][] p = {{"ohje", "help"}, {"tila", "status"}, {"asetukset", "settings"}, {"kieli", "language"}, {"tilastot", "stats"},
                {"yhteenveto", "matchover"}, {"nollaa", "reset"}, {"paalle", "on"}, {"pois", "off"}, {"laske", "calc"}, {"varuste", "gear"},
                {"suosittele", "recommend"}, {"tarkista", "check"}, {"musiikki", "music"}, {"uusi", "newmatch"}, {"hiljainen", "quiet"},
                {"vaikeustaso", "difficulty"}, {"kilpi", "shield"}, {"kohde", "target"}, {"pyoristys", "decimals"}, {"yksikko", "units"},
                {"tavoite", "goal"}, {"kehitys", "trend"}, {"ennatykset", "records"}, {"paivita", "update"}, {"versio", "version"},
                {"raportti", "report"}, {"putket", "streaks"}, {"nayta", "show"}, {"seuraa", "track"}, {"lopeta", "untrack"},
                {"herkkyys", "sensitivity"}, {"kynnys", "threshold"}, {"sallitut", "whitelist"}, {"harjoitus", "training"},
                {"simuloi", "simulate"}, {"toista", "play"}, {"pysayta", "stop"}, {"lista", "list"}, {"vie", "export"}, {"tuo", "import"},
                {"vahvista", "confirm"}, {"kopioi", "copy"}, {"asetus", "set"}, {"hae", "get"}, {"nappaimet", "keys"}};
        for (String[] e : p) FI.put(e[0], e[1]);
    }

    private PvPCalcCommands() {}

    // ---------------- registration ----------------

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, access) -> {
            dispatcher = d;
            LiteralCommandNode<FabricClientCommandSource> root = d.register(root());
            d.register(literal("pvp").redirect(root));
            CommandNode<FabricClientCommandSource> hack = root.getChild("hack");
            if (hack != null) d.register(literal("hack").redirect(hack));
        });
    }

    private static final SuggestionProvider<FabricClientCommandSource> NONE = (c, b) -> b.buildFuture();

    private static LiteralArgumentBuilder<FabricClientCommandSource> fb(LiteralArgumentBuilder<FabricClientCommandSource> b) {
        return b.then(argument("rest", StringArgumentType.greedyString()).suggests(NONE).executes(PvPCalcCommands::fallback));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> root() {
        LiteralArgumentBuilder<FabricClientCommandSource> r = literal("pvpcalc").executes(c -> { status(false); return 1; });
        r.then(literal("on").executes(c -> { setOn(true); return 1; }));
        r.then(literal("off").executes(c -> { setOn(false); return 1; }));
        r.then(literal("reset").executes(c -> { resetCsv(); return 1; }));
        r.then(literal("help").executes(c -> { help(); return 1; }));
        r.then(literal("status").executes(c -> { status(false); return 1; })
                .then(literal("all").executes(c -> { status(true); return 1; })));
        r.then(literal("settings").executes(c -> { PvPCalcClient.pendingScreen = new SettingsScreen(null); return 1; }));
        r.then(literal("keys").executes(c -> { for (String l : KeyBindings.lines()) Chat.send(Text.literal("\u00a77 " + l)); return 1; }));
        r.then(config());
        r.then(setting("language", "language"));
        r.then(literal("active_kit").then(kitArg()));
        r.then(literal("kit").then(kitArg()));
        r.then(pvp());
        r.then(literal("version").executes(c -> { showSetting("combatSystem"); return 1; })
                .then(argument("value", StringArgumentType.greedyString())
                        .suggests((c, b) -> CommandSource.suggestMatching(List.of("1.8", "1.9+"), b))
                        .executes(c -> { setSetting("combatSystem", StringArgumentType.getString(c, "value").equals("1.9") ? "1.9+" : StringArgumentType.getString(c, "value")); return 1; })));
        r.then(setting("target", "target"));
        r.then(setting("shield", "assumeShield"));
        r.then(setting("difficulty", "difficulty"));
        r.then(setting("units", "healthUnit"));
        r.then(setting("decimals", "decimals"));
        r.then(setting("hud", "hud.visible"));
        r.then(setting("hphud", "hud.oppHp"));
        r.then(setting("durability", "hud.durability"));
        r.then(setting("quiet", "quietMode"));
        r.then(setting("smp", "smpMode"));
        r.then(setting("performance", "performanceMode"));
        r.then(setting("presets", "serverPresets"));
        r.then(setting("updates", "updateNotice"));
        r.then(literal("sound").then(setting("pop", "soundPop")).then(setting("kill", "soundKill")));
        r.then(literal("calc").executes(c -> { calc(); return 1; }));
        r.then(literal("gear").executes(c -> { gear(false); return 1; }).then(literal("target").executes(c -> { gear(true); return 1; })));
        r.then(literal("stats").executes(c -> { stats(); return 1; }));
        r.then(literal("matchover").executes(c -> { matchOver(); return 1; }));
        r.then(literal("report").executes(c -> { report(false); return 1; }).then(literal("copy").executes(c -> { report(true); return 1; })));
        r.then(literal("newmatch").executes(c -> { MatchManager.startMatch(Settings.str("kit")); Chat.reply("msg.newmatch"); return 1; }));
        r.then(literal("end").executes(c -> { MatchManager.end("UNKNOWN", "manual"); return 1; }));
        r.then(literal("recommend").then(argument("player", StringArgumentType.word()).suggests(PvPCalcCommands::suggestKnownPlayers)
                .executes(c -> { recommend(StringArgumentType.getString(c, "player")); return 1; })));
        r.then(literal("records").executes(c -> { records(); return 1; }));
        r.then(literal("streaks").executes(c -> { streaks(); return 1; })
                .then(literal("resetall").executes(c -> { StatsStore.resetAll(); Chat.reply("msg.stats_cleared"); return 1; })));
        r.then(literal("goal").executes(c -> { goal(); return 1; }).then(setting("accuracy", "goalAccuracy")).then(setting("crit", "goalCrit")));
        r.then(literal("trend").executes(c -> { trend(); return 1; }).then(setting("window", "trendWindow")));
        r.then(music());
        r.then(literal("check").executes(c -> { for (String l : SelfCheck.run()) Chat.send(Text.literal(l)); return 1; }));
        r.then(literal("update").executes(c -> { Update.check(true); return 1; }));
        r.then(hack());
        // fallback: unknown words (wrong case, Finnish, typos) are handled here
        r.then(argument("rest", StringArgumentType.greedyString())
                .suggests((c, b) -> Lang.fi() ? CommandSource.suggestMatching(FI.keySet(), b) : b.buildFuture())
                .executes(PvPCalcCommands::fallback));
        return r;
    }

    // ---------------- builders ----------------

    private static List<String> values(String id) {
        Settings.Def d = Settings.def(id);
        List<String> v = new ArrayList<>();
        if (d == null) return v;
        switch (d.type) {
            case BOOL: v.add("on"); v.add("off"); break;
            case ENUM: for (String o : d.options) v.add(o.isEmpty() ? "none" : o); break;
            case INT: case DOUBLE: v.add(Settings.showDefault(id)); v.add(String.valueOf((int) d.min)); v.add(String.valueOf((int) d.max)); break;
            default: break;
        }
        return v;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> setting(String name, String id) {
        return literal(name).executes(c -> { showSetting(id); return 1; })
                .then(argument("value", StringArgumentType.greedyString())
                        .suggests((c, b) -> CommandSource.suggestMatching(values(id), b))
                        .executes(c -> { setSetting(id, StringArgumentType.getString(c, "value")); return 1; }));
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, String> kitArg() {
        return argument("kit", StringArgumentType.greedyString())
                .suggests((c, b) -> CommandSource.suggestMatching(KitDefs.all(), b))
                .executes(c -> { activateKit(StringArgumentType.getString(c, "kit")); return 1; });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> pvp() {
        List<String> groups = List.copyOf(KitDefs.GROUPS.keySet());
        LiteralArgumentBuilder<FabricClientCommandSource> b = literal("pvp").executes(c -> { for (String g : groups) Chat.send(menu(g)); return 1; });
        String[] names = {"netherite", "diamond", "other", "additional"};
        for (int i = 0; i < names.length; i++) {
            final String g = groups.get(i);
            b.then(literal(names[i]).executes(c -> { Chat.send(menu(g)); return 1; }));
        }
        return b;
    }

    private static MutableText menu(String group) {
        MutableText line = Text.literal(group + ": ").formatted(Formatting.GOLD);
        for (String kit : KitDefs.GROUPS.get(group)) {
            Style st = Style.EMPTY.withColor(Formatting.AQUA).withBold(true)
                    .withClickEvent(new ClickEvent.RunCommand("/pvpcalc active_kit " + kit))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal(Lang.t("tip.activate", kit))));
            line.append(Text.literal("[" + kit + "]").setStyle(st)).append(Text.literal(" "));
        }
        return line;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> config() {
        return literal("config")
                .then(literal("export").executes(c -> { export("export"); return 1; })
                        .then(argument("name", StringArgumentType.word()).executes(c -> { export(StringArgumentType.getString(c, "name")); return 1; })))
                .then(literal("import").executes(c -> { importCfg("export"); return 1; })
                        .then(argument("name", StringArgumentType.word()).executes(c -> { importCfg(StringArgumentType.getString(c, "name")); return 1; })))
                .then(literal("reset").executes(c -> { askConfigReset(); return 1; })
                        .then(literal("confirm").executes(c -> { doConfigReset(); return 1; })))
                .then(literal("get").then(argument("id", StringArgumentType.word()).suggests((c, b) -> CommandSource.suggestMatching(Settings.ids(), b))
                        .executes(c -> { showSetting(StringArgumentType.getString(c, "id")); return 1; })))
                .then(literal("set").then(argument("id", StringArgumentType.word()).suggests((c, b) -> CommandSource.suggestMatching(Settings.ids(), b))
                        .then(argument("value", StringArgumentType.greedyString())
                                .suggests((c, b) -> CommandSource.suggestMatching(values(StringArgumentType.getString(c, "id")), b))
                                .executes(c -> { setSetting(StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "value")); return 1; }))))
                .then(literal("list").executes(c -> { status(true); return 1; }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> music() {
        return literal("music")
                .then(literal("play").then(argument("track", StringArgumentType.greedyString())
                        .suggests((c, b) -> CommandSource.suggestMatching(Music.DISCS, b))
                        .executes(c -> {
                            String t = StringArgumentType.getString(c, "track");
                            if (Music.play(t)) Chat.reply("msg.music_playing", t); else Chat.reply("err.music", t);
                            return 1;
                        })))
                .then(literal("stop").executes(c -> { Music.stop(); Chat.reply("msg.music_stopped"); return 1; }))
                .then(literal("list").executes(c -> { Chat.reply("msg.music_list", String.join(", ", Music.DISCS)); return 1; }));
    }

    private static List<String> checkNames() {
        return List.of(Settings.CHECKS);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> hack() {
        LiteralArgumentBuilder<FabricClientCommandSource> h = literal("hack").executes(c -> { hackStatus(); return 1; });
        h.then(setting("detect", "hack.enabled"));
        h.then(literal("status").executes(c -> { hackStatus(); return 1; }));
        h.then(literal("track").executes(c -> { trackLooked(); return 1; })
                .then(argument("player", StringArgumentType.word()).suggests(PvPCalcCommands::suggestNearPlayers)
                        .executes(c -> { track(StringArgumentType.getString(c, "player")); return 1; })));
        h.then(literal("untrack").executes(c -> { HackDetector.untrackAll(); Chat.reply("hack.untracked_all"); return 1; })
                .then(literal("all").executes(c -> { HackDetector.untrackAll(); Chat.reply("hack.untracked_all"); return 1; }))
                .then(argument("player", StringArgumentType.word()).suggests(PvPCalcCommands::suggestTracked)
                        .executes(c -> {
                            String n = StringArgumentType.getString(c, "player");
                            if (HackDetector.untrack(n)) Chat.reply("hack.untracked", n); else Chat.reply("err.not_found_near", n);
                            return 1;
                        })));
        h.then(literal("check").then(argument("player", StringArgumentType.word()).suggests(PvPCalcCommands::suggestTracked)
                .executes(c -> { for (String l : HackDetector.checkLines(StringArgumentType.getString(c, "player"))) Chat.send(Text.literal(l)); return 1; })));
        h.then(setting("threshold", "hack.alertThreshold"));
        h.then(setting("sensitivity", "hack.sensitivity"));
        h.then(setting("mode", "hack.alertMode"));
        h.then(setting("sound", "hack.alertSound"));
        h.then(setting("auto", "hack.autoTrackHit"));
        h.then(setting("nearest", "hack.autoTrackNearest"));
        h.then(setting("center", "hack.centerText"));
        h.then(setting("training", "hack.training"));
        h.then(literal("simulate").then(argument("scenario", StringArgumentType.word())
                .suggests((c, b) -> CommandSource.suggestMatching(checkNames(), b))
                .executes(c -> {
                    String s = StringArgumentType.getString(c, "scenario");
                    if (!HackDetector.simulate(s)) Chat.reply("err.bad_scenario", s, String.join(", ", checkNames()));
                    return 1;
                })));
        h.then(literal("reset").executes(c -> { HackDetector.clearHistoryOnly(); Chat.reply("hack.history_cleared"); return 1; }));
        h.then(literal("whitelist")
                .then(literal("list").executes(c -> { Chat.reply("hack.whitelist", Settings.str("hack.whitelist").isEmpty() ? "-" : Settings.str("hack.whitelist")); return 1; }))
                .then(literal("add").then(argument("player", StringArgumentType.word()).suggests(PvPCalcCommands::suggestNearPlayers)
                        .executes(c -> { whitelist(true, StringArgumentType.getString(c, "player")); return 1; })))
                .then(literal("remove").then(argument("player", StringArgumentType.word()).suggests(PvPCalcCommands::suggestWhitelist)
                        .executes(c -> { whitelist(false, StringArgumentType.getString(c, "player")); return 1; }))));
        h.then(literal("category").then(argument("check", StringArgumentType.word()).suggests((c, b) -> CommandSource.suggestMatching(checkNames(), b))
                .executes(c -> { category(StringArgumentType.getString(c, "check"), null, null); return 1; })
                .then(argument("field", StringArgumentType.word()).suggests((c, b) -> CommandSource.suggestMatching(List.of("on", "weight", "limit", "alert"), b))
                        .then(argument("value", StringArgumentType.greedyString())
                                .executes(c -> { category(StringArgumentType.getString(c, "check"), StringArgumentType.getString(c, "field"), StringArgumentType.getString(c, "value")); return 1; })))));
        h.then(argument("rest", StringArgumentType.greedyString()).suggests(NONE).executes(PvPCalcCommands::fallback));
        return h;
    }

    // ---------------- suggestions ----------------

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestNearPlayers(
            CommandContext<FabricClientCommandSource> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        List<String> n = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) for (PlayerEntity p : mc.world.getPlayers()) if (PlayerFilter.real(mc, p)) n.add(p.getName().getString());
        return CommandSource.suggestMatching(n, b);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTracked(
            CommandContext<FabricClientCommandSource> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return CommandSource.suggestMatching(HackDetector.trackedNames(), b);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestWhitelist(
            CommandContext<FabricClientCommandSource> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        List<String> n = new ArrayList<>();
        for (String s : Settings.str("hack.whitelist").split(",")) if (!s.trim().isEmpty()) n.add(s.trim());
        return CommandSource.suggestMatching(n, b);
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestKnownPlayers(
            CommandContext<FabricClientCommandSource> c, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return CommandSource.suggestMatching(new ArrayList<>(StatsStore.p.names.values()), b);
    }

    // ---------------- fallback: case-insensitive, Finnish words, "did you mean" ----------------

    private static int fallback(CommandContext<FabricClientCommandSource> ctx) {
        String input = ctx.getInput();
        if (input.startsWith("/")) input = input.substring(1);
        String fixed = normalize(input);
        if (!fixed.equals(input) && !redispatching) {
            redispatching = true;
            try {
                dispatcher.execute(fixed, ctx.getSource());
                return 1;
            } catch (CommandSyntaxException e) {
                // fall through to the suggestion
            } finally {
                redispatching = false;
            }
        }
        suggest(input);
        return 0;
    }

    /** Fixes the case and translates Finnish command words, walking the real command tree. */
    private static String normalize(String input) {
        String[] tok = input.trim().split("\\s+");
        CommandNode<FabricClientCommandSource> node = dispatcher.getRoot();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tok.length; i++) {
            String t = tok[i];
            String fi = FI.getOrDefault(t.toLowerCase(Locale.ROOT), t);
            CommandNode<FabricClientCommandSource> next = null;
            for (CommandNode<FabricClientCommandSource> c : node.getChildren()) {
                if (c instanceof LiteralCommandNode && (c.getName().equalsIgnoreCase(t) || c.getName().equalsIgnoreCase(fi))) { next = c; break; }
            }
            if (next == null) {
                for (int k = i; k < tok.length; k++) out.append(tok[k]).append(' ');
                break;
            }
            out.append(next.getName()).append(' ');
            node = next.getRedirect() != null ? next.getRedirect() : next;
        }
        return out.toString().trim();
    }

    private static void suggest(String input) {
        String[] tok = input.trim().split("\\s+");
        CommandNode<FabricClientCommandSource> node = dispatcher.getRoot();
        StringBuilder prefix = new StringBuilder("/");
        for (String t : tok) {
            CommandNode<FabricClientCommandSource> next = null;
            List<String> names = new ArrayList<>();
            for (CommandNode<FabricClientCommandSource> c : node.getChildren()) {
                if (!(c instanceof LiteralCommandNode)) continue;
                names.add(c.getName());
                if (c.getName().equalsIgnoreCase(FI.getOrDefault(t.toLowerCase(Locale.ROOT), t))) next = c;
            }
            if (next == null) {
                String best = Fuzzy.closest(t, names);
                if (best == null) { Chat.reply("cmd.unknown", t); return; }
                String cmd = prefix + best;
                Chat.send(Chat.prefix().append(Text.literal(Lang.t("cmd.didyoumean", cmd) + " ").formatted(Formatting.YELLOW))
                        .append(Chat.link(Lang.t("btn.use"), cmd, cmd)));
                return;
            }
            prefix.append(next.getName()).append(' ');
            node = next.getRedirect() != null ? next.getRedirect() : next;
        }
        Chat.reply("cmd.unknown", input);
    }

    // ---------------- settings helpers ----------------

    private static void showSetting(String id) {
        Settings.Def d = Settings.def(id);
        if (d == null) {
            Chat.reply("err.unknown_setting", id);
            suggestSetting(id);
            return;
        }
        MutableText t = Text.literal(Lang.t("msg.setting_show", id, Settings.show(id), Settings.showDefault(id)));
        t.setStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Text.literal(SettingText.name(d) + "\n" + SettingText.desc(d)))));
        Chat.reply(t);
    }

    private static void suggestSetting(String id) {
        String best = Fuzzy.closest(id, Settings.ids());
        if (best != null) Chat.reply("cmd.didyoumean", "/pvpcalc config get " + best);
    }

    private static void setSetting(String id, String value) {
        String err = Settings.set(id, value);
        if (err != null) {
            Settings.Def d = Settings.def(id);
            Chat.reply(err, id, d != null && d.options != null ? String.join(", ", d.options.isEmpty() ? List.of("-") : d.options) : "");
            if (err.equals("err.unknown_setting")) suggestSetting(id);
            return;
        }
        if (id.equals("kit") && !Settings.str("kit").isEmpty()) MatchManager.startMatch(Settings.str("kit"));
        Chat.reply("msg.set", id, Settings.show(id));
        ConfigIo.saveIfDirty();
    }

    private static void setOn(boolean on) {
        Settings.set("enabled", on ? "on" : "off");
        ConfigIo.saveIfDirty();
        Chat.reply(on ? "msg.on" : "msg.off");
    }

    private static void resetCsv() {
        if (CsvStore.reset()) Chat.reply("msg.csv_reset"); else Chat.reply("err.write_failed");
    }

    private static void activateKit(String raw) {
        java.util.Optional<String> kit = KitDefs.resolve(raw);
        if (kit.isEmpty()) {
            Chat.reply("err.unknown_kit", raw);
            String best = Fuzzy.closest(raw, KitDefs.all());
            if (best != null) Chat.reply("cmd.didyoumean", "/pvpcalc active_kit " + best);
            return;
        }
        Settings.set("kit", kit.get());
        ConfigIo.saveIfDirty();
        Chat.reply("msg.kit", kit.get(), Settings.bool("enabled") ? "" : Lang.t("msg.kit_off"));
        MatchManager.startMatch(kit.get());
    }

    private static void export(String name) {
        Path p = ConfigIo.export(name);
        if (p == null) Chat.reply("err.write_failed"); else Chat.reply("msg.exported", p.toString());
    }

    private static void importCfg(String name) {
        String err = ConfigIo.importFile(name);
        if (err != null) Chat.reply(err, name);
        else { ConfigIo.saveIfDirty(); Chat.reply("msg.imported"); }
    }

    private static void askConfigReset() {
        resetRequest = System.currentTimeMillis();
        Chat.send(Chat.prefix().append(Text.literal(Lang.t("msg.config_reset_ask") + " ").formatted(Formatting.YELLOW))
                .append(Chat.link(Lang.t("btn.confirm"), "/pvpcalc config reset confirm", Lang.t("tip.confirm_reset"))));
    }

    private static void doConfigReset() {
        if (resetRequest < 0 || System.currentTimeMillis() - resetRequest > 30000) { Chat.reply("err.confirm_expired"); return; }
        resetRequest = -1;
        Settings.resetAll();
        ConfigIo.saveIfDirty();
        Chat.reply("msg.config_reset");
    }

    // ---------------- info commands ----------------

    private static void help() {
        for (String k : new String[]{"help.title", "help.1", "help.2", "help.3", "help.4", "help.5", "help.6", "help.7", "help.8"}) Chat.send(Text.literal(Lang.t(k)));
    }

    private static void status(boolean all) {
        Chat.reply("msg.status", Dirs.version(), Settings.bool("enabled") ? "ON" : "OFF", Settings.str("kit").isEmpty() ? "NONE" : Settings.str("kit"),
                Settings.str("language"), Settings.str("combatSystem"), Settings.str("target"));
        int shown = 0;
        for (Settings.Def d : Settings.ALL) {
            if (d.hidden) continue;
            if (!all && Settings.isDefault(d.id)) continue;
            shown++;
            String col = Settings.isDefault(d.id) ? "\u00a77" : "\u00a7e";
            Chat.send(Chat.hover(col + " " + d.id + " = " + Settings.show(d.id) + " \u00a78(" + Lang.t("gui.default") + ": " + Settings.showDefault(d.id) + ")",
                    SettingText.name(d) + "\n" + SettingText.desc(d), Formatting.WHITE));
        }
        if (!all) Chat.reply("msg.status_hint", shown);
    }

    private static MatchData matchForView() {
        return MatchManager.current() != null ? MatchManager.current() : MatchManager.last();
    }

    private static void stats() {
        MatchData m = matchForView();
        if (m == null) { Chat.reply("err.no_match"); return; }
        Chat.send(Chat.prefix().append(Text.literal(Report.chatShort(m) + " ")).append(Chat.link(Lang.t("btn.open"), "/pvpcalc matchover", Lang.t("tip.open_summary"))));
        Persist.KitRec k = StatsStore.p.kit(m.kit);
        Chat.reply("msg.kit_record", m.kit, k.wins, k.losses, k.streak, k.best);
    }

    private static void matchOver() {
        MatchData m = matchForView();
        if (m == null) { Chat.reply("err.no_match"); return; }
        PvPCalcClient.pendingScreen = new MatchSummaryScreen(m);
    }

    private static void report(boolean copy) {
        MatchData m = matchForView();
        if (m == null) { Chat.reply("err.no_match"); return; }
        String tpl = Settings.str("reportTemplate");
        String text = tpl.equals("discord") ? Report.discord(m, Settings.bool("anonymousCopy"))
                : tpl.equals("full") ? Report.plain(Report.full(m, null)) : Report.strip(Report.chatShort(m)) + "\n" + Settings.str("disclaimer");
        if (copy) {
            MinecraftClient.getInstance().keyboard.setClipboard(text);
            Chat.reply("msg.copied");
        } else {
            for (String l : text.split("\n")) Chat.send(Text.literal("\u00a77" + l));
        }
    }

    private static void recommend(String name) {
        String uuid = null;
        for (Map.Entry<String, String> e : StatsStore.p.names.entrySet()) if (e.getValue().equalsIgnoreCase(name)) uuid = e.getKey();
        if (uuid == null) { Chat.reply("rec.unknown", name); return; }
        String kit = StatsStore.p.recommend(uuid);
        if (kit == null) { Chat.reply("rec.none", name); return; }
        Persist.VsRec v = StatsStore.p.vs.get(uuid).get(kit);
        Chat.reply("rec.result", name, kit, v.wins, v.losses);
    }

    private static void records() {
        if (StatsStore.p.records.isEmpty()) { Chat.reply("err.no_records"); return; }
        Chat.reply("msg.records_head");
        for (Map.Entry<String, Persist.Rec> e : StatsStore.p.records.entrySet()) {
            String key = e.getKey();
            int bar = key.lastIndexOf('|');
            String kit = key.substring(0, Math.max(0, bar)), uuid = bar < 0 ? "" : key.substring(bar + 1);
            String who = uuid.equals("*") ? Lang.t("msg.all_players") : StatsStore.p.names.getOrDefault(uuid, uuid);
            Chat.send(Text.literal("\u00a77 " + kit + " / " + who + ": \u00a7f" + Report.hp(e.getValue().maxHit) + " \u00a77| combo \u00a7f" + e.getValue().maxCombo));
        }
    }

    private static void streaks() {
        if (StatsStore.p.kits.isEmpty()) { Chat.reply("err.no_records"); return; }
        for (Map.Entry<String, Persist.KitRec> e : StatsStore.p.kits.entrySet()) {
            Persist.KitRec r = e.getValue();
            Chat.send(Text.literal("\u00a77 " + e.getKey() + ": \u00a7a" + r.wins + "W \u00a7c" + r.losses + "L \u00a77| " + Lang.t("msg.streak") + " \u00a7f" + r.streak + " \u00a77(" + Lang.t("msg.best") + " " + r.best + ")"));
        }
    }

    private static void goal() {
        MatchData m = matchForView();
        Chat.reply("msg.goal", Settings.i("goalAccuracy"), Settings.i("goalCrit"));
        if (m != null) Chat.reply("msg.goal_progress", Report.pct(m.accuracy()), Report.pct(m.critPct()));
    }

    private static void trend() {
        String kit = Settings.str("kit");
        double[] t = StatsStore.p.trend(kit, Settings.i("trendWindow"));
        if (t[4] == 0) { Chat.reply("err.no_records"); return; }
        if (Double.isNaN(t[1])) { Chat.reply("msg.trend_few", (int) t[4], Settings.i("trendWindow")); return; }
        Chat.reply("msg.trend", kit.isEmpty() ? "ALL" : kit, Settings.i("trendWindow"), Num.fmt1(t[0]), Num.fmt1(t[1]), Num.fmt1(t[2]), Num.fmt1(t[3]),
                t[0] >= t[1] && t[2] >= t[3] ? "\u00a7a\u2191" : "\u00a7c\u2193");
    }

    // ---------------- gear / calc ----------------

    private static String stackLine(ItemStack s) {
        if (s.isEmpty()) return "-";
        String e = DamageCalculator.enchantList(s);
        String dur = s.isDamageable() && s.getMaxDamage() > 0 ? " " + Num.fmt(100.0 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage(), 0) + "%" : "";
        return s.getName().getString() + dur + (e.isEmpty() ? "" : " [" + e + "]");
    }

    private static void gear(boolean target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LivingEntity e = target ? Opp.opponent(mc) : mc.player;
        if (e == null) { Chat.reply("err.no_player_looked"); return; }
        Gear g = Gear.of(e);
        Chat.reply("gear.head", g.name);
        Chat.send(Text.literal("\u00a77 " + Lang.t("gear.weapon") + ": \u00a7f" + stackLine(e.getMainHandStack())));
        Chat.send(Text.literal("\u00a77 " + Lang.t("gear.offhand") + ": \u00a7f" + stackLine(e.getOffHandStack())));
        for (int i = 0; i < Gear.ARMOR.length; i++) Chat.send(Text.literal("\u00a77 " + Lang.t("piece." + Gear.PIECE_KEYS[i]) + ": \u00a7f" + stackLine(e.getEquippedStack(Gear.ARMOR[i]))));
        Chat.send(Text.literal("\u00a77 " + Lang.t("gear.numbers", Num.fmt1(g.armor), Num.fmt1(g.toughness), Num.fmt(g.epf, 0), g.resist,
                DamageCalculator.strengthLevel(e), DamageCalculator.weaknessLevel(e), Report.hp(g.health), Report.num(g.absorption), g.totemsTotal)));
        Chat.reply("gear.used");
    }

    private static void calc() {
        MinecraftClient mc = MinecraftClient.getInstance();
        LivingEntity t = Opp.target(mc);
        if (mc.player == null || t == null) { Chat.reply("err.no_player_looked"); return; }
        DamageCalculator.Result r = DamageCalculator.melee(mc.player, t);
        Gear g = Gear.of(t);
        Chat.reply(Text.literal(r.details()).formatted(Formatting.GREEN));
        int htk = DamageMath.hitsToKill(g.effectiveHp(false), r.dmg());
        double ttk = Predict.ttk(g.effectiveHp(false), r.dmg(), DamageCalculator.intervalSec(mc.player, DamageCalculator.kindOf(mc.player.getMainHandStack())));
        Chat.reply("calc.result", g.name, Report.hp(g.health + g.absorption), htk == Integer.MAX_VALUE ? "-" : htk, ttk >= 1e9 ? "-" : Num.fmt(ttk, 1),
                Settings.str("combatSystem"), Settings.str("difficulty"), Settings.bool("assumeShield") ? "ON" : "OFF");
        double fall = MaceCalculator.fallNeeded(mc.player, t);
        if (DamageCalculator.kindOf(mc.player.getMainHandStack()) == DamageCalculator.Kind.MACE)
            Chat.reply(Double.isNaN(fall) ? "hud.mace_none" : "calc.mace", Double.isNaN(fall) ? "" : Num.fmt(fall, 1));
    }

    // ---------------- hack commands ----------------

    private static void hackStatus() {
        for (String l : HackDetector.statusLines()) Chat.send(Text.literal(l));
    }

    private static void trackLooked() {
        PlayerEntity p = Opp.looked(MinecraftClient.getInstance(), 40.0);
        if (p == null) { Chat.reply("err.no_player_looked"); return; }
        track(p.getName().getString());
    }

    private static void track(String name) {
        if (HackDetector.track(name)) Chat.reply("hack.tracking", name); else Chat.reply("err.not_found_near", name);
    }

    private static void whitelist(boolean add, String name) {
        List<String> l = new ArrayList<>();
        for (String s : Settings.str("hack.whitelist").split(",")) if (!s.trim().isEmpty()) l.add(s.trim());
        boolean has = l.removeIf(s -> s.equalsIgnoreCase(name));
        if (add) { l.add(name); } else if (!has) { Chat.reply("err.not_found", name); return; }
        Settings.set("hack.whitelist", String.join(",", l));
        ConfigIo.saveIfDirty();
        Chat.reply(add ? "hack.whitelist_added" : "hack.whitelist_removed", name);
    }

    private static void category(String check, String field, String value) {
        String c = check.toLowerCase(Locale.ROOT);
        if (!checkNames().contains(c)) {
            Chat.reply("err.bad_scenario", check, String.join(", ", checkNames()));
            return;
        }
        if (field == null) {
            for (String f : new String[]{"on", "weight", "limit", "alert"}) {
                String id = "chk." + c + "." + f;
                Chat.send(Text.literal("\u00a77 " + id + " = \u00a7f" + Settings.show(id) + " \u00a78(" + Lang.t("gui.default") + ": " + Settings.showDefault(id) + ")"));
            }
            return;
        }
        String id = "chk." + c + "." + field.toLowerCase(Locale.ROOT);
        if (!Settings.exists(id)) { Chat.reply("err.unknown_setting", id); return; }
        setSetting(id, value);
    }

    public static String csvMarker() {
        return CsvUtil.MARKER;
    }
}
