package com.teslamaps.utils;

import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from Odin's PartyUtils.kt
public final class PartyTracker {

    private static final String R = "(?:\\[[^]]*?] ?)?";
    private static final String N = "(\\w{1,16})";
    private static final Pattern JOINED_SELF = Pattern.compile("^You have joined " + R + N + "'s? party!$");
    private static final Pattern JOINED_OTHER = Pattern.compile("^" + R + N + " joined the party\\.$");
    private static final Pattern LEFT = Pattern.compile("^" + R + N + " has left the party\\.$");
    private static final Pattern KICKED = Pattern.compile("^" + R + N + " has been removed from the party\\.$");
    private static final Pattern KICKED_OFFLINE = Pattern.compile("^Kicked " + R + N + " because they were offline\\.$");
    private static final Pattern KICKED_DC = Pattern.compile("^" + R + N + " was removed from your party because they disconnected\\.$");
    private static final Pattern TRANSFER_LEAVE = Pattern.compile("^The party was transferred to " + R + N + " because " + R + N + " left$");
    private static final Pattern TRANSFER_BY = Pattern.compile("^The party was transferred to " + R + N + " by " + R + N + "$");
    private static final Pattern PARTY_CHAT = Pattern.compile("^Party > " + R + N + "(?: [^:]{1,3})?: ");
    private static final Pattern INVITE = Pattern.compile("^" + R + N + " invited " + R + N + " to the party! They have 60 seconds to accept\\.$");
    private static final Pattern LEADER_DC = Pattern.compile("^The party leader, " + R + N + " has disconnected, they have 5 minutes to rejoin before the party is disbanded\\.$");
    private static final Pattern LEADER_BACK = Pattern.compile("^The party leader " + R + N + " has rejoined\\.$");
    private static final Pattern MEMBER = Pattern.compile("^" + R + N + "$");
    private static final Pattern PARTY_WITH = Pattern.compile("^You'll be partying with: (.+)$");
    private static final Pattern QUEUED = Pattern.compile("^Party Finder > Your party has been queued in the dungeon finder!$");
    private static final Pattern DUNGEON_JOIN = Pattern.compile("^Party Finder > " + N + " joined the dungeon group! \\(\\w+ Level \\d+\\)$");
    private static final Pattern KUUDRA_JOIN = Pattern.compile("^Party Finder > " + R + N + " joined the group! \\(Combat Level \\d+\\)$");
    private static final Pattern LIST = Pattern.compile("^Party (Leader|Moderators|Members): (.+)$");
    private static final Pattern[] DISBAND = {
            Pattern.compile("^" + R + N + " has disbanded the party!$"),
            Pattern.compile("^You have been kicked from the party by " + R + N + "$"),
            Pattern.compile("^The party was disbanded because all invites expired and the party was empty\\.$"),
            Pattern.compile("^The party was disbanded because the party leader disconnected\\.$"),
            Pattern.compile("^You left the party\\.$"),
            Pattern.compile("^You are not currently in a party\\.$"),
    };

    private static final List<String> members = new ArrayList<>();
    private static String leader;
    private static boolean inParty;

    private PartyTracker() {}

    public static void register() {
        Feature.of("PartyTracker")
                .chat(PartyTracker::onChat)
                .tickAlways(() -> { if (inParty && Minecraft.getInstance().getConnection() == null) disband(); });
    }

    public static List<String> members() { return members; }
    public static String leader() { return leader; }
    public static boolean inParty() { return inParty; }
    public static int size() { return members.size(); }

    public static boolean isLeader() {
        String me = me();
        return me != null && me.equalsIgnoreCase(leader);
    }

    /** True unless the tracker knows someone else leads. */
    public static boolean mayLead() {
        return leader == null || isLeader();
    }

    /** "ted" -> "Teddy123" when exactly one party member contains it, else the input. */
    public static String resolve(String partial) {
        if (partial == null) return null;
        String low = partial.toLowerCase(Locale.ROOT);
        String hit = null;
        for (String m : members) {
            if (m.equalsIgnoreCase(partial)) return m;
            if (m.toLowerCase(Locale.ROOT).contains(low)) {
                if (hit != null) return partial;
                hit = m;
            }
        }
        return hit != null ? hit : partial;
    }

    public static boolean isMember(String name) {
        for (String m : members) if (m.equalsIgnoreCase(name)) return true;
        return false;
    }

    static void onChat(String raw) {
        String s = StringUtil.strip(raw).trim();
        if (!s.contains("arty") && !s.startsWith("Kicked ")) return;
        Matcher m;
        if ((m = JOINED_OTHER.matcher(s)).matches()) { add(m.group(1)); return; }
        if ((m = JOINED_SELF.matcher(s)).matches()) {
            add(m.group(1));
            leader = m.group(1);
            if (me() != null) add(me());
            return;
        }
        if ((m = LEFT.matcher(s)).matches() || (m = KICKED.matcher(s)).matches()
                || (m = KICKED_OFFLINE.matcher(s)).matches() || (m = KICKED_DC.matcher(s)).matches()) {
            remove(m.group(1));
            return;
        }
        if ((m = TRANSFER_BY.matcher(s)).matches()) {
            add(m.group(1));
            add(m.group(2));
            leader = m.group(1);
            return;
        }
        if ((m = TRANSFER_LEAVE.matcher(s)).matches()) {
            add(m.group(1));
            leader = m.group(1);
            remove(m.group(2));
            return;
        }
        if ((m = LEADER_DC.matcher(s)).matches() || (m = LEADER_BACK.matcher(s)).matches()) { leader = m.group(1); return; }
        if ((m = PARTY_CHAT.matcher(s)).find()) { add(m.group(1)); return; }
        if ((m = INVITE.matcher(s)).matches()) {
            add(m.group(1));
            return;
        }
        if (QUEUED.matcher(s).matches()) {
            if (me() != null) {
                add(me());
                if (leader == null) leader = me();
            }
            return;
        }
        for (Pattern p : DISBAND) if (p.matcher(s).matches()) { disband(); return; }
        if ((m = LIST.matcher(s)).matches()) {
            boolean isLeader = m.group(1).equals("Leader");
            for (String seg : m.group(2).split("●")) {
                Matcher mm = MEMBER.matcher(seg.trim());
                if (!mm.matches()) continue;
                add(mm.group(1));
                if (isLeader) leader = mm.group(1);
            }
            return;
        }
        if ((m = PARTY_WITH.matcher(s)).matches()) {
            for (String seg : m.group(1).split(", ")) {
                Matcher mm = MEMBER.matcher(seg.trim());
                if (mm.matches()) add(mm.group(1));
            }
            return;
        }
        if ((m = KUUDRA_JOIN.matcher(s)).matches() || (m = DUNGEON_JOIN.matcher(s)).matches()) add(m.group(1));
    }

    private static void add(String name) {
        inParty = true;
        if (!isMember(name)) members.add(name);
    }

    private static void remove(String name) {
        members.removeIf(n -> n.equalsIgnoreCase(name));
        if (members.isEmpty()) disband();
    }

    /** From the Hypixel Mod API; names it could not resolve stay as the chat tracker had them. */
    static void applyApi(boolean party, String leaderName, List<String> names) {
        if (!party) {
            disband();
            return;
        }
        for (String n : names) add(n);
        inParty = true;
        if (leaderName != null) leader = leaderName;
    }

    private static void disband() {
        members.clear();
        leader = null;
        inParty = false;
    }

    private static String me() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.getGameProfile().name();
    }
}
