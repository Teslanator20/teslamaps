package com.teslamaps.adm.adr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's PartyState.kt
/** The party, worked out from Hypixel's chat as it happens; {@link #doubt} says why a /p list is wanted. */
public class PartyState {
    public enum Role { LEADER, MOD, MEMBER }

    public static class Member {
        public final String name;
        public Role role;
        public boolean online;

        Member(String name, Role role) { this(name, role, true); }

        Member(String name, Role role, boolean online) {
            this.name = name;
            this.role = role;
            this.online = online;
        }
    }

    private final Supplier<String> self;

    public PartyState(Supplier<String> self) {
        this.self = self;
    }

    private boolean known = false;
    /** Everyone in it, you included (lower case name -> member). Empty and known: not in a party. */
    public final LinkedHashMap<String, Member> members = new LinkedHashMap<>();
    private String doubt = "not known yet";
    private int version = 0;

    public boolean known() { return known; }
    public String doubt() { return doubt; }
    public int version() { return version; }
    public boolean inParty() { return !members.isEmpty(); }

    public String leader() {
        for (Member m : members.values()) if (m.role == Role.LEADER) return m.name;
        return null;
    }

    private ListRead list = null;

    private static class ListRead {
        final int size;
        final long at;
        final LinkedHashMap<String, Member> members = new LinkedHashMap<>();

        ListRead(int size, long at) {
            this.size = size;
            this.at = at;
        }
    }

    private String me() { return self.get().toLowerCase(); }
    private void changed() { version++; }
    private void sure() { known = true; doubt = null; }
    private void unsure(String why) { if (doubt == null) doubt = why; changed(); }

    private void clear() { members.clear(); sure(); changed(); }

    private void add(String name) { add(name, Role.MEMBER); }

    private void add(String name, Role role) {
        String k = name.toLowerCase();
        Member m = members.get(k);
        if (m == null) members.put(k, new Member(name, role));
        else {
            m.online = true;
            if (role != Role.MEMBER) m.role = role;
        }
        changed();
    }

    private void remove(String name) {
        if (members.remove(name.toLowerCase()) == null) unsure(name + " left, but wasn't known to be in the party");
        if (doubt == null && members.size() == 1 && members.containsKey(me())) members.clear();
        changed();
    }

    private void setLeader(String name) {
        for (Member m : members.values()) if (m.role == Role.LEADER) m.role = Role.MOD;
        add(name, Role.LEADER);
        Member l = members.remove(name.toLowerCase());
        List<Member> rest = new ArrayList<>(members.values());
        members.clear();
        members.put(l.name.toLowerCase(), l);
        for (Member m : rest) members.put(m.name.toLowerCase(), m);
    }

    private void ensureIn() {
        if (!members.containsKey(me())) members.put(me(), new Member(self.get(), Role.MEMBER));
    }

    public void reset(String why) {
        members.clear();
        known = false;
        doubt = why;
        list = null;
        changed();
    }

    public boolean line(String text, String colored) {
        return line(text, colored, System.currentTimeMillis());
    }

