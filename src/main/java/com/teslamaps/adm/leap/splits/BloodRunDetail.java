package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's splits/BloodRunDetail.kt
public final class BloodRunDetail {

    public record MapRoom(String id, String name, boolean fairy, boolean entrance) {}

    public enum Level { COMPACT, DETAILED, DEBUG }

    private static final String UNNAMED = "...";
    private static final String NAME = "§5", FAIRY = "§d", TOTAL = "§6";
    private static final String KEY = "§c", SLOW_KEY = "§4", DOOR = "§7";
    private static final long SLOW_PICKUP_MS = 350L, SLOW_DOOR_MS = 250L;
    private static final List<String> LABELS = List.of("door fell", "last mob", "pickup", "opened", "room total");
    private static final List<String> COLOURS = List.of("§8", "§7", "§c", "§4", "§6");
    private static final List<String> FULL_LABELS = List.of("door fell", "last mob killed", "key picked up", "key delta", "door opened", "door delta", "total room time");
    private static final List<String> FULL_COLOURS = List.of("§8", "§7", "§c", "§c", "§4", "§4", "§6");
    private static final String MORT = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";
    private static final String BLOOD_DOOR = "The BLOOD DOOR has been opened!";
    private static final Pattern KEY_PICKED = Pattern.compile("^(?:\\[[^\\]]+] )?(\\w+) has obtained (?:Wither|Blood) Key!$|^A (?:Wither|Blood) Key was picked up!$");
    private static final Pattern WITHER_DOOR = Pattern.compile("^(\\w+) opened a WITHER door!$");

    private static final class Room {
        Stamp start;
        String name = UNNAMED;
        String mapId;
        boolean toFairy;
        Stamp doorFell, mobKilled, keyPicked, doorOpened;
        String keyBy = "", doorBy = "";
        String startFrom = "the door line";
        Double keySeenAt;
        boolean pickupAnonymous;
        Room(Stamp start) { this.start = start; }
    }

    private record Span(long ms, long ticks) {}

    private final List<Room> rooms = new ArrayList<>();
    private Room room;
    private boolean started, done;

    public void reset() { rooms.clear(); room = null; started = false; done = false; }

    public boolean active() { return started && !done; }

    public void onDoorStart(Stamp at, MapRoom a, MapRoom b) {
        Room r = room;
        if (r == null) return;
        if (rooms.isEmpty() && r.doorFell == null && ((a != null && a.entrance) || (b != null && b.entrance))) {
            r.start = at;
            r.startFrom = "the start door seen falling";
        }
    }

    public void onDoorDown(Stamp at, MapRoom a, MapRoom b) {
        Room r = room;
        if (r == null || r.doorFell != null) return;
        List<MapRoom> sides = new ArrayList<>();
        if (a != null) sides.add(a);
        if (b != null) sides.add(b);
        if (rooms.isEmpty()) {
            boolean ent = false;
            for (MapRoom s : sides) if (s.entrance) ent = true;
            if (!ent) return;
        } else if (r.doorOpened == null && at.realMs() < r.start.realMs()) return;

        r.doorFell = at;
        Room previous = rooms.isEmpty() ? null : rooms.get(rooms.size() - 1);
        MapRoom fairy = null;
        for (MapRoom s : sides) if (s.fairy) { fairy = s; break; }
        boolean fromPrev = false;
        for (MapRoom s : sides) if (previous != null && s.id.equals(previous.mapId)) fromPrev = true;
        if (fairy != null && fromPrev) {
            r.name = fairy.name; r.mapId = fairy.id;
            previous.toFairy = true;
            return;
        }
        MapRoom next = null;
        for (MapRoom s : sides) {
            if (!s.entrance && !s.fairy && (previous == null || !s.id.equals(previous.mapId))) { next = s; break; }
        }
        if (next != null) { r.name = next.name; r.mapId = next.id; }
        if (fairy != null && previous != null && !fairy.id.equals(previous.mapId)) previous.toFairy = true;
    }

    public void onKeySpawned(Stamp at, double distance) {
        Room r = room;
        if (r == null) return;
        if (r.mobKilled == null) { r.mobKilled = at; r.keySeenAt = distance; }
    }

