package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/GhostRun.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GhostRun {
    public final String clazz;
    public final String skill;
    public final int time;
    public final List<Frame> frames;
    public final List<Ev> events;
    public final int[] sectionN;
    public final long date;

    public GhostRun(String clazz, String skill, int time, List<Frame> frames, List<Ev> events, int[] sectionN, long date) {
        this.clazz = clazz;
        this.skill = skill;
        this.time = time;
        this.frames = frames;
        this.events = events;
        this.sectionN = sectionN;
        this.date = date;
    }

    public GhostRun(String clazz, String skill, int time, List<Frame> frames, List<Ev> events, int[] sectionN) {
        this(clazz, skill, time, frames, events, sectionN, 0);
    }

    public static final class Frame {
        public final P p;
        public final float yaw;
        public final float pitch;
        public final String held;

        public Frame(P p, float yaw, float pitch, String held) {
            this.p = p;
            this.yaw = yaw;
            this.pitch = pitch;
            this.held = held;
        }

        public Frame(P p, float yaw, float pitch) { this(p, yaw, pitch, ""); }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Frame f)) return false;
            return Objects.equals(p, f.p) && Float.compare(yaw, f.yaw) == 0 && Float.compare(pitch, f.pitch) == 0 && Objects.equals(held, f.held);
        }

        @Override public int hashCode() { return Objects.hash(p, yaw, pitch, held); }

        @Override public String toString() { return "Frame(p=" + p + ", yaw=" + yaw + ", pitch=" + pitch + ", held=" + held + ")"; }
    }

    public static final class Ev {
        public final int n;
        public final String type;
        public final String a;
        public final int k;

        public Ev(int n, String type, String a, int k) {
            this.n = n;
            this.type = type;
            this.a = a;
            this.k = k;
        }

        public Ev(int n, String type, String a) { this(n, type, a, 0); }

        public Ev(int n, String type) { this(n, type, "", 0); }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Ev e)) return false;
            return n == e.n && k == e.k && Objects.equals(type, e.type) && Objects.equals(a, e.a);
        }

        @Override public int hashCode() { return Objects.hash(n, type, a, k); }

        @Override public String toString() { return "Ev(n=" + n + ", type=" + type + ", a=" + a + ", k=" + k + ")"; }
    }

    public Frame frame(int n) { return frames.get(Math.clamp(n, 0, frames.size() - 1)); }

    public int end() { return frames.size() - 1; }

    public List<String> jobs() {
        List<String> out = new ArrayList<>();
        for (Ev e : events) if (e.type.equals("done")) out.add(e.a);
        for (Ev e : events) if (e.type.equals("gate")) out.add("gate " + e.k);
        return out;
    }

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("class", clazz);
        o.addProperty("skill", skill);
        o.addProperty("time", time);
        o.addProperty("date", date);
        JsonArray sections = new JsonArray();
        for (int s : sectionN) sections.add(s);
        o.add("sections", sections);
        JsonArray f = new JsonArray();
        String held = "";
        JsonArray heldAt = new JsonArray();
        for (int i = 0; i < frames.size(); i++) {
            Frame fr = frames.get(i);
            f.add(r(fr.p.x)); f.add(r(fr.p.y)); f.add(r(fr.p.z)); f.add(r1(fr.yaw)); f.add(r1(fr.pitch));
            if (!fr.held.equals(held)) {
                held = fr.held;
                JsonArray a = new JsonArray();
                a.add(i);
                a.add(held);
                heldAt.add(a);
            }
        }
        o.add("frames", f);
        o.add("held", heldAt);
        JsonArray evs = new JsonArray();
        for (Ev e : events) {
            JsonArray a = new JsonArray();
            a.add(e.n); a.add(e.type); a.add(e.a); a.add(e.k);
            evs.add(a);
        }
        o.add("events", evs);
        return o.toString();
    }

    public static final int VERSION = 1;

    private static double r(double d) { return Math.round(d * 100) / 100.0; }

    private static double r1(double d) { return Math.round(d * 10) / 10.0; }

    public static GhostRun fromJson(String text) {
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            JsonElement v = o.get("v");
            if (v == null || v.getAsInt() != VERSION) return null;
            JsonArray f = o.getAsJsonArray("frames");
            if (f.size() == 0 || f.size() % 5 != 0) return null;
            List<Pair<Integer, String>> heldAt = new ArrayList<>();
            JsonArray heldArr = o.getAsJsonArray("held");
            if (heldArr != null) for (JsonElement it : heldArr) {
                JsonArray a = it.getAsJsonArray();
                heldAt.add(Pair.of(a.get(0).getAsInt(), a.get(1).getAsString()));
            }
            int h = 0;
            String held = "";
            List<Frame> frames = new ArrayList<>();
            for (int i = 0; i < f.size() / 5; i++) {
                while (h < heldAt.size() && heldAt.get(h).first <= i) { held = heldAt.get(h).second; h++; }
                frames.add(new Frame(new P(f.get(i * 5).getAsDouble(), f.get(i * 5 + 1).getAsDouble(), f.get(i * 5 + 2).getAsDouble()),
                    f.get(i * 5 + 3).getAsFloat(), f.get(i * 5 + 4).getAsFloat(), held));
            }
            List<Ev> events = new ArrayList<>();
            for (JsonElement it : o.getAsJsonArray("events")) {
                JsonArray a = it.getAsJsonArray();
                events.add(new Ev(a.get(0).getAsInt(), a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsInt()));
            }
            JsonArray secArr = o.getAsJsonArray("sections");
            int[] sections = new int[secArr.size()];
            for (int i = 0; i < sections.length; i++) sections[i] = secArr.get(i).getAsInt();
            if (sections.length != 6) return null;
            int time = o.get("time").getAsInt();
            if (time < 0 || time >= frames.size()) return null;
            JsonElement date = o.get("date");
            return new GhostRun(o.get("class").getAsString(), o.get("skill").getAsString(), time, frames, events, sections, date != null ? date.getAsLong() : 0);
        } catch (Throwable t) {
            return null;
        }
    }
}
