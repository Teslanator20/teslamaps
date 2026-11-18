package com.teslamaps.safari;

// Ported from Sparkling Mutuals' ContestTracker.kt (party part) + ContestDowntimeState.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.features.ContestTimers;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.SkyblockUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Leader posts "New Contest Started!" and holds the Safari entry until everyone confirmed their ticket. */
public final class SafariContestGuard {

    private static final String REMINDER_TAIL = ", still waiting on ticket earned confirmation message";

    private static final Set<String> waiting = new LinkedHashSet<>();
    private static long contestStartedAt;
    private static long reminderAt;
    private static boolean reminded;
    private static long bypassedWindow = Long.MIN_VALUE;
    private static boolean returnedFromSafari;
    private static long voluntaryReturnAt;
    private static long windowKey = Long.MIN_VALUE;
    private static Long previous;
    private static int ticks;

    private SafariContestGuard() {}

    private static boolean enabled() {
        return TeslaMapsConfig.get().safariContestGuard;
    }

    private static boolean betweenContests(long now) {
        return ContestTimers.foragingSecondsIn(now) >= 1170;
    }

    private static List<String> others() {
        List<String> out = new ArrayList<>();
        String self = SafariEntryGuard.selfName();
        for (String m : PartyTracker.members()) if (!m.equalsIgnoreCase(self)) out.add(m);
        return out;
    }

    private static boolean hasInfo() {
        return PartyTracker.inParty() && PartyTracker.leader() != null;
    }

    public static void bypass() {
        if (waiting().isEmpty()) return;
        bypassedWindow = windowKey;
        clear();
    }

    public static void clear() {
        waiting.clear();
        reminderAt = 0;
    }

    public static List<String> waiting() {
        if (!enabled() || hasInfo() && !PartyTracker.isLeader()) return List.of();
        long now = System.currentTimeMillis();
        if (betweenContests(now) || windowKey != ContestTimers.foragingWindow(now)) return List.of();
        if (!hasInfo()) return new ArrayList<>(waiting);
        List<String> others = others();
        List<String> out = new ArrayList<>();
        for (String w : waiting) {
            for (String o : others) if (o.equalsIgnoreCase(w)) out.add(o);
        }
        return out;
    }

    static void onCommand(String command) {
        if (SafariEntryGuard.stage() != SafariEntryGuard.Stage.SAFARI) return;
        String c = command.trim().toLowerCase(Locale.ROOT);
        if (c.equals("warp torrhus") || c.equals("warp canyon") || c.equals("warp torrhus_canyon")) {
            voluntaryReturnAt = System.currentTimeMillis();
        }
    }

    static void onChat(String raw) {
        if (!enabled() || waiting.isEmpty()) return;
        String[] chat = SafariDowntime.party(raw);
        if (chat == null) return;
        String body = chat[1];
        if (body.toLowerCase(Locale.ROOT).contains(REMINDER_TAIL)) return;
        if (!confirmsTicket(body)) return;
        if (System.currentTimeMillis() < contestStartedAt) return;
        waiting.removeIf(w -> w.equalsIgnoreCase(chat[0]));
    }

    private static boolean confirmsTicket(String body) {
        return SafariDowntime.readyMessage(body)
                || !body.startsWith("!") && !body.regionMatches(true, 0, "[Reminder]", 0, 10)
                && SafariDowntime.ticketMessage(body);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (SafariEntryGuard.activeRun()) {
            returnedFromSafari = true;
            reminderAt = 0;
        }
        if (++ticks < 10) return;
        ticks = 0;

        long window = ContestTimers.foragingWindow(now);
        int secondsIn = ContestTimers.foragingSecondsIn(now);
        if (window != windowKey) {
            windowKey = window;
            clear();
        }
        boolean leader = PartyTracker.isLeader();
        List<String> others = others();
        boolean skyblock = SkyblockUtils.isInSkyblock();
        if (hasInfo() && !leader || !enabled() || betweenContests(now)) clear();
        if (hasInfo()) waiting.removeIf(w -> others.stream().noneMatch(o -> o.equalsIgnoreCase(w)));

        Long before = previous;
        previous = window;
        if (before != null && before != window && secondsIn < 10 && enabled() && skyblock && !others.isEmpty() && leader) {
            waiting.addAll(others);
            contestStartedAt = now;
            reminded = false;
            Announce.party("New Contest Started!");
        }

        boolean canyon = skyblock && SafariEntryGuard.inTorrhus();
        if (canyon && returnedFromSafari) {
            boolean voluntary = now - voluntaryReturnAt >= 0 && now - voluntaryReturnAt < 15_001L;
            if (voluntary || othersListed(mc, others)) {
                returnedFromSafari = false;
                voluntaryReturnAt = 0;
            }
        }
        if (!canyon) reminderAt = 0;

        if (leader && canyon && !returnedFromSafari && !waiting.isEmpty() && !reminded && bypassedWindow != windowKey) {
            if (reminderAt == 0) reminderAt = now + 30_000L;
            if (now >= reminderAt) remind();
        }
    }

    private static boolean othersListed(Minecraft mc, List<String> others) {
        if (mc.getConnection() == null) return false;
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            String name = info.getProfile().name();
            for (String o : others) if (o.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static void remind() {
        List<String> names = new ArrayList<>(waiting);
        if (names.size() > 3) names = names.subList(0, 3);
        reminded = true;
        Announce.party(String.join(", ", names)
                + REMINDER_TAIL + " (Send message containing \"r\" or \"ticket\").");
    }
}
