// Ported from ADM's p3sim/clear/state/RoomChecks.kt
package com.teslamaps.adm.sim.clear.state;

import com.teslamaps.adm.sim.run.CheckMark;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;

public final class RoomChecks {
    private RoomChecks() {}

    public static CheckMark onDiscovered(RoomType type) { return type == RoomType.FAIRY ? CheckMark.GREEN : CheckMark.NONE; }

    public static CheckMark onObjectiveDone(int secretsMax, int secretsFound) {
        return secretsMax <= 0 || secretsFound >= secretsMax ? CheckMark.GREEN : CheckMark.WHITE;
    }

    public static boolean secretsComplete(int secretsMax, int secretsFound) { return secretsMax > 0 && secretsFound >= secretsMax; }

    public static boolean counts(RoomType type) { return type != RoomType.ENTRANCE; }

    public static Pair<Integer, Integer> counters(List<RoomRecord> rooms) {
        int opened = 0;
        int completed = 0;
        for (RoomRecord r : rooms) {
            if (!counts(r.plan.type)) continue;
            if (r.check != CheckMark.UNDISCOVERED) opened += r.plan.tileCount();
            if (r.check == CheckMark.WHITE || r.check == CheckMark.GREEN) completed += r.plan.tileCount();
        }
        return Pair.of(opened, completed);
    }
}