    /** One line of system chat (colored: with § codes). True if it was part of a /p list. */
    public boolean line(String text, String colored, long now) {
        String t = text.trim();
        if (list != null && now - list.at > 3000) finishList();
        if (readList(t, colored, now)) return true;

        Matcher m;
        if ((m = JOINED_SELF.matcher(t)).matches()) {
            members.clear();
            add(m.group(1), Role.LEADER);
            ensureIn(); sure(); changed();
            return false;
        }
        if ((m = PARTYING_WITH.matcher(t)).matches()) {
            for (String n : names(m.group(1))) add(n);
            ensureIn(); changed();
            return false;
        }
        if ((m = JOINED_OTHER.matcher(t)).matches()) {
            String n = m.group(1);
            if (!inParty()) {
                if (known) add(self.get(), Role.LEADER);
                else { ensureIn(); unsure(n + " joined a party that wasn't known"); }
            }
            add(n);
            return false;
        }
        if ((m = LEFT.matcher(t)).matches()) { remove(m.group(1)); return false; }
        if ((m = REMOVED.matcher(t)).matches()) { remove(m.group(1)); return false; }
        if ((m = REMOVED_DC.matcher(t)).matches()) { remove(m.group(1)); return false; }
        if ((m = KICKED_OFFLINE.matcher(t)).matches()) { for (String n : names(m.group(1))) remove(n); return false; }
        if (NOT_IN_PARTY.matcher(t).matches() || YOU_LEFT.matcher(t).matches() || YOU_KICKED.matcher(t).matches()
                || DISBANDED.matcher(t).matches() || DISBANDED_BY.matcher(t).matches()) { clear(); return false; }
        if ((m = TRANSFER_BY.matcher(t)).matches()) {
            if (!inParty()) unsure("a transfer in a party that wasn't known");
            ensureIn();
            setLeader(m.group(1));
            add(m.group(2), Role.MOD);
            return false;
        }
        if ((m = TRANSFER_LEFT.matcher(t)).matches()) {
            if (!inParty()) unsure("a transfer in a party that wasn't known");
            ensureIn();
            members.remove(m.group(2).toLowerCase());
            setLeader(m.group(1));
            return false;
        }
        if ((m = PROMOTED.matcher(t)).matches()) {
            ensureKnownIn("a promotion");
            if (m.group(3).equals("Leader")) { setLeader(m.group(2)); add(m.group(1), Role.MOD); }
            else { add(m.group(1)); add(m.group(2), Role.MOD); }
            return false;
        }
        if ((m = DEMOTED.matcher(t)).matches()) {
            ensureKnownIn("a demotion");
            add(m.group(1));
            Member d = members.get(m.group(2).toLowerCase());
            if (d != null) { d.role = Role.MEMBER; changed(); } else add(m.group(2));
            return false;
        }
        if ((m = INVITED.matcher(t)).matches()) {
            String by = m.group(1);
            if (by.equalsIgnoreCase(self.get())) {
                if (!inParty() && known) { add(self.get(), Role.LEADER); return false; }
                if (!inParty()) { ensureIn(); unsure("an invite from a party that wasn't known"); }
            } else if (!members.containsKey(by.toLowerCase())) {
                ensureKnownIn("an invite from someone not known to be in the party");
                add(by);
            }
            return false;
        }
        if ((m = LEADER_DC.matcher(t)).matches()) {
            ensureKnownIn("the leader disconnecting");
            setLeader(m.group(1));
            Member l = members.get(m.group(1).toLowerCase());
            if (l != null) l.online = false;
            changed();
            return false;
        }
        if ((m = LEADER_BACK.matcher(t)).matches()) { ensureKnownIn("the leader rejoining"); setLeader(m.group(1)); return false; }
        if ((m = MEMBER_DC.matcher(t)).matches()) {
            ensureKnownIn("someone disconnecting");
            String n = m.group(1);
            if (!members.containsKey(n.toLowerCase())) { add(n); unsure(n + " disconnected, but wasn't known to be in the party"); }
            Member d = members.get(n.toLowerCase());
            if (d != null) d.online = false;
            changed();
            return false;
        }
        if ((m = SUMMONED.matcher(t)).matches()) { leaderEvidence(m.group(1)); return false; }
        if ((m = ALL_INVITE.matcher(t)).matches()) { leaderEvidence(m.group(1)); return false; }
        if ((m = PF_JOINED.matcher(t)).matches()) {
            String n = m.group(1);
            if (n.equalsIgnoreCase(self.get())) {
                members.clear(); ensureIn(); known = true; doubt = "joined a Party Finder group"; changed();
            } else {
                if (!inParty()) {
                    if (known) add(self.get(), Role.LEADER);
                    else { ensureIn(); unsure(n + " joined a party that wasn't known"); }
                }
                add(n);
            }
            return false;
        }
        if ((m = PARTY_CHAT.matcher(t)).matches()) {
            String n = m.group(1);
            if (!members.containsKey(n.toLowerCase())) {
                ensureIn();
                if (!n.equalsIgnoreCase(self.get())) add(n);
                unsure("party chat from " + n + ", who wasn't known to be in the party");
            } else {
                Member p = members.get(n.toLowerCase());
                if (!p.online) { p.online = true; changed(); }
            }
            return false;
        }
        return false;
    }

