package com.pvpcalc.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-player movement / click history and the individual checks. Pure Java: the Minecraft side only fills
 * {@link Sample}s, so the same code is used for real players, training mode and simulation mode.
 */
public final class Track {
    public static final class Sample {
        public long tick;
        public double x, y, z, yaw, pitch;
        public boolean onGround, swing, offTotem;
        public int exempt;
        public int speedAmp = -1, jumpAmp = -1;
        public int hurt;
    }

    public static final class Viol {
        public final String check;
        public final double points;
        public final String info;
        public Viol(String check, double points, String info) {
            this.check = check; this.points = points; this.info = info;
        }
    }

    private Sample prev;
    private int grace, speedStreak, airTicks, hoverTicks, cpsOver;
    private double maxY, noFallFall;
    private boolean wasAir;
    private long noFallDeadline = -1, velStart = -1, popTick = -1, lastConsistent = -1000;
    private double velX, velZ;
    private final ArrayList<Long> swings = new ArrayList<>();
    private final ArrayDeque<double[]> aim = new ArrayDeque<>(); // {tick, |dyaw|}
    private final ArrayList<String> errors = new ArrayList<>();
    public double lastSpeed;   // blocks per tick (for baselines / display)

    /** Names of checks that threw since the last call (the caller counts them and can switch a check off). */
    public List<String> consumeErrors() {
        List<String> l = new ArrayList<>(errors);
        errors.clear();
        return l;
    }

