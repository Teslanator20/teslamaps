// Ported from ADM's p3sim/SimParty.kt
package com.teslamaps.adm.sim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

public final class Players {
    private Players() {}

    public static class Profile {
        public final ConcurrentHashMap<String, JsonElement> vals = new ConcurrentHashMap<>();
        public volatile DungeonClass clazz = null;
        public volatile String asked = null;
        public final ConcurrentHashMap<String, String> files = new ConcurrentHashMap<>();
        public volatile Set<String> mine = null;
        public volatile String mineFor = null;
        public final ConcurrentHashMap<String, Spots.Spot> spots = new ConcurrentHashMap<>();
        public volatile List<DungeonClass> order = null;
        public volatile Map<String, DungeonClass> stacks = Collections.emptyMap();
    }

    private static final ConcurrentHashMap<UUID, Profile> profiles = new ConcurrentHashMap<>();

    public static Profile profile(ServerPlayer p) { return profiles.computeIfAbsent(p.getUUID(), k -> new Profile()); }

    public static void forgetFiles(ServerPlayer p) {
        HotbarLayout.forget(p.getUUID());
        Loadouts.forget(p.getUUID());
    }

    public static Profile joiner() {
        ServerPlayer a = Sim.acting();
        if (a == null || Sim.isHost(a)) return null;
        return profiles.get(a.getUUID());
    }

    public static DungeonClass classOf(ServerPlayer p) {
        if (Sim.isHost(p)) return P3Sim.ownClass();
        Profile prof = profiles.get(p.getUUID());
        DungeonClass c = prof == null ? null : prof.clazz;
        return c != null ? c : P3Sim.ownClass();
    }

    public static ServerPlayer playerOf(DungeonClass c) {
        if (c == null) return null;
        for (ServerPlayer it : Sim.players()) if (hasClass(it) && classOf(it) == c) return it;
        return null;
    }

    public static volatile Set<DungeonClass> joinerClasses = Collections.emptySet();

    public static Set<DungeonClass> playerClasses() {
        LinkedHashSet<DungeonClass> s = new LinkedHashSet<>(joinerClasses);
        s.add(P3Sim.ownClass());
        return s;
    }

    public static boolean isPlayerClass(DungeonClass c) { return c != null && playerClasses().contains(c); }

    public static Spots.Spot spotOf(ServerPlayer p, String key) {
        if (Sim.isHost(p)) return P3Plan.customSpot(key);
        DungeonClass c = classOf(p);
        Profile prof = profiles.get(p.getUUID());
        Spots.Spot s = prof == null ? null : prof.spots.get(P3Plan.skill + "/" + c.name() + "/" + key);
        return s != null ? s : P3Plan.defaultSpot(c, key);
    }

    public static Set<String> jobsOf(ServerPlayer p) {
        if (Sim.isHost(p)) return P3Plan.mine();
        if (!hasClass(p)) return Collections.emptySet();
        DungeonClass c = classOf(p);
        Profile prof = profiles.get(p.getUUID());
        String key = P3Plan.skill + "/" + c;
        if (prof != null) {
            Set<String> m = prof.mine;
            if (m != null && key.equals(prof.mineFor)) return m;
        }
        List<String> jobs = P3Plan.plan().jobsOf.get(c);
        return jobs == null ? new LinkedHashSet<>() : new LinkedHashSet<>(jobs);
    }

    public static boolean isPlayerJob(String job) {
        for (ServerPlayer it : Sim.players()) if (jobsOf(it).contains(job)) return true;
        return false;
    }

    public static ServerPlayer playerDoing(String job) {
        for (ServerPlayer it : Sim.players()) if (jobsOf(it).contains(job)) return it;
        return null;
    }

    public static void joined(ServerPlayer p) {
        Profile prof = profile(p);
        JsonElement v = prof.vals.get("class");
        DungeonClass want = v == null ? null : classByName(v.getAsString());
        if (prof.clazz == null || takenBy(p).contains(prof.clazz)) prof.clazz = free(p, want);
        refresh();
    }

    public static DungeonClass request(ServerPlayer p, DungeonClass want) {
        Profile prof = profile(p);
        Set<DungeonClass> taken = takenBy(p);
        DungeonClass got;
        if (want != null && !taken.contains(want)) got = want;
        else {
            DungeonClass had = prof.clazz;
            got = had != null && !taken.contains(had) ? had : free(p, want);
        }
        prof.clazz = got;
        refresh();
        return got;
    }