    public void onChat(String msg, Stamp at) {
        if (done) return;
        if (!started) {
            if (msg.equals(MORT)) {
                started = true;
                room = new Room(at);
                room.startFrom = "Mort's line (start door not seen falling)";
            }
            return;
        }
        Room r = room;
        if (r == null) return;
        Matcher key = KEY_PICKED.matcher(msg);
        if (key.find()) {
            if (r.keyPicked == null) {
                r.keyPicked = at;
                r.keyBy = key.group(1) == null ? "" : key.group(1);
                r.pickupAnonymous = r.keyBy.isEmpty();
                if (r.mobKilled == null) r.mobKilled = at;
            }
            return;
        }
        Matcher door = WITHER_DOOR.matcher(msg);
        if (door.find()) {
            r.doorOpened = at;
            r.doorBy = door.group(1);
            rooms.add(r);
            room = new Room(at);
            return;
        }
        if (msg.equals(BLOOD_DOOR)) {
            r.doorOpened = at;
            rooms.add(r);
            room = null;
            done = true;
        }
    }

    private List<Room> all() {
        List<Room> out = new ArrayList<>(rooms);
        if (room != null) out.add(room);
        return out;
    }

    public List<String> lines(Level level, Stamp now, boolean totalRow) {
        return switch (level) {
            case COMPACT -> compact(now, totalRow);
            case DETAILED -> detailed(now);
            case DEBUG -> debug(now);
        };
    }

    private List<String> compact(Stamp now, boolean totalRow) {
        List<String> out = new ArrayList<>();
        for (Room r : all()) {
            Stamp live = r == room ? now : null;
            out.add(row(name(r) + ": ", keySpan(r, live), pickupSpan(r, live), doorSpan(r, live), span(r.start, r.doorOpened)));
        }
        if (totalRow && done && !rooms.isEmpty()) {
            out.add(row(TOTAL + "Avg: ", avg(x -> keySpan(x, null)), avg(x -> pickupSpan(x, null)), avg(x -> doorSpan(x, null)), avg(x -> span(x.start, x.doorOpened))));
        }
        return out;
    }

    private Span avg(Function<Room, Span> f) {
        List<Span> vals = new ArrayList<>();
        for (Room r : rooms) { Span s = f.apply(r); if (s != null) vals.add(s); }
        return vals.isEmpty() ? null : mean(vals);
    }

    private Span keySpan(Room r, Stamp live) {
        Stamp from = r.doorFell != null ? r.doorFell : (r.keyPicked != null ? r.start : null);
        Span s = span(from, r.keyPicked != null ? r.keyPicked : live);
        return s == null ? null : new Span(Math.max(s.ms, 0L), Math.max(s.ticks, 0L));
    }

    private Span pickupSpan(Room r, Stamp live) { return span(r.mobKilled, r.keyPicked != null ? r.keyPicked : live); }
    private Span doorSpan(Room r, Stamp live) { return span(r.keyPicked, r.doorOpened != null ? r.doorOpened : live); }

    private String row(String name, Span key, Span pickup, Span door, Span total) {
        String keyColour = pickup != null && pickup.ms > SLOW_PICKUP_MS ? SLOW_KEY : KEY;
        List<String> cells = List.of(
                door != null && door.ms > SLOW_DOOR_MS ? DOOR + Split.seconds(door.ms) + " §8| " : "",
                key != null ? keyColour + Split.seconds(key.ms) + " " : "",
                name,
                total != null ? TOTAL + Split.seconds(total.ms) : "");
        String s = String.join("\t", cells);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\t') end--;
        return s.substring(0, end);
    }

    private List<String> detailed(Stamp now) {
        List<String> out = new ArrayList<>();
        for (Room r : all()) {
            out.add(name(r));
            List<Span> st = stats(r, now);
            for (int i = 0; i < st.size(); i++) if (st.get(i) != null) out.add(labelled(COLOURS.get(i), LABELS.get(i), st.get(i)));
            out.add("");
        }
        List<Span> avg = averages();
        if (avg != null) {
            for (int i = 0; i < avg.size(); i++) if (avg.get(i) != null) out.add(labelled(COLOURS.get(i), LABELS.get(i) + " avg", avg.get(i)));
        }
        return out;
    }

    private record Full(int i, Span span, String who) {}

    private List<String> debug(Stamp now) {
        List<String> out = new ArrayList<>();
        for (Room r : all()) {
            out.add(name(r));
            for (Full f : full(r, now)) if (f.span != null) out.add(labelled(FULL_COLOURS.get(f.i), FULL_LABELS.get(f.i), f.span) + by(f.who));
            out.addAll(how(r));
            out.add("");
        }
        if (done && !rooms.isEmpty()) {
            for (int i = 0; i < FULL_LABELS.size(); i++) {
                List<Span> vals = new ArrayList<>();
                for (Room r : rooms) { Span s = full(r, now).get(i).span; if (s != null) vals.add(s); }
                if (!vals.isEmpty()) out.add(labelled(FULL_COLOURS.get(i), "average " + FULL_LABELS.get(i), mean(vals)));
            }
        }
        return out;
    }

