// Ported from ADM's p3sim/pack/PackTypes.kt
package com.teslamaps.adm.sim.pack;

import java.util.ArrayList;
import java.util.List;

public class PackReport {
    private final ArrayList<Line> lines = new ArrayList<>();

    public static class Line {
        public final Level level;
        public final String text;

        public Line(Level level, String text) { this.level = level; this.text = text; }
    }

    public enum Level { INFO, WARNING, ERROR }

    public synchronized void info(String text) { lines.add(new Line(Level.INFO, text)); }
    public synchronized void warn(String text) { lines.add(new Line(Level.WARNING, text)); }
    public synchronized void error(String text) { lines.add(new Line(Level.ERROR, text)); }

    public synchronized List<Line> all() { return new ArrayList<>(lines); }

    public synchronized List<String> errors() {
        List<String> out = new ArrayList<>();
        for (Line l : lines) if (l.level == Level.ERROR) out.add(l.text);
        return out;
    }

    public synchronized List<String> warnings() {
        List<String> out = new ArrayList<>();
        for (Line l : lines) if (l.level == Level.WARNING) out.add(l.text);
        return out;
    }

    public synchronized boolean hasErrors() {
        for (Line l : lines) if (l.level == Level.ERROR) return true;
        return false;
    }
}
