package com.pvpcalc;

import com.pvpcalc.combat.ExplosionCalculator;
import com.pvpcalc.combat.SpearCalculator;
import com.pvpcalc.combat.WeaponHandler;
import com.pvpcalc.commands.PvPCalcCommands;
import com.pvpcalc.config.ConfigIo;
import com.pvpcalc.core.Lang;
import com.pvpcalc.core.ServerPresets;
import com.pvpcalc.core.Settings;
import com.pvpcalc.hack.HackDetector;
import com.pvpcalc.hud.HudRenderer;
import com.pvpcalc.input.KeyBindings;
import com.pvpcalc.logging.CsvStore;
import com.pvpcalc.tracking.ConsumableTracker;
import com.pvpcalc.tracking.DeathAnalyzer;
import com.pvpcalc.tracking.MatchDedupe;
import com.pvpcalc.tracking.MatchManager;
import com.pvpcalc.tracking.StatsStore;
import com.pvpcalc.util.Chat;
import com.pvpcalc.util.Clock;
import com.pvpcalc.util.Dirs;
import com.pvpcalc.util.Guard;
import com.pvpcalc.util.Hooks;
import com.pvpcalc.util.Music;
import com.pvpcalc.util.Update;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;

/** PvPCalc 3.0.0 - client-side PvP calculator, tracker and cheat-estimate tool. Made by O_Kissa. */
public class PvPCalcClient implements ClientModInitializer {
    public static final String MOD_ID = "pvpcalc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Screens are opened on the next tick (the chat screen closes itself after a command). */
    public static volatile Screen pendingScreen;
    private static boolean joinPending;
    private static long joinTick;
    private static ClientPlayerEntity lastPlayer;

    @Override
    public void onInitializeClient() {
        try {
            Files.createDirectories(Dirs.dir());               // first run: folder + files are created automatically
            Settings.load(ConfigIo.configFile());               // a missing / broken config is replaced by defaults
            Lang.load();
            if (Settings.dirty) Settings.save(ConfigIo.configFile());
            CsvStore.init();
            StatsStore.load();
        } catch (Throwable t) {
            LOGGER.error("PvPCalc start-up problem (continuing with defaults)", t);
        }
        PvPCalcCommands.register();
        KeyBindings.register();
        HudRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(PvPCalcClient::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { joinPending = true; joinTick = -1; });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Guard.run("disconnect", PvPCalcClient::onDisconnect));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Guard.run("shutdown", PvPCalcClient::shutdown));
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> Guard.run("world", () -> {
            HackDetector.clearHistoryOnly();   // dimension change: history cleared + grace period
            ConsumableTracker.reset();
        }));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> Guard.run("death", () -> DeathAnalyzer.onMessage(message)));
        LOGGER.info("PvPCalc {} initialized (tracking {})", Dirs.version(), Settings.bool("enabled") ? "ON" : "OFF");
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        Clock.advance();
        long now = Clock.tick();
        final boolean perf = Settings.bool("performanceMode");

        Guard.run("core", () -> {
            if (mc.player != lastPlayer) {            // respawn / new world: short grace period for the movement checks
                lastPlayer = mc.player;
                HackDetector.grace();
            }
            if (pendingScreen != null && mc.currentScreen == null) {
                mc.setScreen(pendingScreen);
                pendingScreen = null;
            }
            if (joinPending) {
                if (joinTick < 0) joinTick = now;
                if (now - joinTick > 40) { joinPending = false; onJoinReady(mc); }
            }
        });
        Guard.run("keys", () -> KeyBindings.tick(mc));
        Guard.run("spear", () -> SpearCalculator.trackTick(mc));
        Guard.run("explosion", () -> ExplosionCalculator.snapshot(mc));
        Guard.run("weapon", () -> WeaponHandler.tick(mc));
        Guard.run("consumables", () -> { if (!perf || now % 2 == 0) ConsumableTracker.tick(mc); });
        Guard.run("match", () -> MatchManager.tick(mc));
        Guard.run("hack", () -> HackDetector.tick(mc));
        Guard.run("hud-data", () -> { if (now % 5 == 0) HudRenderer.update(mc); });
        Guard.run("csv", () -> { if (now % 20 == 0) CsvStore.flush(); });
        Guard.run("config", () -> {
            if (now % 40 == 0) {
                ConfigIo.saveIfDirty();
                StatsStore.save();
            }
        });
    }

    /** Runs once, shortly after joining a world or server. */
    private static void onJoinReady(MinecraftClient mc) {
        Guard.run("join", () -> {
            HackDetector.resetState();
            if (!Settings.bool("firstRunDone")) {
                Settings.set("firstRunDone", "on");
                ConfigIo.saveIfDirty();
                for (String k : new String[]{"help.title", "help.1", "help.2", "help.3", "help.4", "help.5", "help.6", "help.7", "help.8"})
                    Chat.send(net.minecraft.text.Text.literal(Lang.t(k)));
            }
            if (Settings.bool("serverPresets")) {
                ServerInfo si = mc.getCurrentServerEntry();
                ServerPresets.Preset p = si == null ? null : ServerPresets.match(si.address);
                if (p != null) {
                    Settings.set("combatSystem", p.combat);
                    Settings.set("chk.reach.limit", String.valueOf(p.reach));
                    Settings.set("smpMode", p.smp ? "on" : "off");
                    Chat.reply("msg.preset", p.host, p.combat, p.reach, p.smp ? "ON" : "OFF");
                }
            }
            if (Settings.bool("updateNotice")) Update.check(false);
        });
    }

    private static void onDisconnect() {
        MatchManager.onDisconnect();        // writes the match rows + HACK SUMMARY rows before state is cleared
        HackDetector.onDisconnect();
        ConsumableTracker.reset();
        WeaponHandler.reset();
        SpearCalculator.reset();
        ExplosionCalculator.reset();
        MatchDedupe.clear();
        Chat.reset();
        Hooks.reset();
        lastPlayer = null;
        joinPending = false;
        CsvStore.flush();
        ConfigIo.saveIfDirty();
        StatsStore.save();
    }

    private static void shutdown() {
        MatchManager.onDisconnect();
        CsvStore.flush();                   // buffered rows are written before the game closes
        ConfigIo.saveIfDirty();
        StatsStore.save();
        Music.stop();
    }
}