    private void guarded(String check, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            errors.add(check);
        }
    }
    public int lastCps;

    private void resetStreaks() {
        speedStreak = 0; airTicks = 0; hoverTicks = 0; wasAir = false; noFallDeadline = -1;
    }

    public int cpsAt(long tick) {
        int c = 0;
        for (int i = swings.size() - 1; i >= 0; i--) {
            if (tick - swings.get(i) <= 20) c++; else break;
        }
        return c;
    }

    public List<Viol> push(Sample s, Limits L) {
        List<Viol> out = new ArrayList<>();
        Sample p = prev;
        prev = s;
        if (p == null) return out;
        long dt = Math.max(1, s.tick - p.tick);
        double dx = s.x - p.x, dz = s.z - p.z, dy = s.y - p.y;
        double hdist = Math.hypot(dx, dz);
        if (hdist > 8.0 || Math.abs(dy) > 8.0) { // teleport / respawn: ignore for a while
            grace = 40;
            resetStreaks();
            return out;
        }
        if (grace > 0) grace--;
        final int ex = s.exempt | (s.hurt > 0 ? Exempt.KNOCKBACK : 0);
        final boolean clean = ex == 0 && grace == 0;
        final double hd = hdist / dt;
        lastSpeed = hd;

        // ---- speed ----
        guarded("speed", () -> {
        if (L.on("speed")) {
            if (clean) {
                double allowed = 0.2806 * (1.0 + 0.2 * (s.speedAmp + 1)) * L.kitSpeedMul * (s.onGround ? 1.0 : 1.3);
                double ratio = hd / allowed;
                if (ratio > L.speed * L.tol) {
                    speedStreak++;
                    if (speedStreak >= 3) out.add(new Viol("speed", Math.min(3.0, 1.0 + (ratio - L.speed) * 3.0), "x" + Num.fmt(ratio, 2)));
                } else {
                    speedStreak = 0;
                }
            } else {
                speedStreak = 0;
            }
        }
        });

        // ---- fly ----
        guarded("fly", () -> {
        if (L.on("fly")) {
            int skip = Exempt.WATER | Exempt.LAVA | Exempt.CLIMB | Exempt.VEHICLE | Exempt.ELYTRA | Exempt.LEVITATION
                    | Exempt.SLOWFALL | Exempt.WIND | Exempt.EXPLOSION | Exempt.GAMEMODE | Exempt.RIPTIDE
                    | Exempt.FIREWORK | Exempt.PEARL | Exempt.RESPAWN | Exempt.TELEPORT;
            boolean air = !s.onGround && (ex & skip) == 0 && grace == 0 && !L.flyAllowed;
            if (air) {
                airTicks++;
                if (Math.abs(dy) < 0.03) hoverTicks++; else hoverTicks = Math.max(0, hoverTicks - 2);
                if (hoverTicks >= L.fly * L.tol) {
                    out.add(new Viol("fly", 2.0, "hover " + hoverTicks + "t"));
                    hoverTicks = (int) (L.fly / 2);
                }
                if (dy > 0.1 && airTicks > 22 && s.jumpAmp < 0) {
                    out.add(new Viol("fly", 2.0, "rising " + airTicks + "t"));
                    airTicks = 10;
                }
            } else {
                airTicks = 0;
                hoverTicks = 0;
            }
        }
        });

        // ---- nofall ----
        guarded("nofall", () -> {
        if (L.on("nofall")) {
            if (!s.onGround) {
                if (!wasAir) maxY = s.y;
                maxY = Math.max(maxY, s.y);
                wasAir = true;
            } else {
                if (wasAir) {
                    double fall = maxY - s.y;
                    int skip = Exempt.WATER | Exempt.LAVA | Exempt.CLIMB | Exempt.VEHICLE | Exempt.ELYTRA | Exempt.SLOWFALL
                            | Exempt.SOFT | Exempt.PEARL | Exempt.WIND | Exempt.TELEPORT | Exempt.LEVITATION | Exempt.GAMEMODE | Exempt.RESPAWN;
                    if (fall > L.nofall && (s.exempt & skip) == 0 && grace == 0) {
                        noFallDeadline = s.tick + 4;
                        noFallFall = fall;
                    }
                }
                wasAir = false;
            }
            if (noFallDeadline >= 0) {
                if (s.hurt > 0) noFallDeadline = -1;
                else if (s.tick > noFallDeadline) {
                    out.add(new Viol("nofall", 3.0, "fell " + Num.fmt(noFallFall, 1) + " blocks, no damage"));
                    noFallDeadline = -1;
                }
            }
        }
        });

        // ---- aim snap + autoclicker (both depend on swings) ----
        guarded("aimbot", () -> {
        final double dyaw = Math.abs(Num.wrapDeg(s.yaw - p.yaw));
        aim.addLast(new double[]{s.tick, dyaw});
        while (aim.size() > 4) aim.pollFirst();
        if (s.swing) {
            swings.add(s.tick);
            while (!swings.isEmpty() && s.tick - swings.get(0) > 100) swings.remove(0);
            int cps = cpsAt(s.tick);
            lastCps = cps;
            if (L.on("aimbot")) {
                double mx = 0;
                for (double[] a : aim) if (s.tick - a[0] <= 3) mx = Math.max(mx, a[1]);
                if (mx > L.aim * L.tol && (ex & (Exempt.TELEPORT | Exempt.RESPAWN | Exempt.LAG)) == 0 && grace == 0)
                    out.add(new Viol("aimbot", 2.0, "snap " + Num.fmt(mx, 0) + " deg"));
            }
            if (L.on("autoclicker")) {
                if (cps > L.cps) {
                    cpsOver++;
                    if (cpsOver >= 3) out.add(new Viol("autoclicker", Math.min(3.0, 1.0 + (cps - L.cps) * 0.4), cps + " CPS"));
                } else {
                    cpsOver = 0;
                }
                if (swings.size() >= 20 && s.tick - lastConsistent > 40) {
                    int n = swings.size();
                    double[] iv = new double[19];
                    double mean = 0;
                    for (int k = 0; k < 19; k++) { iv[k] = swings.get(n - 1 - k) - swings.get(n - 2 - k); mean += iv[k]; }
                    mean /= 19.0;
                    double var = 0;
                    for (double v : iv) var += (v - mean) * (v - mean);
                    double sd = Math.sqrt(var / 19.0);
                    if (mean < 4.5 && mean > 0 && sd < 0.7 && cps > 8) {
                        lastConsistent = s.tick;
                        out.add(new Viol("autoclicker", 2.0, "too consistent (sd " + Num.fmt(sd, 2) + ")"));
                    }
                }
            }
        }
        });

        // ---- velocity (anti knockback) ----
        guarded("velocity", () -> {
        if (velStart >= 0 && s.tick - velStart >= 6) {
            double disp = Math.hypot(s.x - velX, s.z - velZ);
            velStart = -1;
            if (L.on("velocity") && grace == 0 && (s.exempt & ~Exempt.KNOCKBACK) == 0 && disp < L.vel)
                out.add(new Viol("velocity", 2.0, "moved " + Num.fmt(disp, 2) + " after hit"));
        }
        });

        // ---- autototem ----
        guarded("autototem", () -> {
        if (popTick >= 0) {
            if (s.offTotem) {
                long ticks = s.tick - popTick;
                popTick = -1;
                if (L.on("autototem") && ticks <= L.totem) out.add(new Viol("autototem", 3.0, "re-equipped in " + ticks + "t"));
            } else if (s.tick - popTick > 200) {
                popTick = -1;
            }
        }
        });

        return out;
    }

    /** I hit this player (position at the moment of the hit). */
    public void noteHit(long tick, double x, double z) {
        velStart = tick;
        velX = x;
        velZ = z;
    }

    public void notePop(long tick) {
        popTick = tick;
    }

    /** This player hit ME from distance dist (eye to my nearest hitbox point). */
    public Viol reach(double dist, double pingMs, Limits L, int exempt) {
        if (!L.on("reach") || exempt != 0) return null;
        double tol = Num.clamp(pingMs / 1000.0 * 1.5, 0.0, 0.6) * (L.tol > 1.0 ? 1.0 : 0.5);
        double limit = L.reach + tol;
        if (dist <= limit) return null;
        return new Viol("reach", Math.min(3.0, 1.0 + (dist - limit) * 4.0), Num.fmt(dist, 2) + " blocks");
    }

    /** This player hit me with a mace for dmg HP while their own fall was fall blocks. */
    public Viol maceHit(double dmg, double fall, int exempt, Limits L) {
        if (!L.on("mace") || exempt != 0) return null;
        if (fall < 1.5 && dmg > L.mace) return new Viol("mace", 3.0, Num.fmt(dmg, 1) + " dmg with " + Num.fmt(fall, 1) + " fall");
        return null;
    }
}
