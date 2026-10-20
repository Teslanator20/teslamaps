package com.teslamaps.adm.practice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.ChatQueue;
import com.teslamaps.player.PlayerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's BrRoles.kt: who kills which BR box. Plans are the site's brroles.json, bundled.
final class BrRoles {
    private BrRoles() {}

    record Plan(List<List<Integer>> roles, List<List<Integer>> stacks) {}

    sealed interface Look permits Mine, Stack, Theirs {}
    record Mine(int order) implements Look {}
    record Stack() implements Look {}
    record Theirs(int role) implements Look {}

    enum DoorKills { NEVER, ALWAYS, AFTER_FAIRY }

    static final int MAX_KILLING = 4;

    private record Door(int x, int z) {}
    private record DoorPair(Door entry, Door wither) {}

    private static Map<String, Map<Door, Map<Integer, Plan>>> plans = Map.of();
    private static Map<String, Map<Door, Map<Integer, Plan>>> m7Plans = Map.of();
    private static Map<String, Map<DoorPair, Map<Integer, Plan>>> doorerPlans = Map.of();
    private static Map<String, Map<DoorPair, Map<Integer, Plan>>> doorerM7Plans = Map.of();
    private static Set<String> mini = Set.of();
    private static boolean loaded;

    static boolean isMini(String room) { return mini.contains(room); }

    static int settingKilling = 2;
    static Integer settingRole = 2;
    static DoorKills doorKills = DoorKills.NEVER;
    static boolean dropBottom = false;

    static boolean doorerKills(boolean late) {
        return doorKills == DoorKills.ALWAYS || (doorKills == DoorKills.AFTER_FAIRY && late);
    }

    static boolean dropped(boolean late) {
        return late && doorKills == DoorKills.AFTER_FAIRY && dropBottom && count() > 0;
    }

    static boolean youDropped(boolean late) {
        Integer m = mine();
        return dropped(late) && m != null && m == count();
    }

    static int planCount(boolean late) {
        int c = count();
        if (c == 0) return 0;
        return c - (dropped(late) ? 1 : 0) + (doorerKills(late) ? 1 : 0);
    }

    static Integer playing(boolean late) {
        if (youDropped(late)) return null;
        Integer m = mine();
        if (m != null) return m;
        int n = planCount(late);
        return doorerKills(late) && youOnDoor() && n > 0 ? n : null;
    }

    static boolean master() {
        String f = DungeonManager.getFloorName();
        return f != null && f.startsWith("M");
    }

    private static Integer baseRole() {
        Integer s = settingRole;
        if (s == null) return null;
        if (!master()) return s;
        String cls = PlayerTracker.getLocalClass();
        if (cls == null) return s;
        return switch (cls) {
            case "Archer" -> 1;
            case "Mage" -> 2;
            case "Berserk" -> 3;
            case "Tank" -> 4;
            case "Healer" -> 0;
            default -> s;
        };
    }

    private static Integer chatCount;
    private static Integer chatMine;
    private static final Map<String, Integer> taken = new LinkedHashMap<>();
    private static String doorClaim;

    static int count() {
        if (chatCount != null) return chatCount;
        Integer b = baseRole();
        return b == null || b > settingKilling ? 0 : settingKilling;
    }

    static boolean youOnDoor() {
        String me = me();
        if (doorClaim != null) return doorClaim.equalsIgnoreCase(me == null ? "" : me);
        Integer b = baseRole();
        return b != null && b == 0 && chatMine == null;
    }

    static boolean active() {
        return count() > 0 && (youOnDoor() || mine() != null);
    }

    static Integer mine() {
        if (youOnDoor()) return null;
        if (chatMine != null) return chatMine;
        Integer b = baseRole();
        return b != null && b >= 1 && b <= count() ? b : null;
    }

    private static final Pattern CLAIM = Pattern.compile("^Party > (?:\\[[^]]*] )?(\\w{1,16}): !([2-4])?br ([1-4]|d)$");

