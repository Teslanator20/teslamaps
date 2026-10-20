package com.teslamaps.adm.adr;

import com.teslamaps.TeslaMaps;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// Ported from ADM's PartyTracker.kt
/**
 * The party, live: {@link PartyState} reading Hypixel's chat, plus the dungeon's teammates. A /p list
 * goes out only when the state has a doubt, at most one a minute, its answer kept out of chat. One
 * without an answer pauses asking for 30 min, a second stops it until the game restarts.
 */
public final class PartyTracker {
    private PartyTracker() {}

    public static final PartyState state = new PartyState(() -> Minecraft.getInstance().getUser().getName());

    private static final long JOIN_WAIT_MS = 4_000L;
    private static final long DOUBT_WAIT_MS = 2_500L;
    private static final long ASK_GAP_MS = 60_000L;
    private static final long HIDE_MS = 4_000L;
    private static final long FAIL_PAUSE_MS = 30 * 60_000L;

    private static int failures = 0;
    private static long answerBy = 0L;
    private static long pausedUntil = 0L;

    private static long joinedAt = 0L;
    private static boolean onHypixel = false;
    private static boolean connected = false;
    private static long doubtSince = 0L;
    private static long lastAsk = 0L;
    private static volatile long hideUntil = 0L;
    private static List<String> lastTeam = List.of();

    private static final Pattern NAME = Pattern.compile("^\\w{1,16}$");

    public static void onJoin() {
        synchronized (state) {
            Minecraft mc = Minecraft.getInstance();
            onHypixel = mc.getCurrentServer() != null && mc.getCurrentServer().ip.toLowerCase().contains("hypixel");
            if (connected) return;
            connected = true;
            joinedAt = System.currentTimeMillis();
            lastAsk = 0L;
            lastTeam = List.of();
            state.reset(onHypixel ? "just joined" : "not on Hypixel");
        }
    }

    public static void onDisconnect() {
        synchronized (state) {
            onHypixel = false;
            connected = false;
            answerBy = 0L;
            state.reset("not connected");
        }
    }

    /** A system chat message (plain and coloured). True when it is the answer to our /p list, to hide. */
    public static boolean onChat(String plain, String colored) {
        synchronized (state) {
            long now = System.currentTimeMillis();
            String[] lines = plain.split("\n", -1);
            String[] colors = colored.split("\n", -1);
            if (colors.length != lines.length) colors = null;
            boolean hide = now < hideUntil;
            for (int i = 0; i < lines.length; i++) {
                String l = lines[i];
                String lt = l.trim();
                boolean listLine = state.line(l, colors != null ? colors[i] : null, now);
                if (answerBy != 0L && (listLine || PartyState.NOT_IN_PARTY.matcher(lt).matches())) { answerBy = 0L; failures = 0; }
                if (hide && !listLine && !PartyState.SEPARATOR.matcher(lt).matches() && !l.isBlank() && !PartyState.NOT_IN_PARTY.matcher(lt).matches()) hide = false;
            }
            if (hide && state.doubt() == null) hideUntil = now + 300;
            return hide;
        }
    }

    public static void tick() {
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (answerBy != 0L && now > answerBy) {
                answerBy = 0L;
                failures++;
                pausedUntil = now + FAIL_PAUSE_MS;
                TeslaMaps.LOGGER.info("[adr] /p list got no answer, " + (failures >= 2 ? "not asking again until the game restarts" : "not asking for " + FAIL_PAUSE_MS / 60_000 + " min"));
            }
            if (DungeonManager.isInDungeon()) {
                List<String> team = new ArrayList<>();
                for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers())
                    if (NAME.matcher(p.getName()).matches()) team.add(p.getName());
                if (!team.isEmpty() && !team.equals(lastTeam)) { lastTeam = team; state.teammates(team); }
            } else lastTeam = List.of();

            String doubt = state.doubt();
            if (doubt == null) { doubtSince = 0L; return; }
            if (doubtSince == 0L) doubtSince = now;
            if (!onHypixel || Minecraft.getInstance().player == null) return;
            if (now - joinedAt < JOIN_WAIT_MS || now - doubtSince < DOUBT_WAIT_MS || now - lastAsk < ASK_GAP_MS) return;
            ask(now, doubt);
        }
    }

    /** For a screen that needs the party now: asks when unknown, at most every 10 s. */
    public static void askIfUnknown() {
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (state.known() || !onHypixel || Minecraft.getInstance().player == null || now - lastAsk < 10_000L) return;
            ask(now, "asked for");
        }
    }

    private static void ask(long now, String why) {
        if (answerBy != 0L || failures >= 2 || now < pausedUntil) return;
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        lastAsk = now;
        hideUntil = now + HIDE_MS;
        answerBy = now + HIDE_MS;
        TeslaMaps.LOGGER.info("[adr] /p list ({})", why);
        conn.sendCommand("p list");
    }
}
