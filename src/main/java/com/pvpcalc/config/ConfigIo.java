package com.pvpcalc.config;

import com.pvpcalc.core.Settings;
import com.pvpcalc.util.Dirs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Export / import of settings as JSON (for another computer or a friend) and saving the live config. */
public final class ConfigIo {
    private ConfigIo() {}

    public static Path configFile() {
        return Dirs.file("config.json");
    }

    public static String safeName(String name) {
        String n = (name == null || name.isBlank() ? "export" : name.trim()).replaceAll("[^A-Za-z0-9_\\-]", "_");
        return n + ".json";
    }

    public static void saveIfDirty() {
        if (Settings.dirty) Settings.save(configFile());
    }

    /** Returns the written path, or null on failure. */
    public static Path export(String name) {
        try {
            Path f = Dirs.file("exports").resolve(safeName(name));
            Files.createDirectories(f.getParent());
            Files.writeString(f, Settings.toJson(), StandardCharsets.UTF_8);
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    /** Returns null on success or an error key (err.*). Invalid files are rejected without changing anything. */
    public static String importFile(String name) {
        try {
            Path f = Dirs.file("exports").resolve(safeName(name));
            if (!Files.exists(f)) return "err.file_missing";
            String json = Files.readString(f, StandardCharsets.UTF_8);
            // validate first, so a broken file cannot half-apply
            if (!(com.pvpcalc.core.MiniJson.parse(json) instanceof java.util.Map)) return "err.bad_file";
            return Settings.fromJson(json);
        } catch (IOException | RuntimeException e) {
            return "err.bad_file";
        }
    }
}
