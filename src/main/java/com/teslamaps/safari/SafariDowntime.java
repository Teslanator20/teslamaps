package com.teslamaps.safari;

// Ported from Sparkling Mutuals' SafariDowntime.kt + DowntimeAnnouncement.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code !dt [reason]} in party chat holds the Safari entry until that player says r / ready. */
public final class SafariDowntime {

    public static final String REQUEST_PREFIX = "DT request by ";
    public static final String TICKET_REMINDER = "[Reminder] Everyone get your ticket!";

    static final Pattern PARTY = Pattern.compile("^Party\\s*>\\s*(?:\\[[^\\]]+]\\s*)*([A-Za-z0-9_]{1,16}):\\s*(.*)$");
    private static final Pattern REQUEST = Pattern.compile("^!dt(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESUME = Pattern.compile(
            "^(?:(?:ok|okay|yes|yep|yeah) )?(?:(?:i'm|im|i am|we're|we are|all|everyone is|everyone's) )?(?:all |finally )?(?:r|rdy|ready)(?: now| to go| when you are| whenever you are| whenever| let's go| lets go)?$");
    private static final Pattern READY_TYPO = Pattern.compile("^(?:r+|[etdfg]r+|r+[etdfg])$");
    private static final Pattern TORRHUS_TRAVEL = Pattern.compile("^»\\s+(?:\\[[^]]+]\\s*)*([A-Za-z0-9_]{1,16}) is traveling to Torrhus Canyon FOLLOW$");
    private static final Pattern PARTY_WARP = Pattern.compile("^SkyBlock Party Warp \\([0-9]+ players?\\)$");
    private static final Pattern[] DEPARTED = {
            Pattern.compile("^(?:\\[[^]]+]\\s*)*([A-Za-z0-9_]{1,16}) (?:has left the party|has been removed from the party)[.!]$"),
            Pattern.compile("^(?:\\[[^]]+]\\s*)*([A-Za-z0-9_]{1,16}) was removed from your party because they disconnected[.!]?$"),
            Pattern.compile("^Kicked (?:\\[[^]]+]\\s*)*([A-Za-z0-9_]{1,16}) because they were offline[.!]?$")};
    private static final Pattern[] PARTY_GONE = {
            Pattern.compile("^You (?:left the party|have been kicked from the party(?: by .+)?)[.!]?$"),
            Pattern.compile("^You have joined .+'s party!$"),
            Pattern.compile("^(?:The party was disbanded|(?:\\[[^]]+]\\s*)*([A-Za-z0-9_]{1,16}) has disbanded the party)(?:[.!]| because .+)$"),
            Pattern.compile("^You are not (?:currently )?in a party[.!]?$")};
    private static final Pattern PUNCT = Pattern.compile("[.,!]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final LinkedHashMap<String, String> requests = new LinkedHashMap<>();
    private static final Map<String, Long> contestTravelAt = new HashMap<>();
    private static boolean active;
    private static boolean announcementDue;

    private static final Set<String> observed = new LinkedHashSet<>();
    private static String pending;
    private static long due;

    private SafariDowntime() {}

    private static boolean enabled() {
        return TeslaMapsConfig.get().safariDowntime;
    }

    public static boolean entryHold() {
        return !requests.isEmpty() && enabled();
    }

    private static boolean holding() {
        return active && enabled();
    }

    public static boolean readyMessage(String body) {
        if (body.indexOf('?') >= 0) return false;
        String low = StringUtil.strip(body).toLowerCase(Locale.ROOT).trim();
        int s = 0, e = low.length() - 1;
        while (s <= e && !Character.isLetterOrDigit(low.charAt(s))) s++;
        while (e >= s && !Character.isLetterOrDigit(low.charAt(e))) e--;
        String raw = low.substring(s, e + 1);
        if (raw.length() <= 6 && READY_TYPO.matcher(raw).matches()) return true;
        String norm = PUNCT.matcher(low.replace('’', '\'')).replaceAll(" ").trim();
        return RESUME.matcher(SPACES.matcher(norm).replaceAll(" ")).matches();
    }

    public static boolean ticketMessage(String body) {
        return !body.startsWith(REQUEST_PREFIX) && body.toLowerCase(Locale.ROOT).contains("ticket");
    }

    /** Party sender + body, or null. */
    static String[] party(String raw) {
        Matcher m = PARTY.matcher(StringUtil.stripTrim(raw));
        return m.matches() ? new String[]{m.group(1), m.group(2).trim()} : null;
    }

    static void onChat(String raw) {
        if (!enabled()) {
            if (active || !requests.isEmpty()) clear();
            return;
        }
        String line = StringUtil.stripTrim(raw);
        boolean server = StringUtil.isServerMessage(raw);
        if (server) {
            long now = System.currentTimeMillis();
            if (PARTY_WARP.matcher(line).matches()) {
                partyWarp(now);
                return;
            }
            Matcher t = TORRHUS_TRAVEL.matcher(line);
            if (t.matches()) {
                if (requests.containsValue("contest")) contestTravelAt.putIfAbsent(t.group(1).toLowerCase(Locale.ROOT), now);
                return;
            }
            if (partyChanged(line)) return;
        }
        String[] chat = party(raw);
        if (chat == null) return;
        String sender = chat[0], body = chat[1];
        observe(body);
        Matcher m = REQUEST.matcher(body);
        if (m.matches()) {
            String reason = m.group(1) == null ? "" : m.group(1).trim();
            boolean contest = reason.equalsIgnoreCase("contest");
            if (contest) {
                reason = "contest";
                contestTravelAt.clear();
            } else {
                contestTravelAt.remove(sender.toLowerCase(Locale.ROOT));
            }
            if (requests.isEmpty()) clearAnnouncement();
            if (contest && sender.equalsIgnoreCase(PartyTracker.leader())) {
                for (String member : new ArrayList<>(PartyTracker.members())) {
                    if (key(member) == null) requests.put(member, "contest");
                }
            }
            String k = key(sender);
            requests.put(k != null ? k : sender, reason);
            if (!SafariEntryGuard.activeRun()) {
                String msg = active ? summary() : leftSafari();
                if (msg != null) queue(msg);
            }
        } else if (ticketMessage(body) || readyMessage(body)) {
            ready(sender);
        }
    }

    private static String key(String name) {
        for (String k : requests.keySet()) if (k.equalsIgnoreCase(name)) return k;
        return null;
    }

    public static boolean ready(String player) {
        String k = key(player);
        if (k == null) return false;
        requests.remove(k);
        if (requests.isEmpty()) active = false;
        return true;
    }

    /** Our own contest ticket clears our own "!dt contest" hold. */
    static void ticketEarned(String player) {
        String k = key(player);
        if (k == null || !"contest".equalsIgnoreCase(requests.get(k))) return;
        requests.remove(k);
        if (requests.isEmpty()) active = false;
    }

    private static void partyWarp(long now) {
        if (!SafariEntryGuard.inCanyon() || !PartyTracker.isLeader()) return;
        String self = SafariEntryGuard.selfName();
        for (Map.Entry<String, String> e : requests.entrySet()) {
            if (e.getValue().equalsIgnoreCase("contest") && !e.getKey().equalsIgnoreCase(self)) {
                contestTravelAt.putIfAbsent(e.getKey().toLowerCase(Locale.ROOT), now);
            }
        }
    }

    private static void releaseContestTravel(long now) {
        List<String> release = new ArrayList<>();
        for (Map.Entry<String, String> e : requests.entrySet()) {
            if (!e.getValue().equalsIgnoreCase("contest")) continue;
            Long at = contestTravelAt.get(e.getKey().toLowerCase(Locale.ROOT));
            if (at != null && now - at >= 20_000L) release.add(e.getKey());
        }
        for (String k : release) {
            requests.remove(k);
            contestTravelAt.remove(k.toLowerCase(Locale.ROOT));
        }
        if (requests.isEmpty()) active = false;
        if (!requests.containsValue("contest")) contestTravelAt.clear();
    }

    private static boolean partyChanged(String line) {
        for (Pattern p : PARTY_GONE) {
            if (p.matcher(line).matches()) {
                clear();
                return true;
            }
        }
        for (Pattern p : DEPARTED) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                ready(m.group(1));
                return true;
            }
        }
        return false;
    }

    private static String leftSafari() {
        if (requests.isEmpty()) return null;
        if (!active) {
            active = true;
            announcementDue = true;
        }
        if (!announcementDue) return null;
        announcementDue = false;
        return summary();
    }

    static void tick() {
        if (!enabled()) {
            if (active || !requests.isEmpty()) clear();
            return;
        }
        long now = System.currentTimeMillis();
        releaseContestTravel(now);
        if (com.teslamaps.features.ContestTimers.ticketEarned()) ticketEarned(SafariEntryGuard.selfName());
        if (!PartyTracker.inParty()) return;
        if (!SafariEntryGuard.activeRun()) {
            String msg = leftSafari();
            if (msg != null) queue(msg);
        }
        String out = poll(now, holding() ? summary() : null, PartyTracker.isLeader(), SafariEntryGuard.inCanyon());
        if (out != null && TeslaMapsConfig.get().safariDowntimeAnnounce) Announce.party(out);
    }

    private static String summary() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : requests.entrySet()) {
            if (e.getValue().equalsIgnoreCase("contest")) continue;
            if (sb.length() > 0) sb.append(" · ");
            String reason = e.getValue().isBlank() ? "Not specified" : e.getValue();
            sb.append(REQUEST_PREFIX).append(e.getKey()).append(" (Reason: ").append(take(reason, 60)).append(")");
        }
        if (sb.length() > 0) return sb.toString();
        return requests.isEmpty() ? null : TICKET_REMINDER;
    }

    public static String waitingFor() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : requests.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            String reason = e.getValue().isBlank() ? "downtime" : e.getValue();
            sb.append(e.getKey()).append(" (").append(take(reason, 60)).append(")");
        }
        return sb.toString();
    }

    private static String take(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static void observe(String body) {
        if (body.equals(TICKET_REMINDER) || body.startsWith(REQUEST_PREFIX)) observed.add(body);
    }

    private static void queue(String message) {
        pending = message;
        due = System.currentTimeMillis() + 500L;
    }

    private static String poll(long now, String current, boolean leader, boolean inCanyon) {
        if (pending == null) return null;
        String message = pending;
        if (current == null) {
            pending = null;
            return null;
        }
        if (!message.equals(current)) {
            pending = current;
            due = now + 500L;
            return null;
        }
        if (!leader || !inCanyon || now < due) return null;
        pending = null;
        return observed.add(message) ? message : null;
    }

    private static void clearAnnouncement() {
        pending = null;
        observed.clear();
    }

    public static void clear() {
        active = false;
        announcementDue = false;
        requests.clear();
        contestTravelAt.clear();
        clearAnnouncement();
    }
}