    private static String me() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.getGameProfile().name();
    }

    static String doorRunner() {
        if (doorClaim != null) return doorClaim;
        return youOnDoor() ? me() : null;
    }

    /** A chat line (§ stripped): a role claim is taken note of. True if it was one. */
    static boolean onChat(String line) {
        Matcher m = CLAIM.matcher(line);
        if (!m.matches()) return false;
        String name = m.group(1);
        Integer n = m.group(2) == null ? null : Integer.parseInt(m.group(2));
        String what = m.group(3);
        String meName = me();
        boolean you = meName != null && name.equalsIgnoreCase(meName);
        if (n != null && n != count()) { taken.clear(); chatMine = null; }
        if (n != null) chatCount = n;
        if (what.equals("d")) {
            doorClaim = name;
            taken.remove(name);
            if (you) chatMine = null;
            MutableComponent c = Component.literal(you ? "§dBR §7you are on the §6door" : "§dBR §f" + name + " §7is on the §6door");
            offer(c, you);
            PracticeUtil.msg(c);
            return true;
        }
        int killing = n != null ? n : chatCount != null ? chatCount : settingKilling;
        int role = Integer.parseInt(what);
        if (role > killing) return true;
        chatCount = killing;
        taken.remove(name);
        taken.put(name, role);
        if (doorClaim != null && doorClaim.equalsIgnoreCase(name)) doorClaim = null;
        if (you) chatMine = role;
        List<String> clash = new ArrayList<>();
        for (Map.Entry<String, Integer> e : taken.entrySet()) if (e.getValue() == role && !e.getKey().equals(name)) clash.add(e.getKey());
        MutableComponent c = Component.literal(you ? "§dBR §7you are role §f" + role + " §7of §f" + count() : "§dBR §f" + name + " §7is role §f" + role + " §7of §f" + count());
        if (!clash.isEmpty()) c.append(Component.literal(" §c(so is " + String.join(", ", clash) + ")"));
        offer(c, you);
        PracticeUtil.msg(c);
        return true;
    }

    private static void offer(MutableComponent line, boolean you) {
        if (you || mine() != null || youOnDoor() || count() == 0) return;
        line.append(Component.literal(" §7— yours:"));
        for (int r = 1; r <= count(); r++) {
            String holder = null;
            for (Map.Entry<String, Integer> e : taken.entrySet()) if (e.getValue() == r) { holder = e.getKey(); break; }
            if (holder != null) {
                String h = holder;
                line.append(Component.literal(" §8[" + r + "]").withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Taken by §f" + h)))));
            } else {
                line.append(button(" §a[" + r + "]", "/brrole " + count() + " " + r, "§7Say §f!" + count() + "br " + r + " §7in party chat"));
            }
        }
        if (doorClaim == null) line.append(button(" §6[door]", "/brrole door", "§7Say §f!br d §7in party chat"));
    }

    private static Component button(String text, String command, String hover) {
        return Component.literal(text).withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    static void claim(int n, int role) {
        if (n < 2 || n > MAX_KILLING || role < 1 || role > n) {
            PracticeUtil.msg("§c2 to " + MAX_KILLING + " kill (the door runner is extra): /brrole 3 2");
            return;
        }
        ChatQueue.command("pc !" + n + "br " + role);
    }

    static void claimDoor() {
        ChatQueue.command(count() > 0 ? "pc !" + count() + "br d" : "pc !br d");
    }

    static void status() {
        int c = count();
        if (c == 0 && doorRunner() == null) {
            PracticeUtil.msg("§dBR §7no roles yet. §f/brrole <killing> <role>§7 or §f/brrole door§7, or §f!3br 2§7 / §f!br d§7 in party chat.");
            return;
        }
        StringBuilder who = new StringBuilder();
        for (int r = 1; r <= c; r++) {
            if (r > 1) who.append("§7, ");
            String holder = null;
            for (Map.Entry<String, Integer> e : taken.entrySet()) if (e.getValue() == r) { holder = e.getKey(); break; }
            who.append("§f").append(r).append(" §7").append(holder != null ? holder : "§8free");
        }
        String dr = doorRunner();
        String door = dr != null ? "§6door §7" + dr : "§6door §8unknown";
        String from = chatCount != null ? "§8(party chat)" : "§8(settings)";
        Integer m = mine();
        PracticeUtil.msg("§dBR §7" + c + " killing " + from + "§7: " + who + "§7, " + door + (m != null ? " §7· you §f" + m : ""));
    }

    static String describe(boolean late) {
        String from = (chatCount != null || doorClaim != null ? "party chat" : master() ? "class" : "settings") + (master() ? ", M7 roles" : "");
        int c = count();
        if (c == 0) return "roles off";
        if (youOnDoor()) return (doorerKills(late) ? "door (kills, role " + planCount(late) + ")" : "door") + ", " + c + " killing (" + from + ")";
        if (youDropped(late)) return "role " + mine() + " of " + c + ", dropped to clear past fairy (" + from + ")";
        if (mine() != null) return "role " + mine() + " of " + c + " (" + from + ")";
        return "no role, " + c + " killing (" + from + ")";
    }

    static void newRun(boolean dungeonOver) {
        if (dungeonOver) { taken.clear(); chatCount = null; chatMine = null; doorClaim = null; }
    }

    // --- the plans (bundled brroles.json) ---

    static void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = BrRoles.class.getResourceAsStream("/assets/teslamaps/adm/brroles.json")) {
            if (in == null) return;
            JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!doc.has("rooms") || !doc.get("rooms").isJsonObject()) return;
            plans = readBy(doc.getAsJsonObject("rooms"), BrRoles::xz);
            m7Plans = obj(doc, "m7") != null ? readBy(obj(doc, "m7"), BrRoles::xz) : Map.of();
            Set<String> mn = new HashSet<>();
            JsonObject miniObj = obj(doc, "mini");
            if (miniObj != null) for (Map.Entry<String, JsonElement> e : miniObj.entrySet())
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsBoolean()) mn.add(e.getKey());
            mini = mn;
            doorerPlans = obj(doc, "doorer") != null ? readBy(obj(doc, "doorer"), BrRoles::pair) : Map.of();
            doorerM7Plans = obj(doc, "doorerM7") != null ? readBy(obj(doc, "doorerM7"), BrRoles::pair) : Map.of();
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[adm] brroles: the bundled roles couldn't be read: {}", e.getMessage());
        }
    }

    private static JsonObject obj(JsonObject doc, String key) {
        JsonElement e = doc.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static Door xz(String s) {
        String[] p = s.split(",");
        if (p.length != 2) return null;
        try { return new Door(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())); }
        catch (NumberFormatException e) { return null; }
    }

    private static DoorPair pair(String key) {
        String[] p = key.split(">");
        if (p.length != 2) return null;
        Door a = xz(p[0]), b = xz(p[1]);
        return a == null || b == null ? null : new DoorPair(a, b);
    }

    private static <K> Map<String, Map<K, Map<Integer, Plan>>> readBy(JsonObject rooms, Function<String, K> key) {
        Map<String, Map<K, Map<Integer, Plan>>> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> room : rooms.entrySet()) {
            Map<K, Map<Integer, Plan>> byDoor = new HashMap<>();
            for (Map.Entry<String, JsonElement> door : room.getValue().getAsJsonObject().entrySet()) {
                K k = key.apply(door.getKey());
                if (k == null) continue;
                Map<Integer, Plan> byCount = new HashMap<>();
                for (Map.Entry<String, JsonElement> n : door.getValue().getAsJsonObject().entrySet()) {
                    try {
                        JsonObject o = n.getValue().getAsJsonObject();
                        List<List<Integer>> roles = ints(o.getAsJsonArray("roles"));
                        List<List<Integer>> stacks = o.has("stacks") ? ints(o.getAsJsonArray("stacks")) : null;
                        if (stacks == null) { stacks = new ArrayList<>(); for (int i = 0; i < roles.size(); i++) stacks.add(List.of()); }
                        byCount.put(Integer.parseInt(n.getKey()), new Plan(roles, stacks));
                    } catch (Exception ignored) {
                    }
                }
                byDoor.put(k, byCount);
            }
            out.put(room.getKey(), byDoor);
        }
        return out;
    }

    private static List<List<Integer>> ints(com.google.gson.JsonArray a) {
        List<List<Integer>> out = new ArrayList<>();
        for (JsonElement r : a) {
            List<Integer> l = new ArrayList<>();
            for (JsonElement i : r.getAsJsonArray()) l.add(i.getAsInt());
            out.add(l);
        }
        return out;
    }

    static Plan planFor(String room, int doorX, int doorZ, int[] wither, boolean late) {
        if (count() == 0) return null;
        int n = planCount(late);
        Door door = new Door(doorX, doorZ);
        if (doorerKills(late)) {
            if (wither == null) return null;
            Door w = new Door(wither[0], wither[1]);
            if (master()) { Plan p = doorerIn(doorerM7Plans, room, door, w, n); if (p != null) return p; }
            return doorerIn(doorerPlans, room, door, w, n);
        }
        if (master()) { Plan p = planIn(m7Plans, room, door, n); if (p != null) return p; }
        return planIn(plans, room, door, n);
    }

    private static int d(Door a, Door b) { return Math.abs(a.x - b.x) + Math.abs(a.z - b.z); }

    private static Plan planIn(Map<String, Map<Door, Map<Integer, Plan>>> table, String room, Door door, int n) {
        Map<Door, Map<Integer, Plan>> doors = table.get(room);
        if (doors == null) return null;
        Door near = null;
        for (Door k : doors.keySet()) if (near == null || d(k, door) < d(near, door)) near = k;
        if (near == null || d(near, door) > 3) return null;
        return doors.get(near).get(n);
    }

    private static Plan doorerIn(Map<String, Map<DoorPair, Map<Integer, Plan>>> table, String room, Door door, Door wither, int n) {
        Map<DoorPair, Map<Integer, Plan>> doors = table.get(room);
        if (doors == null) return null;
        DoorPair near = null;
        for (DoorPair k : doors.keySet())
            if (near == null || d(k.entry, door) + d(k.wither, wither) < d(near.entry, door) + d(near.wither, wither)) near = k;
        if (near == null || d(near.entry, door) > 3 || d(near.wither, wither) > 3) return null;
        return doors.get(near).get(n);
    }

    static Look look(Plan plan, int number, boolean late) {
        Integer role = playing(late);
        if (role != null) {
            if (role - 1 < plan.roles.size()) {
                int i = plan.roles.get(role - 1).indexOf(number);
                if (i >= 0) return new Mine(i + 1);
            }
            if (role - 1 < plan.stacks.size() && plan.stacks.get(role - 1).contains(number)) return new Stack();
        }
        for (int i = 0; i < plan.roles.size(); i++) if (plan.roles.get(i).contains(number)) return new Theirs(i + 1);
        for (List<Integer> s : plan.stacks) if (s.contains(number)) return new Theirs(0);
        return null;
    }
}