    private List<String> how(Room r) {
        List<String> out = new ArrayList<>();
        out.add("§8· start: " + r.startFrom);
        out.add(r.doorFell != null ? "§8· door down: seen (its blocks)" : "§8· door down: not seen - too far away; key time counts from the start");
        if (r.keySeenAt != null) out.add("§8· key dropped: seen " + String.format(Locale.ROOT, "%.0f blocks", r.keySeenAt) + " away");
        else if (r.keyPicked != null) out.add("§8· key dropped: not seen (out of render distance) - put at the pickup line");
        else out.add("§8· key dropped: not yet");
        if (r.keyPicked != null) out.add(r.pickupAnonymous ? "§8· picked up: chat, with no name" : "§8· picked up: chat");
        if (r.doorOpened != null) out.add("§8· opened: chat");
        if (UNNAMED.equals(r.name)) out.add("§8· name: unknown - its door was never seen");
        Span s = span(r.start, r.doorOpened);
        if (s != null) {
            long lag = s.ms - s.ticks * 50;
            if (Math.abs(lag) >= 100) out.add("§8· lag: server " + Split.seconds(Math.abs(lag)) + (lag > 0 ? " behind" : " ahead") + " real time");
        }
        return out;
    }

    private static Span span(Stamp from, Stamp to) {
        if (from == null || to == null) return null;
        return new Span(to.realMs() - from.realMs(), to.tick() - from.tick());
    }

    private List<Span> stats(Room r, Stamp now) {
        Stamp live = r == room ? now : null;
        Stamp killFrom = r.doorFell != null ? r.doorFell : (r.mobKilled != null ? r.start : null);
        Span kill = span(killFrom, r.mobKilled != null ? r.mobKilled : live);
        List<Span> out = new ArrayList<>();
        out.add(span(r.start, r.doorFell != null ? r.doorFell : live));
        out.add(kill == null ? null : new Span(Math.max(kill.ms, 0L), Math.max(kill.ticks, 0L)));
        out.add(span(r.mobKilled, r.keyPicked != null ? r.keyPicked : live));
        out.add(span(r.keyPicked, r.doorOpened != null ? r.doorOpened : live));
        out.add(span(r.start, r.doorOpened));
        return out;
    }

    private List<Full> full(Room r, Stamp now) {
        Stamp live = r == room ? now : null;
        return List.of(
                new Full(0, span(r.start, r.doorFell != null ? r.doorFell : live), ""),
                new Full(1, span(r.start, r.mobKilled != null ? r.mobKilled : live), ""),
                new Full(2, span(r.start, r.keyPicked != null ? r.keyPicked : (r.mobKilled != null ? live : null)), r.keyBy),
                new Full(3, span(r.mobKilled, r.keyPicked != null ? r.keyPicked : live), ""),
                new Full(4, span(r.start, r.doorOpened != null ? r.doorOpened : (r.keyPicked != null ? live : null)), r.doorBy),
                new Full(5, span(r.keyPicked, r.doorOpened != null ? r.doorOpened : live), ""),
                new Full(6, span(r.start, r.doorOpened), ""));
    }

    private List<Span> averages() {
        if (!done || rooms.isEmpty()) return null;
        List<Span> out = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            List<Span> col = new ArrayList<>();
            for (Room r : rooms) { Span s = stats(r, r.doorOpened).get(i); if (s != null) col.add(s); }
            out.add(col.isEmpty() ? null : mean(col));
        }
        return out;
    }

    private static Span mean(List<Span> vals) {
        long a = 0, b = 0;
        for (Span s : vals) { a += s.ms; b += s.ticks; }
        return new Span(a / vals.size(), b / vals.size());
    }

    private static String labelled(String colour, String label, Span s) {
        return colour + label + " §b> " + colour + Split.seconds(s.ms) + " §8(§7" + Split.seconds(s.ticks * 50L) + "§8)";
    }

    private static String name(Room r) { return (r.toFairy ? FAIRY : NAME) + r.name; }

    private static String by(String who) { return who.isEmpty() ? "" : " §7" + who; }
}
