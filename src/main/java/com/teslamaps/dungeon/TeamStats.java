package com.teslamaps.dungeon;

// Secrets from Odin's SecretsCounter.kt, rooms/deaths after FunnyMapExtras' PlayerTracker.kt
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.scanner.ComponentGrid;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TeamStats {

    private static final Pattern END = Pattern.compile("^(?:Master Mode )?(?:The )?Catacombs - (?:Entrance|Floor .{1,3})$");
    private static final Pattern DEATH = Pattern.compile("^☠ (\\w+) ");

    private static final class P {
        final String name;
        String cls = "?";
        volatile long baseline = -1, end = -1;
        int rooms, solo, deaths;
        DungeonRoom lastRoom;
        long lastSeen;

        P(String name) { this.name = name; }
    }

    private static final Map<String, P> players = new LinkedHashMap<>();
    private static final Map<DungeonRoom, CheckmarkState> states = new IdentityHashMap<>();
    private static Object lastLevel;
    private static boolean started, printed;
    private static int tickN;

    private TeamStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("TeamStats").section("Score & Splits").when(() -> cfg().teamStats && !com.teslamaps.adm.Adm.overrides(cfg().admClearBreakdown))
                .tick(TeamStats::tick)
                .serverChat(TeamStats::onChat)
                .config("Score & Splits", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Team Stats At Run End", () -> c.teamStats, v -> c.teamStats = v)
                            .desc("Chat summary per teammate after the run: secrets, cleared rooms (solo/stacked), deaths. /tmap teamstats reprints it.");
                    b.toggle("  Secrets Per Player (API)", () -> c.teamStatsSecrets, v -> c.teamStatsSecrets = v)
                            .desc("Reads every teammate's secret total at run start and end from the API.");
                    b.toggle("  Rooms Cleared Per Player", () -> c.teamStatsRooms, v -> c.teamStatsRooms = v)
                            .desc("Counts a room for everyone standing in it when it gets its checkmark.");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("teamstats").executes(c -> {
                    if (players.isEmpty()) {
                        c.getSource().sendFeedback(Component.literal("§c[TeslaMaps] No team stats for this run yet."));
                        return 0;
                    }
                    print();
                    return 1;
                }))));
    }

    private static void reset() {
        players.clear();
        states.clear();
        started = false;
        printed = false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (!DungeonManager.isInDungeon()) return;
        tickN++;
        if (tickN % 10 == 0) {
            for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
                P p = players.computeIfAbsent(dp.getName(), P::new);
                String cls = dp.getDungeonClass();
                if (cls != null && !cls.equals("EMPTY") && !cls.equals("DEAD")) p.cls = cls;
            }
        }
        if (!cfg().teamStatsRooms || players.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Player pl : mc.level.players()) {
            P p = players.get(pl.getGameProfile().name());
            if (p == null) continue;
            int[] g = ComponentGrid.worldToGrid(pl.getX(), pl.getZ());
            if (g == null || DungeonManager.getGrid() == null) continue;
            DungeonRoom room = DungeonManager.getRoomAt(g[0], g[1]);
            if (room == null) continue;
            p.lastRoom = room;
            p.lastSeen = now;
        }
        if (tickN % 5 != 0 || DungeonManager.getGrid() == null) return;
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            CheckmarkState state = room.getCheckmarkState();
            CheckmarkState prev = states.put(room, state);
            if (prev == null || cleared(prev) || !cleared(state)) continue;
            RoomType t = room.getType();
            if (t == RoomType.ENTRANCE || t == RoomType.BLOOD || t == RoomType.FAIRY || t == RoomType.UNKNOWN) continue;
            List<P> in = new ArrayList<>();
            for (P p : players.values()) if (p.lastRoom == room && now - p.lastSeen < 5000) in.add(p);
            for (P p : in) {
                p.rooms++;
                if (in.size() == 1) p.solo++;
            }
        }
    }

    private static boolean cleared(CheckmarkState s) {
        return s == CheckmarkState.WHITE || s == CheckmarkState.GREEN;
    }

    private static void onChat(String raw) {
        if (!DungeonManager.isInDungeon()) return;
        String s = StringUtil.stripTrim(raw);
        if (!started && s.equals("[NPC] Mort: Good luck.")) {
            started = true;
            if (cfg().teamStatsSecrets) {
                for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
                    P p = players.computeIfAbsent(dp.getName(), P::new);
                    fetch(p, true);
                }
            }
            return;
        }
        Matcher d = DEATH.matcher(s);
        if (d.find()) {
            String name = d.group(1);
            Minecraft mc = Minecraft.getInstance();
            if (name.equals("You") && mc.player != null) name = mc.player.getGameProfile().name();
            P p = players.get(name);
            if (p != null) p.deaths++;
            return;
        }
        if (!printed && END.matcher(s).matches() && !players.isEmpty()) {
            printed = true;
            if (!cfg().teamStatsSecrets) {
                print();
                return;
            }
            List<CompletableFuture<?>> all = new ArrayList<>();
            for (P p : players.values()) if (p.baseline >= 0) all.add(fetch(p, false));
            CompletableFuture.allOf(all.toArray(new CompletableFuture[0]))
                    .orTimeout(20, TimeUnit.SECONDS)
                    .whenComplete((v, e) -> Minecraft.getInstance().execute(TeamStats::print));
        }
    }

    private static CompletableFuture<Void> fetch(P p, boolean start) {
        return HypixelApi.nameToUuid(p.name).thenCompose(uuid -> {
            if (uuid == null) return CompletableFuture.completedFuture((JsonObject) null);
            HypixelApi.invalidateProfiles(uuid);
            return HypixelApi.getProfiles(uuid);
        }).thenAccept(json -> {
            long v = secrets(json);
            if (start) p.baseline = v;
            else p.end = v;
        }).exceptionally(e -> null);
    }

    private static long secrets(JsonObject json) {
        if (json == null || !json.has("profiles") || !json.get("profiles").isJsonObject()) return -1;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("profiles").entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject prof = e.getValue().getAsJsonObject();
            if (!prof.has("current") || !prof.get("current").getAsBoolean()) continue;
            try {
                return prof.getAsJsonObject("data").getAsJsonObject("dungeons").get("secrets").getAsLong();
            } catch (Exception ex) {
                return -1;
            }
        }
        return -1;
    }

    private static void print() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || players.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] Team Stats"));
        for (P p : players.values()) {
            StringBuilder sb = new StringBuilder(" ").append(classColor(p.cls)).append(p.name)
                    .append(" §7(").append(p.cls).append(")");
            if (c.teamStatsSecrets) {
                String sec = p.baseline >= 0 && p.end >= 0 ? String.valueOf(Math.max(0, p.end - p.baseline)) : "N/A";
                sb.append(" §8| §f").append(sec).append(" §7secrets");
            }
            if (c.teamStatsRooms) {
                sb.append(" §8| §f").append(p.rooms).append(" §7rooms §8(").append(p.solo).append(" solo)");
            }
            sb.append(" §8| ").append(p.deaths > 0 ? "§c" : "§f").append(p.deaths).append(p.deaths == 1 ? " §7death" : " §7deaths");
            mc.player.sendSystemMessage(Component.literal(sb.toString()));
        }
    }

    private static String classColor(String cls) {
        return switch (cls) {
            case "Archer" -> "§6";
            case "Berserk" -> "§4";
            case "Healer" -> "§d";
            case "Mage" -> "§b";
            case "Tank" -> "§2";
            default -> "§f";
        };
    }
}
