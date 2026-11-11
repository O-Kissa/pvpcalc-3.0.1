package com.pvpcalc.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

/** Plays vanilla music discs or any sound id from a resource pack (e.g. "mypack:songs.azali"). */
public final class Music {
    public static final List<String> DISCS = List.of("13", "cat", "blocks", "chirp", "far", "mall", "mellohi", "stal",
            "strad", "ward", "11", "wait", "pigstep", "otherside", "5", "relic", "creator", "creator_music_box", "precipice");
    private static SoundInstance current;

    private Music() {}

    public static Identifier resolve(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (DISCS.contains(n)) return Identifier.of("minecraft", "music_disc." + n);
        return Identifier.tryParse(n);
    }

    public static boolean play(String name) {
        try {
            Identifier id = resolve(name);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (id == null) return false;
            stop();
            current = PositionedSoundInstance.master(SoundEvent.of(id), 1.0F, 1.0F);
            mc.getSoundManager().play(current);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void stop() {
        try {
            if (current != null) MinecraftClient.getInstance().getSoundManager().stop(current);
        } catch (Throwable ignored) {
            // nothing to stop
        }
        current = null;
    }
}
