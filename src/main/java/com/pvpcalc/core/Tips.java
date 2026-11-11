package com.pvpcalc.core;

import java.util.ArrayList;
import java.util.List;

/** Three concrete things to practise after a match (rule based). Each tip = {langKey, arg0, arg1}. */
public final class Tips {
    private Tips() {}

    public static List<String[]> compute(MatchData m, int goalAcc, int goalCrit) {
        List<String[]> t = new ArrayList<>();
        if (m.swings >= 8 && m.accuracy() < goalAcc)
            t.add(new String[]{"tip.accuracy", Num.fmt1(m.accuracy()), String.valueOf(goalAcc)});
        if (m.hits >= 6 && m.critPct() < goalCrit)
            t.add(new String[]{"tip.crit", Num.fmt1(m.critPct()), String.valueOf(goalCrit)});
        if (m.taken > m.dealt && m.taken > 0)
            t.add(new String[]{"tip.defense", Num.fmt1(m.taken), Num.fmt1(m.dealt)});
        if (m.hits >= 5 && m.maxCombo <= 2)
            t.add(new String[]{"tip.combo", String.valueOf(m.maxCombo), ""});
        if (m.avgReaction() > 1.5)
            t.add(new String[]{"tip.reaction", Num.fmt1(m.avgReaction()), ""});
        if (m.spacingPct() > 0 && m.spacingPct() < 25)
            t.add(new String[]{"tip.spacing", Num.fmt1(m.spacingPct()), ""});
        if (m.myTotems >= 2)
            t.add(new String[]{"tip.totem", String.valueOf(m.myTotems), ""});
        String[] fallback = {"tip.keep", "", ""};
        String[] fallback2 = {"tip.review", "", ""};
        String[] fallback3 = {"tip.warmup", "", ""};
        if (t.size() < 3) t.add(fallback);
        if (t.size() < 3) t.add(fallback2);
        if (t.size() < 3) t.add(fallback3);
        return new ArrayList<>(t.subList(0, 3));
    }
}
