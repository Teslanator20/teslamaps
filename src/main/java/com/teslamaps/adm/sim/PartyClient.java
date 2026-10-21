// Ported from ADM's p3sim/SimParty.kt
package com.teslamaps.adm.sim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

public final class PartyClient {
    private PartyClient() {}

    public static class Mate {
        public final String name;
        public final DungeonClass clazz;
        public final boolean player;

        public Mate(String name, DungeonClass clazz, boolean player) {
            this.name = name;
            this.clazz = clazz;
            this.player = player;
        }
    }

    public static volatile DungeonClass me = null;
    public static volatile List<Mate> team = Collections.emptyList();
    public static volatile boolean party = false;
    public static volatile String phase = "";

    private static boolean helloSent = false;
    private static String lastProfile = null;
    private static JsonObject lastShared = null;
    private static String lastPlan = null;
    private static JsonObject ownShared = null;
    private static boolean gotShared = false;
    private static int helloTicks = 0;

    public static boolean joiner() { return P3Sim.inSim() && !P3Sim.localSim(); }

    public static void register() {
        SimNet.onClient("shared", data -> {
            if (!joiner()) return;
            JsonObject o = JsonParser.parseString(data).getAsJsonObject();
            if (!gotShared) {
                gotShared = true;
                ownShared = PartySettings.snapshot(PartySettings.SHARED());
                keepOwn(ownShared);
            }
            PartySettings.apply(PartySettings.SHARED(), o);
            lastShared = PartySettings.snapshot(PartySettings.SHARED());
        });
        SimNet.onClient("planShared", data -> {
            if (joiner()) P3Plan.receiveShared(JsonParser.parseString(data).getAsJsonObject());
        });
        SimNet.onClient("set", data -> {
            PartySettings.apply(PartySettings.PERSONAL(), JsonParser.parseString(data).getAsJsonObject());
            lastProfile = null;
            com.teslamaps.config.TeslaMapsConfig.save();
        });
        SimNet.onClient("roster", data -> {
            JsonObject o = JsonParser.parseString(data).getAsJsonObject();
            JsonElement in = o.get("in");
            if (in == null || in.getAsBoolean()) {
                JsonElement m = o.get("me");
                me = Players.classByName(m == null ? null : m.getAsString());
            } else me = null;
            List<Mate> t = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("team")) {
                JsonObject m = e.getAsJsonObject();
                DungeonClass c = Players.classByName(m.get("c").getAsString());
                if (c != null) t.add(new Mate(m.get("n").getAsString(), c, m.get("p").getAsBoolean()));
            }
            team = t;
            JsonElement pa = o.get("party");
            party = pa != null && pa.getAsBoolean();
        });
        SimNet.onClient("phase", it -> phase = it);
        SimNet.onClient("fx", PartyClient::fx);
        SimNet.onClient("file", data -> {
            JsonObject o = JsonParser.parseString(data).getAsJsonObject();
            PlayerFiles.writeLocal(o.get("name").getAsString(), o.get("text").getAsString());
            HotbarLayout.forgetLocal();
            Loadouts.forgetLocal();
        });
        SimNet.onClient("say", Ec::msg);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            P3Party.simServer = false;
            Minecraft.getInstance().execute(PartyClient::left);
        });
    }

    private static boolean restoredOwn = false;

    private static void restoreOwn() {
        restoredOwn = true;
        Ec.safely("p3sim own settings restore", () -> {
            File f = ownFile();
            if (!f.exists()) return;
            try {
                PartySettings.apply(PartySettings.SHARED(), JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Throwable ignored) {
            }
            com.teslamaps.config.TeslaMapsConfig.save();
            f.delete();
        });
    }

    private static File ownFile() { return Ec.configDir().resolve("p3sim-own-settings.json").toFile(); }

    private static void keepOwn(JsonObject o) {
        Ec.safely("p3sim own settings keep", () -> {
            File f = ownFile();
            f.getParentFile().mkdirs();
            try {
                Files.writeString(f.toPath(), o.toString(), StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private static void fx(String what) {
        JsonObject o;
        try {
            o = JsonParser.parseString(what).getAsJsonObject();
        } catch (Throwable t) {
            return;
        }
        JsonElement ne = o.get("n");
        int n = ne != null ? ne.getAsInt() : 0;
        JsonElement fe = o.get("fx");
        String f = fe == null ? null : fe.getAsString();
        if (f == null) return;
        switch (f) {
            case "splitsStart" -> {
                JsonElement se = o.get("split");
                String split = se == null ? null : se.getAsString();
                if (split == null) return;
                Ec.safely("p3sim splits", () -> SimHooks.splitsStart(split, n));
                Ec.safely("p3sim arrows device", Fight::resetArrowsDevice);
            }
            case "splitsStop" -> {
                Ec.safely("p3sim splits stop", SimHooks::splitsStop);
                Ec.safely("p3sim arrows device", Fight::resetArrowsDevice);
                Ec.safely("p3sim tick timers stop", SimHooks::tickTimersStop);
            }
            case "masksReset" -> Ec.safely("p3sim masks", SimHooks::masksReset);
            case "arrowsReset" -> Ec.safely("p3sim arrows device", Fight::resetArrowsDevice);
            case "termStart" -> Ec.safely("p3sim term info", () -> SimHooks.termStart(n));
            case "termClear" -> Ec.safely("p3sim term info", SimHooks::termClearTime);
            case "sectionTime" -> Ec.safely("p3sim term info", () -> SimHooks.termSectionTime(n));
            default -> {}
        }
    }

    public static void send(String fx) { send(fx, 0, null, null); }

    public static void send(String fx, int n) { send(fx, n, null, null); }

    public static void send(String fx, int n, String split) { send(fx, n, split, null); }

    public static void send(String fx, int n, String split, ServerPlayer to) {
        JsonObject j = new JsonObject();
        j.addProperty("fx", fx);
        j.addProperty("n", n);
        if (split != null) j.addProperty("split", split);
        String o = j.toString();
        if (to != null) SimNet.send(to, "fx", o);
        else SimNet.sendAll("fx", o);
    }

    public static void tick() {
        if (!restoredOwn && !P3Sim.inSim()) restoreOwn();
        if (!P3Sim.inSim() || Minecraft.getInstance().player == null) {
            if (helloSent) left();
            return;
        }
        if (!helloSent) {
            helloSent = true;
            lastProfile = profileJson(true);
            SimNet.send("hello", lastProfile);
            if (joiner()) sendPlan(true);
            helloTicks = 0;
            return;
        }
        if (me == null && ++helloTicks % 100 == 0) {
            SimNet.send("hello", profileJson(true));
            if (joiner()) sendPlan(true);
        }
        String prof = profileJson(false);
        String was = lastProfile == null ? null : stripFiles(lastProfile);
        if (!prof.equals(was)) {
            lastProfile = prof;
            SimNet.send("profile", prof);
        }
        if (joiner() && gotShared) {
            JsonObject now = PartySettings.snapshot(PartySettings.SHARED());
            JsonObject d = PartySettings.diff(lastShared, now);
            if (d.size() > 0) {
                lastShared = now;
                SimNet.send("shared", d.toString());
            }
            sendPlan();
        }
    }

    private static String stripFiles(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            o.remove("files");
            return o.toString();
        } catch (Throwable t) {
            return json;
        }
    }

    private static String profileJson(boolean withFiles) {
        JsonObject o = new JsonObject();
        o.add("settings", PartySettings.snapshot(PartySettings.PERSONAL()));
        if (withFiles) {
            JsonObject f = new JsonObject();
            for (String n : PlayerFiles.NAMES) {
                String t = PlayerFiles.readLocal(n);
                if (t != null) f.addProperty(n, t);
            }
            o.add("files", f);
        }
        return o.toString();
    }

    private static void sendPlan() { sendPlan(false); }

    private static void sendPlan(boolean force) {
        String j = P3Plan.partyJson().toString();
        if (!force && j.equals(lastPlan)) return;
        lastPlan = j;
        SimNet.send("plan", j);
    }

    private static void left() {
        if (gotShared && ownShared != null) {
            PartySettings.apply(PartySettings.SHARED(), ownShared);
            Ec.safely("p3sim own settings back", com.teslamaps.config.TeslaMapsConfig::save);
            ownFile().delete();
        }
        if (helloSent && !P3Sim.localSim()) P3Plan.leaveParty();
        helloSent = false;
        gotShared = false;
        ownShared = null;
        lastShared = null;
        lastProfile = null;
        lastPlan = null;
        team = Collections.emptyList();
        me = null;
        party = false;
        phase = "";
    }
}
