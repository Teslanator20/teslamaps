// Ported from ADM's p3sim/SimParty.kt
package com.teslamaps.adm.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PartyServer {
    private PartyServer() {}

    public static volatile boolean rosterDirty = true;
    private static String lastShared = null;
    private static String lastPlan = null;
    private static final HashMap<UUID, String> sentRoster = new HashMap<>();
    private static boolean lastPerma = false;
    private static final ConcurrentHashMap<UUID, String> lastPersonal = new ConcurrentHashMap<>();

    private static String personalOf(ServerPlayer p) {
        return Sim.asPlayer(p, () -> {
            Object mask = Sim.isHost(p) ? (Object) P3Sim.wornMaskS.index() : Players.profile(p).vals.get("wornMask");
            return P3Sim.speed() + "/" + P3Sim.phoenix() + "/" + P3Sim.realMasks() + "/" + mask;
        });
    }

    public static void notePersonal(ServerPlayer p) {
        MinecraftServer s = SimServer.server;
        if (s != null && s.isSameThread()) lastPersonal.put(p.getUUID(), personalOf(p));
    }

    private static String lastOrder = "";

    public static void register() {
        SimNet.onServer("hello", PartyServer::hello);
        SimNet.onServer("profile", PartyServer::profile);
        SimNet.onServer("shared", PartyServer::shared);
        SimNet.onServer("plan", PartyServer::plan);
        SimNet.onServer("act", SimActions::run);
        SimNet.onServer("hit", SimItems::serverHit);
        SimNet.onServer("left", SimItems::serverLeftClick);
        SimNet.onServer("drop", (p, data) -> SimItems.serverDrop(p, data.equals("1")));
    }

    private static JsonObject parse(String data) {
        try {
            return JsonParser.parseString(data).getAsJsonObject();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void hello(ServerPlayer p, String data) {
        lastShared = null;
        lastPlan = null;
        sentRoster.remove(p.getUUID());
        rosterDirty = true;
        if (!Sim.isHost(p)) {
            JsonObject o = parse(data);
            if (o == null) o = new JsonObject();
            Players.Profile prof = Players.profile(p);
            boolean again = prof.clazz != null;
            JsonObject files = o.getAsJsonObject("files");
            if (files != null) for (Map.Entry<String, JsonElement> e : files.entrySet()) prof.files.put(e.getKey(), e.getValue().getAsString());
            Players.forgetFiles(p);
            JsonObject settings = o.getAsJsonObject("settings");
            if (settings != null) for (Map.Entry<String, JsonElement> e : settings.entrySet()) prof.vals.put(e.getKey(), e.getValue());
            JsonElement cv = prof.vals.get("class");
            String cn = cv == null ? null : cv.getAsString();
            DungeonClass want = cn == null ? null : Players.classByName(cn);
            prof.asked = want == null ? null : want.name();
            DungeonClass got = Players.request(p, want);
            if (want != got) {
                JsonObject s = new JsonObject();
                s.addProperty("class", got.name());
                SimNet.send(p, "set", s.toString());
            }
            Party.rosterChanged();
            if (!again) {
                Fight.setup(p);
                BossBar.showTo(p);
            }
            String name = p.getGameProfile().name();
            if (!again) Sim.everyone(() -> {
                Sim.note("§e" + name + "§7 joined the party as the §f" + Roles.label(got) + "§7.");
            });
            if (want != null && want != got && !again) {
                ServerPlayer holder = Players.playerOf(want);
                String who = holder != null ? holder.getGameProfile().name() : "Someone";
                Sim.note(who + " plays the " + Roles.label(want) + ", so you're the §f" + Roles.label(got) + "§7. Pick a class in the menu: taking someone's swaps you.");
            }
            if (Fight.phase != null && !again) Sim.note("The run is under way: §fRestart§7 (menu or keybind) to play it from the start together.");
        }
        sendState(p);
    }

    private static void profile(ServerPlayer p, String data) {
        JsonObject o = parse(data);
        if (o == null) return;
        if (Sim.isHost(p)) return;
        Players.Profile prof = Players.profile(p);
        JsonObject f = o.getAsJsonObject("files");
        if (f != null) {
            for (Map.Entry<String, JsonElement> e : f.entrySet()) prof.files.put(e.getKey(), e.getValue().getAsString());
            Players.forgetFiles(p);
        }
        JsonObject settings = o.getAsJsonObject("settings");
        if (settings != null) for (Map.Entry<String, JsonElement> e : settings.entrySet()) prof.vals.put(e.getKey(), e.getValue());
        DungeonClass want = null;
        JsonObject s2 = o.getAsJsonObject("settings");
        if (s2 != null) {
            JsonElement c = s2.get("class");
            if (c != null) {
                String n = c.getAsString();
                if (n != null) want = Players.classByName(n);
            }
        }
        if (prof.clazz == null) return;
        if (want != null && want != prof.clazz && !want.name().equals(prof.asked)) Players.changeClass(p, want);
        prof.asked = want == null ? null : want.name();
    }

    private static void shared(ServerPlayer p, String data) {
        if (Sim.isHost(p)) return;
        JsonObject o = parse(data);
        if (o == null) return;
        Minecraft.getInstance().execute(() -> {
            PartySettings.apply(PartySettings.SHARED(), o);
            com.teslamaps.config.TeslaMapsConfig.save();
        });
        String what = String.join(", ", o.keySet());
        if (!what.isEmpty()) Sim.everyone(() -> {
            Sim.note("§f" + p.getGameProfile().name() + "§7 changed: " + what);
        });
    }

    private static void plan(ServerPlayer p, String data) {
        JsonObject o = parse(data);
        if (o == null) return;
        if (Sim.isHost(p)) return;
        Players.Profile prof = Players.profile(p);
        JsonArray mine = o.getAsJsonArray("mine");
        if (mine != null) {
            LinkedHashSet<String> m = new LinkedHashSet<>();
            for (JsonElement it : mine) m.add(it.getAsString());
            prof.mine = m;
            JsonElement mf = o.get("mineFor");
            prof.mineFor = mf == null ? null : mf.getAsString();
        }
        JsonArray order = o.getAsJsonArray("order");
        if (order != null) {
            List<DungeonClass> l = new ArrayList<>();
            for (JsonElement it : order) {
                DungeonClass c = Players.classByName(it.getAsString());
                if (c != null) l.add(c);
            }
            prof.order = l;
        }
        JsonObject st = o.getAsJsonObject("stacks");
        if (st != null) {
            LinkedHashMap<String, DungeonClass> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : st.entrySet()) {
                DungeonClass c = Players.classByName(e.getValue().getAsString());
                if (c != null) m.put(e.getKey(), c);
            }
            prof.stacks = m;
        }
        JsonObject sp = o.getAsJsonObject("spots");
        if (sp != null) {
            prof.spots.clear();
            for (Map.Entry<String, JsonElement> e : sp.entrySet()) {
                String k = e.getKey();
                JsonArray a = e.getValue().getAsJsonArray();
                if (a.size() >= 5) prof.spots.put(k, new Spots.Spot(k, a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsFloat(), a.get(4).getAsFloat()));
            }
        }
        JsonObject sh = o.getAsJsonObject("shared");
        if (sh != null) Minecraft.getInstance().execute(() -> P3Plan.applyShared(sh, true));
    }

    private static void sendState(ServerPlayer p) {
        SimNet.send(p, "shared", PartySettings.snapshot(PartySettings.SHARED()).toString());
        SimNet.send(p, "planShared", P3Plan.sharedJson().toString());
        roster(p, true);
        SimNet.send(p, "phase", phaseName());
    }

    private static String phaseName() {
        Fight.Phase ph = Fight.phase;
        if (ph == null) return "";
        if (ph instanceof P1Maxor) return "P1";
        if (ph instanceof P2Storm) return "P2";
        if (ph instanceof P4Necron) return "P4";
        return "P3";
    }

    private static String lastPhase = "";

    public static void tick() {
        List<ServerPlayer> joiners = new ArrayList<>();
        for (ServerPlayer it : Sim.players()) if (!Sim.isHost(it)) joiners.add(it);
        Players.tick();
        Players.refresh();
        String shared = PartySettings.snapshot(PartySettings.SHARED()).toString();
        if (!shared.equals(lastShared)) {
            lastShared = shared;
            for (ServerPlayer it : joiners) SimNet.send(it, "shared", shared);
        }
        String plan = P3Plan.sharedJson().toString();
        if (!plan.equals(lastPlan)) {
            lastPlan = plan;
            for (ServerPlayer it : joiners) SimNet.send(it, "planShared", plan);
        }
        String ph = phaseName();
        if (!ph.equals(lastPhase)) {
            lastPhase = ph;
            SimNet.sendAll("phase", ph);
        }
        boolean perma = P3Sim.breakerPerma();
        if (lastPerma && !perma) Sim.forEachPlayer(p -> SimItems.unPerma());
        lastPerma = perma;
        for (ServerPlayer p : Sim.players()) Sim.asPlayer(p, () -> {
            String now = personalOf(p);
            String was = lastPersonal.put(p.getUUID(), now);
            if (was != null && !was.equals(now)) {
                String[] a = was.split("/", -1);
                String[] b = now.split("/", -1);
                if (!a[0].equals(b[0]) || !a[1].equals(b[1])) Fight.applySpeed(p);
                if ((!a[2].equals(b[2]) || !a[3].equals(b[3])) && !Masks.ghost.get()) Masks.equip(p);
            }
        });
        StringBuilder order = new StringBuilder();
        boolean first = true;
        for (ServerPlayer p : Sim.players()) {
            if (!first) order.append(", ");
            first = false;
            StringBuilder inner = new StringBuilder();
            boolean f2 = true;
            for (Party.Mate m : Party.mates(p)) {
                if (!f2) inner.append(", ");
                f2 = false;
                inner.append(m.name);
            }
            order.append(inner);
        }
        String o = order.toString();
        if (!o.equals(lastOrder)) {
            lastOrder = o;
            rosterDirty = true;
        }
        if (rosterDirty || Fight.serverTick % 20 == 0) {
            rosterDirty = false;
            for (ServerPlayer it : Sim.players()) roster(it);
        }
    }

    private static void roster(ServerPlayer p) { roster(p, false); }

    private static void roster(ServerPlayer p, boolean force) {
        DungeonClass me = Players.classOf(p);
        JsonArray a = new JsonArray();
        for (Party.Mate m : Party.mates(p)) {
            JsonObject it = new JsonObject();
            it.addProperty("n", m.name);
            it.addProperty("c", m.clazz.name());
            it.addProperty("p", m.player != null);
            a.add(it);
        }
        JsonObject o = new JsonObject();
        o.addProperty("me", me.name());
        o.addProperty("in", Players.hasClass(p));
        o.add("team", a);
        o.addProperty("party", Sim.players().size() > 1);
        String s = o.toString();
        if (!force && s.equals(sentRoster.get(p.getUUID()))) return;
        sentRoster.put(p.getUUID(), s);
        SimNet.send(p, "roster", s);
    }

    public static void left(ServerPlayer p) {
        sentRoster.remove(p.getUUID());
        lastPersonal.remove(p.getUUID());
        rosterDirty = true;
    }

    public static void reset() {
        lastShared = null;
        lastPlan = null;
        sentRoster.clear();
        lastPersonal.clear();
        rosterDirty = true;
        lastPhase = "";
    }
}
