package com.teslamaps.adm.practice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Ported from ADM's SimonSaysSolver.kt
public final class SsSolver<C> {

    private static final class Light<C> {
        final C cell;
        final long on;
        long off = -1L;
        Light(C cell, long on) { this.cell = cell; this.on = on; }
    }

    private enum Kind { ON, AGAIN, OFF, FLASH }

    private record Packet<C>(long tick, C cell, Kind kind) {}

    public static final int MAX_ROUNDS = 4;

    private final List<Light<C>> shown = new ArrayList<>();
    private final List<Packet<C>> packets = new ArrayList<>();
    private boolean showing = false;
    private long skip = -1L;

    private List<C> answer = List.of();
    private int next = 0;

    public List<C> answer() { return answer; }
    public int next() { return next; }

    public void lamp(C cell, boolean lit, boolean was, long tick) {
        Kind kind = lit ? (was ? Kind.AGAIN : Kind.ON) : (was ? Kind.OFF : Kind.FLASH);
        if (kind == Kind.ON && skip < 0 && !showing) packets.clear();
        if (kind != Kind.FLASH || showing || skip >= 0) packets.add(new Packet<>(tick, cell, kind));
        switch (kind) {
            case ON -> {
                if (skip >= 0) { shown.add(new Light<>(cell, tick)); answer = lastChain(tick); }
                else lightOn(cell, tick);
            }
            case OFF -> { lightOff(cell, tick); if (skip >= 0) answer = lastChain(tick); }
            default -> { if (skip >= 0) answer = lastChain(tick); }
        }
    }

    public void serverTick(long tick) {
        if (skip >= 0) answer = lastChain(tick);
    }

    private void lightOn(C cell, long tick) {
        if (!showing) { showing = true; shown.clear(); answer = List.of(); next = 0; }
        shown.add(new Light<>(cell, tick));
        answer = soFar();
    }

    private void lightOff(C cell, long tick) {
        for (int i = shown.size() - 1; i >= 0; i--) {
            Light<C> l = shown.get(i);
            if (Objects.equals(l.cell, cell) && l.off < 0) { l.off = tick; break; }
        }
        if (showing) answer = soFar();
    }

    private List<C> soFar() {
        List<Light<C>> a = new ArrayList<>(shown);
        while (a.size() > 1 && a.get(0).off >= 0 && a.get(0).off - a.get(0).on <= 2) a.remove(0);
        List<C> out = new ArrayList<>();
        for (Light<C> l : a) out.add(l.cell);
        return out;
    }

    private long lastStart(long before) {
        long last = -1L;
        for (Packet<C> p : packets) {
            if (p.tick >= before) break;
            boolean clear = p.kind == Kind.FLASH;
            if (!clear && (p.kind == Kind.AGAIN || p.kind == Kind.OFF)) {
                boolean explained = false;
                for (Packet<C> q : packets) {
                    if (q.tick == p.tick - 8 && Objects.equals(q.cell, p.cell) && q.kind != Kind.OFF) { explained = true; break; }
                }
                clear = !explained;
            }
            if (!clear) continue;
            for (Packet<C> q : packets) {
                if (q.tick == p.tick && (q.kind == Kind.ON || q.kind == Kind.AGAIN)) { last = p.tick; break; }
            }
        }
        return last;
    }

    private List<C> lastChain(long now) {
        List<C> out = new ArrayList<>();
        for (int j = 0; j < MAX_ROUNDS; j++) {
            long t = skip + 8L * j;
            if (t > now) break;
            C exact = null;
            boolean any = false;
            for (Packet<C> p : packets) {
                if (p.tick == t && (p.kind == Kind.ON || p.kind == Kind.AGAIN)) { exact = p.cell; any = true; }
            }
            if (t == now && !any) break;
            C cell = exact;
            if (cell == null && j != 0) {
                List<C> lit = litAt(t);
                cell = lit.isEmpty() ? null : lit.get(lit.size() - 1);
            }
            if (cell == null) break;
            out.add(cell);
        }
        return out;
    }

    private List<C> litAt(long t) {
        Map<C, Long> last = new LinkedHashMap<>();
        for (Packet<C> p : packets) {
            if (p.tick > t) break;
            if (p.kind != Kind.OFF) last.put(p.cell, p.tick);
            if (p.kind == Kind.OFF || p.kind == Kind.FLASH) last.remove(p.cell);
        }
        List<C> out = new ArrayList<>();
        for (Map.Entry<C, Long> e : last.entrySet()) if (e.getValue() > t - 8) out.add(e.getKey());
        return out;
    }

    public void buttonsUp(long tick) {
        if (!showing) return;
        showing = false;
        next = 0;
        long start = lastStart(tick);
        if (start >= 0) { skip = start; answer = lastChain(tick); return; }
        List<Light<C>> a = new ArrayList<>(shown);
        while (a.size() > 1 && a.get(0).off >= 0 && a.get(0).off - a.get(0).on <= 2) a.remove(0);
        boolean anyOn = false;
        for (Light<C> l : shown) if (l.off < 0) { anyOn = true; break; }
        if (anyOn && a.size() >= 3) a.remove(0);
        List<C> out = new ArrayList<>();
        for (Light<C> l : a) out.add(l.cell);
        answer = out;
    }

    public void buttonsGone(long tick) {
        if (skip >= 0) {
            skip = -1;
            List<Light<C>> fresh = new ArrayList<>();
            for (Light<C> l : shown) if (l.on == tick) fresh.add(l);
            packets.removeIf(p -> p.tick != tick);
            shown.clear();
            shown.addAll(fresh);
            showing = !fresh.isEmpty();
            answer = showing ? soFar() : List.of();
            next = 0;
            return;
        }
        if (showing) return;
        answer = List.of();
        next = 0;
    }

    public void pressed(C cell, long tick) {
        if (showing) buttonsUp(tick);
        if (next < answer.size() && Objects.equals(answer.get(next), cell)) { next++; return; }
        int i = answer.indexOf(cell);
        if (i >= next) next = i + 1;
    }

    public void reset() {
        shown.clear(); packets.clear(); showing = false; skip = -1; answer = List.of(); next = 0;
    }
}
