// Ported from ADM's p3sim/clear/blood/camp/CampLines.kt
package com.teslamaps.adm.sim.clear.blood.camp;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/** The Watcher's own camp lines by class. */
public final class CampLines {
    private CampLines() {}

    public static final String INTRO = "Ah, we meet again. As I foresaw...";

    public static final String INTRO_OTHER = "Things feel a little more roomy now, eh?";

    public static final List<String> INTRO_OTHER_MORE = List.of("I've knocked down those pillars to go for a more...open concept.", "Plus I needed to give my new friends some space to roam...");
    public static final int INTRO_OTHER_GAP = 82;

    public static final double INTRO_SHARE = 471.0 / 501.0;

    /** A replay's recorded lines per class (null: drawn). */
    public static volatile Map<CampMech.Line, ArrayDeque<String>> script = null;

    public static CampMech.Line classOf(String text) {
        if (SPAWN.contains(text)) return CampMech.Line.SPAWN;
        if (REACT.contains(text)) return CampMech.Line.REACT;
        if (HANDLE.contains(text)) return CampMech.Line.HANDLE;
        if (ENOUGH.contains(text)) return CampMech.Line.ENOUGH;
        if (ATTACK.contains(text)) return CampMech.Line.ATTACK;
        if (LEDGE.contains(text)) return CampMech.Line.LEDGE;
        return null;
    }

    /** A replay's forced opening (null: drawn). */
    public static volatile String forcedOpening = null;

    public static final List<String> SPAWN = List.of("This guy looks like a fighter.", "You'll do.", "Go and live again!", "Hmmm... this one!", "Go, fight!");
    public static final List<String> REACT = List.of("Very nice.", "I'm impressed.", "Aw, I liked that one.", "That one was weak anyway.", "Not bad.");
    public static final List<String> HANDLE = List.of("Let's see how you can handle this.");
    public static final List<String> ENOUGH = List.of("That will be enough for now.");
    public static final List<String> ATTACK = List.of("Stop attacking me.");
    public static final List<String> LEDGE = List.of(
            "My Watchful Eyes see you up there! Come down and fight!", "Don't try to sneak anything past my Watchful Eyes. They see you up there!",
            "We are always watching. Come down from there!", "My Watchful Eyes see all!"
    );

    public static String opening(KRandom rng) {
        String f = forcedOpening;
        if (f != null) return f;
        return rng.nextDouble() < INTRO_SHARE ? INTRO : INTRO_OTHER;
    }

    public static String pick(CampMech.Line line, KRandom rng) {
        List<String> pool = switch (line) {
            case SPAWN -> SPAWN;
            case REACT -> REACT;
            case HANDLE -> HANDLE;
            case ENOUGH -> ENOUGH;
            case ATTACK -> ATTACK;
            case LEDGE -> LEDGE;
            case INTRO -> null;
        };
        if (pool == null) return null;
        Map<CampMech.Line, ArrayDeque<String>> s = script;
        if (s != null) {
            ArrayDeque<String> q = s.get(line);
            if (q != null) {
                String t = q.pollFirst();
                if (t != null) return t;
            }
        }
        return pool.get(rng.nextInt(pool.size()));
    }
}
