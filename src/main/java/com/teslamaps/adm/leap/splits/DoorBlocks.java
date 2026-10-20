package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.List;

// Ported from ADM's splits/DoorBlocks.kt
public final class DoorBlocks {

    private DoorBlocks() {}

    public static final int DOOR_BLOCKS = 30;

    public record Door(int ax, int az, int bx, int bz) {}

    public static List<Door> doors(List<int[]> blocks) {
        List<List<int[]>> groups = new ArrayList<>();
        for (int[] p : blocks) {
            List<int[]> g = null;
            for (List<int[]> gr : groups) {
                if (Math.abs(gr.get(0)[0] - p[0]) <= 4 && Math.abs(gr.get(0)[1] - p[1]) <= 4) { g = gr; break; }
            }
            if (g != null) g.add(p);
            else { List<int[]> n = new ArrayList<>(); n.add(p); groups.add(n); }
        }
        List<Door> out = new ArrayList<>();
        for (List<int[]> g : groups) {
            if (g.size() < DOOR_BLOCKS) continue;
            double sx = 0, sz = 0;
            for (int[] p : g) { sx += p[0]; sz += p[1]; }
            double tx = (sx / g.size() + 185) / 32;
            double tz = (sz / g.size() + 185) / 32;
            if (Math.abs(tx - Math.round(tx)) < 0.1 && Math.abs(tz - Math.round(tz)) < 0.1) continue;
            out.add(new Door((int) Math.floor(tx + 0.01), (int) Math.floor(tz + 0.01), (int) Math.ceil(tx - 0.01), (int) Math.ceil(tz - 0.01)));
        }
        return out;
    }
}