    private void ensureKnownIn(String what) {
        if (!inParty()) { ensureIn(); unsure(what + " in a party that wasn't known"); }
    }

    private void leaderEvidence(String name) {
        ensureKnownIn("a leader's action");
        Member m = members.get(name.toLowerCase());
        if (m == null || m.role != Role.LEADER) {
            String lead = leader();
            if (lead != null && !lead.equalsIgnoreCase(name)) unsure(name + " acted as leader, but " + lead + " was");
            setLeader(name);
        }
    }

    private boolean readList(String t, String colored, long now) {
        Matcher h = LIST_HEAD.matcher(t);
        if (h.matches()) { list = new ListRead(Integer.parseInt(h.group(1)), now); return true; }
        ListRead l = list;
        if (l == null) return false;
        if (t.isEmpty() || SEPARATOR.matcher(t).matches()) { if (l.members.size() >= l.size) finishList(); return true; }
        Matcher m = LIST_ROW.matcher(t);
        if (!m.matches()) { finishList(); return false; }
        Role role = switch (m.group(1)) { case "Leader" -> Role.LEADER; case "Moderators" -> Role.MOD; default -> Role.MEMBER; };
        List<Boolean> dots = colored != null ? dots(colored) : null;
        Matcher e = ENTRY.matcher(m.group(2));
        int i = 0;
        while (e.find()) {
            String n = e.group(1);
            boolean on = dots == null || i >= dots.size() || dots.get(i);
            l.members.put(n.toLowerCase(), new Member(n, role, on));
            i++;
        }
        if (l.members.size() >= l.size) finishList();
        return true;
    }

    private void finishList() {
        ListRead l = list;
        if (l == null) return;
        list = null;
        members.clear();
        l.members.values().stream().sorted(Comparator.comparingInt(x -> x.role.ordinal()))
                .forEach(x -> members.put(x.name.toLowerCase(), x));
        if (members.size() == 1) members.clear();
        sure(); changed();
    }

