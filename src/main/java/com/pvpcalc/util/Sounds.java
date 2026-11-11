package com.pvpcalc.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class Sounds {
    private Sounds() {}

    public static boolean play(String id, float pitch, float volume) {
        try {
            Identifier ident = Identifier.tryParse(id);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (ident == null || mc.getSoundManager() == null) return false;
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(ident), pitch, volume));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