    public static void left(ServerPlayer p) {
        Profile prof = profiles.get(p.getUUID());
        if (prof != null) prof.clazz = null;
        refresh(p);
    }

    public static boolean hasClass(ServerPlayer p) {
        if (Sim.isHost(p)) return true;
        Profile prof = profiles.get(p.getUUID());
        return prof != null && prof.clazz != null;
    }

    private static Set<DungeonClass> takenBy(ServerPlayer p) {
        LinkedHashSet<DungeonClass> out = new LinkedHashSet<>();
        for (ServerPlayer it : Sim.players()) if (it != p && hasClass(it)) out.add(classOf(it));
        return out;
    }

    private static DungeonClass free(ServerPlayer p, DungeonClass want) {
        Set<DungeonClass> taken = takenBy(p);
        if (want != null && !taken.contains(want)) return want;
        for (DungeonClass c : Party.CLASSES()) if (!taken.contains(c)) return c;
        if (want != null) return want;
        return Party.CLASSES().get(0);
    }

    public static DungeonClass classByName(String n) {
        for (DungeonClass c : Party.CLASSES()) if (c.name().equals(n)) return c;
        return null;
    }

    public static void refresh() { refresh(null); }

    public static void refresh(ServerPlayer gone) {
        LinkedHashSet<DungeonClass> out = new LinkedHashSet<>();
        for (ServerPlayer it : Sim.players()) {
            if (Sim.isHost(it) || it == gone) continue;
            Profile prof = profiles.get(it.getUUID());
            if (prof != null && prof.clazz != null) out.add(prof.clazz);
        }
        joinerClasses = out;
    }

    private static JsonObject classJson(DungeonClass c) {
        JsonObject o = new JsonObject();
        o.addProperty("class", c.name());
        return o;
    }

    public static void changeClass(ServerPlayer p, DungeonClass want) {
        DungeonClass mine = classOf(p);
        if (want == mine) return;
        ServerPlayer holder = playerOf(want);
        if (holder != null && holder != p) {
            if (Sim.isHost(holder)) setHostClass(mine);
            else {
                profile(holder).clazz = mine;
                SimNet.send(holder, "set", classJson(mine).toString());
            }
            Sim.asPlayer(holder, () -> {
                Sim.note("§f" + p.getGameProfile().name() + "§7 took the §f" + Roles.label(want) + "§7: you're the §f" + Roles.label(mine) + "§7 now.");
            });
        }
        if (Sim.isHost(p)) setHostClass(want);
        else profile(p).clazz = want;
        if (!Sim.isHost(p)) SimNet.send(p, "set", classJson(want).toString());
        String other = holder != null && holder != p ? " (§f" + holder.getGameProfile().name() + "§7 is the " + Roles.label(mine) + " now)" : "";
        Sim.asPlayer(p, () -> {
            Sim.note("You're the §f" + Roles.label(want) + "§7" + other + ".");
        });
        refresh();
        Party.rosterChanged();
        PartyServer.rosterDirty = true;
    }

    private static DungeonClass hostClassSeen = null;

    private static void setHostClass(DungeonClass c) {
        hostClassSeen = c;
        P3Sim.classS.setIndex(Math.max(Party.CLASSES().indexOf(c), 0));
        Minecraft.getInstance().execute(com.teslamaps.config.TeslaMapsConfig::save);
    }

    public static void tick() {
        DungeonClass now = P3Sim.ownClass();
        DungeonClass was = hostClassSeen;
        hostClassSeen = now;
        if (was == null || was == now) return;
        ServerPlayer found = null;
        for (ServerPlayer it : Sim.players()) {
            if (Sim.isHost(it)) continue;
            Profile prof = profiles.get(it.getUUID());
            if (prof != null && prof.clazz == now) { found = it; break; }
        }
        ServerPlayer holder = found;
        if (holder == null) {
            Party.rosterChanged();
            PartyServer.rosterDirty = true;
            return;
        }
        profile(holder).clazz = was;
        SimNet.send(holder, "set", classJson(was).toString());
        Sim.asPlayer(holder, () -> {
            Sim.note("§f" + Sim.hostName() + "§7 took the §f" + Roles.label(now) + "§7: you're the §f" + Roles.label(was) + "§7 now.");
        });
        refresh();
        Party.rosterChanged();
        PartyServer.rosterDirty = true;
    }

    public static void clear() {
        profiles.clear();
        joinerClasses = Collections.emptySet();
        hostClassSeen = null;
    }
}
