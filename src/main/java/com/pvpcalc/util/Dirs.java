package com.pvpcalc.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class Dirs {
    private Dirs() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpcalc");
    }

    public static Path file(String name) {
        return dir().resolve(name);
    }

    public static String version() {
        return FabricLoader.getInstance().getModContainer("pvpcalc")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
    }
}