    /** The dungeon's teammates: the party is exactly them. */
    public void teammates(List<String> names) {
        if (names.isEmpty()) return;
        LinkedHashMap<String, String> want = new LinkedHashMap<>();
        for (String n : names) want.put(n.toLowerCase(), n);
        if (want.keySet().equals(members.keySet())) return;
        String lead = leader();
        members.clear();
        for (Map.Entry<String, String> e : want.entrySet())
            members.put(e.getKey(), new Member(e.getValue(), e.getValue().equalsIgnoreCase(lead) ? Role.LEADER : Role.MEMBER));
        if (members.size() == 1) members.clear();
        known = true;
        if (lead == null || !want.containsKey(lead.toLowerCase())) unsure("in a dungeon, but who leads isn't known");
        else changed();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!known) sb.append("? ");
        if (!inParty()) sb.append(known ? "solo" : "");
        else {
            boolean first = true;
            for (Member m : members.values()) {
                if (!first) sb.append(' ');
                first = false;
                sb.append(m.role == Role.LEADER ? "*" : m.role == Role.MOD ? "+" : "").append(m.name).append(m.online ? "" : "(off)");
            }
        }
        if (doubt != null) sb.append(" [doubt: ").append(doubt).append(']');
        return sb.toString();
    }

    private static final String N = "(?:\\[[^\\]]{1,20}] )?(\\w{1,16})";
    private static final Pattern JOINED_SELF = Pattern.compile("^You have joined " + N + "'s? party!$");
    private static final Pattern PARTYING_WITH = Pattern.compile("^You'll be partying with: (.+)$");
    private static final Pattern JOINED_OTHER = Pattern.compile("^" + N + " joined the party\\.$");
    private static final Pattern LEFT = Pattern.compile("^" + N + " has left the party\\.$");
    private static final Pattern REMOVED = Pattern.compile("^" + N + " has been removed from the party\\.$");
    private static final Pattern REMOVED_DC = Pattern.compile("^" + N + " was removed from your party because they disconnected\\.$");
    private static final Pattern KICKED_OFFLINE = Pattern.compile("^Kicked (.+?) because they were offline\\.$");
    static final Pattern NOT_IN_PARTY = Pattern.compile("^You are not (?:currently )?in a party\\.$");
    private static final Pattern YOU_LEFT = Pattern.compile("^You left the party\\.$");
    private static final Pattern YOU_KICKED = Pattern.compile("^You have been kicked from the party by .+$");
    private static final Pattern DISBANDED = Pattern.compile("^The party was disbanded because .+$");
    private static final Pattern DISBANDED_BY = Pattern.compile("^" + N + " has disbanded the party!$");
    private static final Pattern TRANSFER_BY = Pattern.compile("^The party was transferred to " + N + " by " + N + "$");
    private static final Pattern TRANSFER_LEFT = Pattern.compile("^The party was transferred to " + N + " because " + N + " left$");
    private static final Pattern PROMOTED = Pattern.compile("^" + N + " has promoted " + N + " to Party (Leader|Moderator)$");
    private static final Pattern DEMOTED = Pattern.compile("^" + N + " has demoted " + N + " to Party Member$");
    private static final Pattern INVITED = Pattern.compile("^" + N + " invited " + N + " to the party! They have 60 seconds to accept\\.$");
    private static final Pattern LEADER_DC = Pattern.compile("^The party leader, " + N + " has disconnected, .+$");
    private static final Pattern LEADER_BACK = Pattern.compile("^The party leader " + N + " has rejoined\\.$");
    private static final Pattern MEMBER_DC = Pattern.compile("^" + N + " has disconnected, they have \\d+ minutes? to rejoin before they are removed from the party\\.$");
    private static final Pattern SUMMONED = Pattern.compile("^Party Leader, " + N + ", summoned you to their server\\.$");
    private static final Pattern ALL_INVITE = Pattern.compile("^" + N + " (?:enabled|disabled) All Invite$");
    private static final Pattern PF_JOINED = Pattern.compile("^Party Finder > (\\w{1,16}) joined the (?:dungeon )?group! \\(.+\\)$");
    private static final Pattern PARTY_CHAT = Pattern.compile("^Party > " + N + ": .*$");
    private static final Pattern LIST_HEAD = Pattern.compile("^Party Members \\((\\d+)\\)$");
    private static final Pattern LIST_ROW = Pattern.compile("^Party (Leader|Moderators|Members): (.+)$");
    private static final Pattern ENTRY = Pattern.compile("(?:\\[[^\\]]{1,20}] )?(\\w{1,16}) ●");
    static final Pattern SEPARATOR = Pattern.compile("^-{10,}$");
    private static final Pattern NAME_ONLY = Pattern.compile("^" + N + "$");
    private static final Pattern NAME_SPLIT = Pattern.compile(",\\s*|\\s+and\\s+");

    /** For each ● in a coloured /p list row: green (online) rather than red. */
    public static List<Boolean> dots(String colored) {
        List<Boolean> out = new ArrayList<>();
        char color = 'f';
        int i = 0;
        while (i < colored.length()) {
            char c = colored.charAt(i);
            if (c == '§' && i + 1 < colored.length()) {
                char k = Character.toLowerCase(colored.charAt(i + 1));
                if ("0123456789abcdef".indexOf(k) >= 0) color = k;
                i += 2;
                continue;
            }
            if (c == '●') out.add(color != 'c' && color != '4');
            i++;
        }
        return out;
    }

    /** "[MVP+] A, B, [VIP] C" (or "A and B") -> names. */
    public static List<String> names(String s) {
        List<String> out = new ArrayList<>();
        for (String part : NAME_SPLIT.split(s)) {
            Matcher m = NAME_ONLY.matcher(part.trim());
            if (m.matches()) out.add(m.group(1));
        }
        return out;
    }
}
