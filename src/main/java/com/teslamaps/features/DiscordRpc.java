package com.teslamaps.features;

// Pure-Java IPC ported from FloydAddons' DiscordIpcClient.kt / FloydDiscordPresence.kt,
// content after Skyblocker's DiscordRPCManager
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonFloor;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;

import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class DiscordRpc {

    private static final Gson GSON = new Gson();

    private static volatile boolean wanted;
    private static volatile String details = "";
    private static volatile String state = "";
    private static volatile long startSec;
    private static Thread thread;
    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DiscordRpc").tickAlways(DiscordRpc::tick)
                .config("Interface", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Discord");
                    b.toggle("Discord Rich Presence", () -> c.discordRpc, v -> c.discordRpc = v)
                            .desc("Shows SkyBlock, your island and dungeon floor in your Discord status while on SkyBlock.");
                    b.toggle("  Show Location", () -> c.discordRpcLocation, v -> c.discordRpcLocation = v);
                    b.toggle("  Show Dungeon Floor", () -> c.discordRpcDungeon, v -> c.discordRpcDungeon = v);
                });
    }

    private static void tick() {
        if (++ticks % 40 != 0) return;
        boolean on = cfg().discordRpc && SkyblockUtils.isInSkyblock();
        if (on && !wanted) startSec = System.currentTimeMillis() / 1000;
        wanted = on;
        if (on) {
            build();
            if (thread == null || !thread.isAlive()) {
                thread = new Thread(DiscordRpc::loop, "TeslaMaps-DiscordIPC");
                thread.setDaemon(true);
                thread.start();
            }
        }
    }

    private static void build() {
        TeslaMapsConfig c = cfg();
        String d = "Hypixel SkyBlock";
        DungeonFloor floor = DungeonManager.isInDungeon() ? DungeonManager.getCurrentFloor() : null;
        if (c.discordRpcDungeon && floor != null) {
            d = "Catacombs " + floor.name() + (DungeonManager.isInBossArea() ? " (Boss)" : "");
        }
        String s = "";
        if (c.discordRpcLocation && floor == null) {
            String area = SkyblockUtils.getArea();
            String sub = "";
            for (String line : ScoreboardUtils.getScoreboardLines()) {
                String t = StringUtil.stripTrim(line);
                if (t.startsWith("⏣") || t.startsWith("ф")) { sub = t.substring(1).trim(); break; }
            }
            s = area.isEmpty() ? sub : sub.isEmpty() || sub.equalsIgnoreCase(area) ? area : area + " - " + sub;
        }
        details = d;
        state = s;
    }

    private static void loop() {
        Conduit conn = null;
        String sent = null;
        int failures = 0;
        try {
            while (true) {
                if (!wanted) {
                    if (conn != null) { conn.close(); conn = null; sent = null; }
                    if (!cfg().discordRpc) return;
                    Thread.sleep(2000);
                    continue;
                }
                if (conn == null) {
                    if (cfg().discordRpcAppId == null || cfg().discordRpcAppId.isBlank()) { Thread.sleep(5000); continue; }
                    conn = connect(cfg().discordRpcAppId);
                    if (conn == null) {
                        failures++;
                        Thread.sleep(Math.min(60_000, 5000L * failures));
                        continue;
                    }
                    failures = 0;
                    sent = null;
                }
                String key = details + "|" + state + "|" + startSec;
                if (!key.equals(sent)) {
                    try {
                        setActivity(conn);
                        sent = key;
                    } catch (Exception e) {
                        conn.close();
                        conn = null;
                        continue;
                    }
                }
                Thread.sleep(2000);
            }
        } catch (InterruptedException ignored) {
        } finally {
            if (conn != null) conn.close();
        }
    }

    private static Conduit connect(String appId) {
        Conduit c = open();
        if (c == null) return null;
        try {
            JsonObject hs = new JsonObject();
            hs.addProperty("v", 1);
            hs.addProperty("client_id", appId);
            send(c, 0, hs);
            read(c);
            TeslaMaps.LOGGER.info("[DiscordRpc] connected");
            return c;
        } catch (Exception e) {
            c.close();
            return null;
        }
    }

    private static void setActivity(Conduit c) throws Exception {
        JsonObject activity = new JsonObject();
        activity.addProperty("details", details);
        if (!state.isEmpty()) activity.addProperty("state", state);
        JsonObject ts = new JsonObject();
        ts.addProperty("start", startSec);
        activity.add("timestamps", ts);
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);
        JsonObject frame = new JsonObject();
        frame.addProperty("cmd", "SET_ACTIVITY");
        frame.addProperty("nonce", UUID.randomUUID().toString());
        frame.add("args", args);
        send(c, 1, frame);
        read(c);
    }

    private static void send(Conduit c, int op, JsonObject payload) throws Exception {
        byte[] json = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op).putInt(json.length).put(json).flip();
        c.write(buf);
    }

    private static void read(Conduit c) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        if (!c.read(header)) throw new java.io.EOFException("ipc closed");
        header.flip();
        int op = header.getInt();
        int len = header.getInt();
        if (len < 0 || len > (1 << 20)) throw new java.io.IOException("bad frame " + len);
        if (len > 0 && !c.read(ByteBuffer.allocate(len))) throw new java.io.EOFException("ipc closed");
        if (op == 2) throw new java.io.IOException("ipc close frame");
    }

    private interface Conduit {
        void write(ByteBuffer buf) throws Exception;
        boolean read(ByteBuffer buf) throws Exception;
        void close();
    }

    private static Conduit open() {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            for (int i = 0; i < 10; i++) {
                try {
                    RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    return new Conduit() {
                        public void write(ByteBuffer buf) throws Exception {
                            byte[] b = new byte[buf.remaining()];
                            buf.get(b);
                            pipe.write(b);
                        }
                        public boolean read(ByteBuffer buf) throws Exception {
                            byte[] b = new byte[buf.remaining()];
                            try { pipe.readFully(b); } catch (java.io.EOFException e) { return false; }
                            buf.put(b);
                            return true;
                        }
                        public void close() { try { pipe.close(); } catch (Exception ignored) {} }
                    };
                } catch (Exception ignored) {
                }
            }
            return null;
        }
        String[] bases = {System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), "/tmp"};
        String[] subs = {"", "app/com.discordapp.Discord/", "snap.discord/"};
        for (String base : bases) {
            if (base == null) continue;
            for (String sub : subs) for (int i = 0; i < 10; i++) {
                Path p = Path.of(base, sub + "discord-ipc-" + i);
                if (!Files.exists(p)) continue;
                try {
                    SocketChannel ch = SocketChannel.open(UnixDomainSocketAddress.of(p));
                    return new Conduit() {
                        public void write(ByteBuffer buf) throws Exception { while (buf.hasRemaining()) ch.write(buf); }
                        public boolean read(ByteBuffer buf) throws Exception {
                            while (buf.hasRemaining()) if (ch.read(buf) < 0) return false;
                            return true;
                        }
                        public void close() { try { ch.close(); } catch (Exception ignored) {} }
                    };
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
