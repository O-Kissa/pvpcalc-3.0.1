package com.pvpcalc.input;

import com.pvpcalc.core.Lang;
import com.pvpcalc.core.Settings;
import com.pvpcalc.gui.MatchSummaryScreen;
import com.pvpcalc.gui.SettingsScreen;
import com.pvpcalc.hack.HackDetector;
import com.pvpcalc.tracking.MatchManager;
import com.pvpcalc.tracking.Opp;
import com.pvpcalc.util.Chat;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** All key bindings live in Minecraft's own Controls screen (category "PvPCalc"), so they are rebindable there. */
public final class KeyBindings {
    private static KeyBinding settings, master, hud, detector, newMatch, trackLooked, summary, resetHud;
    private static final List<KeyBinding> ALL = new ArrayList<>();

    private KeyBindings() {}

    private static KeyBinding reg(String id, int key, KeyBinding.Category cat) {
        KeyBinding k = KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, key, cat));
        ALL.add(k);
        return k;
    }

    public static void register() {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("pvpcalc", "main"));
        settings = reg("key.pvpcalc.settings", GLFW.GLFW_KEY_O, cat);
        master = reg("key.pvpcalc.master", GLFW.GLFW_KEY_U, cat);
        hud = reg("key.pvpcalc.hud", GLFW.GLFW_KEY_I, cat);
        detector = reg("key.pvpcalc.detector", GLFW.GLFW_KEY_Y, cat);
        newMatch = reg("key.pvpcalc.newmatch", GLFW.GLFW_KEY_B, cat);
        trackLooked = reg("key.pvpcalc.track", GLFW.GLFW_KEY_G, cat);
        summary = reg("key.pvpcalc.summary", GLFW.GLFW_KEY_UNKNOWN, cat);
        resetHud = reg("key.pvpcalc.resethud", GLFW.GLFW_KEY_UNKNOWN, cat);
    }

    public static List<KeyBinding> all() {
        return ALL;
    }

    public static List<String> lines() {
        List<String> l = new ArrayList<>();
        for (KeyBinding k : ALL) l.add(Lang.t(k.getTranslationKey()) + ": " + k.getBoundKeyLocalizedText().getString());
        return l;
    }

    private static void toggle(String id) {
        Settings.cycle(id, 1);
        Chat.reply("msg.set", id, Settings.show(id));
    }

    public static void tick(MinecraftClient mc) {
        if (settings == null) return;
        while (settings.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new SettingsScreen(null));
        }
        while (master.wasPressed()) toggle("enabled");
        while (hud.wasPressed()) toggle("hud.visible");
        while (detector.wasPressed()) toggle("hack.enabled");
        while (newMatch.wasPressed()) {
            MatchManager.startMatch(Settings.str("kit"));
            Chat.reply("msg.newmatch");
        }
        while (trackLooked.wasPressed()) {
            PlayerEntity p = Opp.looked(mc, 40.0);
            if (p == null) Chat.reply("err.no_player_looked");
            else if (HackDetector.track(p.getName().getString())) Chat.reply("hack.tracking", p.getName().getString());
            else Chat.reply("err.not_found", p.getName().getString());
        }
        while (summary.wasPressed()) {
            if (MatchManager.last() != null || MatchManager.current() != null)
                mc.setScreen(new MatchSummaryScreen(MatchManager.current() != null ? MatchManager.current() : MatchManager.last()));
            else Chat.reply("err.no_match");
        }
        while (resetHud.wasPressed()) {
            Settings.reset("hud.x");
            Settings.reset("hud.y");
            Settings.reset("hud.corner");
            Chat.reply("msg.hud_reset");
        }
    }
}
