package com.pvpcalc.core;

/** Check limits for one analysis, already adjusted for sensitivity, kit, version, ping and server TPS. */
public final class Limits {
    public double speed, fly, aim, cps, vel, nofall, totem, mace, reach;
    public double tol = 1.0;          // lag tolerance (>= 1)
    public double kitSpeedMul = 1.0;
    public boolean flyAllowed;
    public boolean v18;
    private final boolean[] on = new boolean[Settings.CHECKS.length];

    public boolean on(String check) {
        for (int i = 0; i < Settings.CHECKS.length; i++) if (Settings.CHECKS[i].equals(check)) return on[i];
        return false;
    }

    public static double sensMul() {
        String s = Settings.str("hack.sensitivity");
        return s.equals("low") ? 1.25 : (s.equals("high") ? 0.8 : 1.0);
    }

    public static Limits from(String kit, boolean v18, double tps, double pingMs) {
        Limits l = new Limits();
        double sm = sensMul();
        l.v18 = v18;
        l.speed = Settings.dbl("chk.speed.limit") * sm;
        l.fly = Math.max(4, Settings.dbl("chk.fly.limit") * sm);
        l.aim = Settings.dbl("chk.aimbot.limit") * sm;
        double cps = Settings.dbl("chk.autoclicker.limit") * sm;
        l.cps = v18 ? cps : Math.min(cps, 12.0 * sm);   // 1.9+ cooldown makes high CPS pointless
        l.vel = Settings.dbl("chk.velocity.limit") / sm;
        l.nofall = Settings.dbl("chk.nofall.limit") * sm;
        l.totem = Settings.dbl("chk.autototem.limit") / sm;
        l.mace = Settings.dbl("chk.mace.limit") * sm;
        l.reach = Settings.dbl("chk.reach.limit") * (1.0 + (sm - 1.0) * 0.15);
        double tol = 1.0;
        if (Settings.bool("hack.tpsComp") && tps > 0) tol *= Num.clamp(20.0 / Math.max(tps, 5.0), 1.0, 3.0);
        if (Settings.bool("hack.pingComp")) tol *= Num.clamp(1.0 + Math.max(0.0, pingMs - 100.0) / 500.0, 1.0, 2.0);
        l.tol = tol;
        l.kitSpeedMul = 1.0;
        if (KitDefs.isMaceKit(kit)) { l.kitSpeedMul = 1.6; l.flyAllowed = true; }
        if (KitDefs.isSpearKit(kit)) l.kitSpeedMul = Math.max(l.kitSpeedMul, 1.5);
        if (KitDefs.isElytraKit(kit)) { l.kitSpeedMul = 2.0; l.flyAllowed = true; }
        for (int i = 0; i < Settings.CHECKS.length; i++) l.on[i] = Settings.bool("chk." + Settings.CHECKS[i] + ".on");
        return l;
    }

    /** For unit tests: all checks on with default limits. */
    public static Limits defaults() {
        return from("", false, 20.0, 50.0);
    }

    public void disable(String check) {
        for (int i = 0; i < Settings.CHECKS.length; i++) if (Settings.CHECKS[i].equals(check)) on[i] = false;
    }

    public void enableAll() {
        for (int i = 0; i < on.length; i++) on[i] = true;
    }
}
