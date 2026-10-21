// Ported from ADM's p3sim/run/LayoutPick.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.layouts.Layout;
import com.teslamaps.adm.sim.pack.layouts.LayoutPack;
import java.util.Objects;
import java.util.function.Predicate;

/** RunConfig.layout to a recorded layout, the same way for everyone. Pure. */
public final class LayoutPick {
    private LayoutPick() {}

    /** seed: the one that picked it (null for a recorded id); warning: a sentence to show, or null. */
    public static class Picked {
        public final Layout layout;
        public final Long seed;
        public final String warning;

        public Picked(Layout layout, Long seed, String warning) {
            this.layout = layout;
            this.seed = seed;
            this.warning = warning;
        }
    }

    public interface Result {
        final class Ok implements Result {
            public final Picked picked;

            public Ok(Picked picked) { this.picked = picked; }
        }

        final class Failed implements Result {
            public final String message;

            public Failed(String message) { this.message = message; }
        }
    }

    public static Result pick(RunConfig config, LayoutPack pack) {
        String floor = config.floor;
        Predicate<Layout> inPool = l -> Boolean.TRUE.equals(l.playable) && l.dupOf == null && Objects.equals(l.floor, floor);
        LayoutSource src = config.layout;
        if (src instanceof LayoutSource.Seed s) return byPool(pack, s.seed, inPool, floor);
        if (src instanceof LayoutSource.Recorded r) {
            Layout l = pack.layout(r.id);
            if (l == null) return new Result.Failed("There is no recorded layout called " + r.id + " in the layout pool.");
            String warning;
            if (!Objects.equals(l.floor, floor)) warning = "That layout is from a " + l.floor + " run, not " + floor + "; it is built as recorded.";
            else if (!Boolean.TRUE.equals(l.playable)) warning = "That layout was not recorded completely, so it may not be buildable or may play oddly.";
            else if (l.dupOf != null) warning = "That layout repeats " + l.dupOf + ".";
            else warning = null;
            return new Result.Ok(new Picked(l, null, warning));
        }
        // a practice room is planned by PracticePlan, not picked as one layout
        if (src instanceof LayoutSource.Practice) return new Result.Failed("Practice builds one room, not a layout.");
        return byPool(pack, config.seed, inPool, floor);
    }

    private static Result byPool(LayoutPack pack, long seed, Predicate<Layout> filter, String floor) {
        Layout l = pack.pick(seed, filter);
        if (l == null) return new Result.Failed("The layout pool has no playable " + floor + " layouts.");
        return new Result.Ok(new Picked(l, seed, null));
    }
}
