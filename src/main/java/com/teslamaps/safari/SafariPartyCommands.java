package com.teslamaps.safari;

// Ported from Sparkling Mutuals' PartyCommands / PartyCommand / ResponderElection / PartyResponseHistory / PartyReplyChunks
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SafariPartyCommands {

    private static final Pattern ENVELOPE = Pattern.compile(
            "^Party\\s*>\\s*(?:\\[[^\\]]+]\\s*)*([A-Za-z0-9_]{1,16})(?: [^:]{1,3})?:\\s*(.*)$");
    private static final Pattern LOOKUP = Pattern.compile("^!(missing|tickets?)(?:\\s+([A-Za-z0-9_]{1,16}))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_RUN = Pattern.compile("^!m(?:\\s+(\\S+))?$", Pattern.CASE_INSENSITIVE);
    private static final int LIMIT = 247;
    private static final long PART_GAP = 1500L;

    enum Kind { MUTUALS, MISSING, TICKETS, HELP, MISSING_RUN }

    record Command(Kind kind, String ign, SafariData.Biome biome) {
        String text() {
            return switch (kind) {
                case MUTUALS -> "!mutuals";
                case MISSING -> "!missing " + ign;
                case TICKETS -> "!tickets " + ign;
                case HELP -> "!commands";
                case MISSING_RUN -> biome == null ? "!m" : "!m " + biome.label.toLowerCase(Locale.ROOT);
            };
        }
    }

    record Request(String sender, Command command, String token) {}

    private static final class Election {
        final long startedAt;
        final int size;
        final int rank;
        boolean started;
        Long readyAt;

        Election(String local, Set<String> roster, long now) {
            startedAt = now;
            List<String> sorted = new ArrayList<>(new TreeSet<>(roster));
            java.util.Collections.reverse(sorted);
            size = sorted.size();
            rank = sorted.indexOf(local);
        }

        boolean expired(long now) {
            return !started && now - startedAt > Math.max(30000L, size * 800L + 2000L);
        }

        boolean shouldStartLookup(long now) {
            if (started || rank < 0 || expired(now)) return false;
            started = true;
            return true;
        }

        void lookupReady(long now) {
            if (started && readyAt == null) readyAt = now;
        }

        boolean canPublish(long now) {
            return started && readyAt != null && now - readyAt >= rank * 500L;
        }
    }

    private static final class Pending {
        final Request request;
        final Set<String> roster;
        final Election election;
        final long cursor;
        List<String> chunks;

        Pending(Request request, Set<String> roster, Election election, long cursor) {
            this.request = request;
            this.roster = roster;
            this.election = election;
            this.cursor = cursor;
        }
    }

    private record Queued(String body, Set<String> roster, long cursor, int part, Command command) {}

    private record Entry(long id, String text) {}

    private static final Map<String, Pending> pending = new LinkedHashMap<>();
    private static final Map<String, Long> recent = new HashMap<>();
    private static final ArrayDeque<Queued> replies = new ArrayDeque<>();
    private static final ArrayDeque<Entry> history = new ArrayDeque<>();
    private static long cursor = 0;
    private static long sentReplyAt = 0;

    private SafariPartyCommands() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean on() {
        return cfg().safariPartyCommands && cfg().section("Critter Safari");
    }

    public static void register() {
        Feature.of("SafariPartyCommands").section("Critter Safari").when(() -> cfg().safariPartyCommands)
                .chat(SafariPartyCommands::onChat)
                .tick(SafariPartyCommands::tick)
                .config("Critter Safari", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Safari Party Commands", () -> c.safariPartyCommands, v -> c.safariPartyCommands = v)
                            .desc("Answers !missing [ign], !tickets [ign], !mutuals, !m [biome] and !commands in party chat. With several TeslaMaps users in the party only one of them answers.");
                    b.toggle("  Timesave Critters Only", () -> c.safariPartyCommandsTimesave, v -> c.safariPartyCommandsTimesave = v)
                            .desc("!missing and !mutuals only list Rockmite, Snoozle, Gemzie, Honeybug, Gazer, Gimmiegold, Doomspiral, Wumpa and the birds.");
                });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> reset());
    }

    private static String me() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.getGameProfile().name();
    }

    private static Set<String> roster() {
        Set<String> out = new TreeSet<>();
        for (String m : PartyTracker.members()) out.add(m.toLowerCase(Locale.ROOT));
        String me = me();
        if (me != null) out.add(me.toLowerCase(Locale.ROOT));
        return out;
    }

    /** Left members cancel, people the tracker only now learns about do not. */
    private static boolean sameParty(Set<String> roster) {
        Set<String> now = roster();
        if (roster.size() > 1 && now.size() <= 1) return false;
        if (now.size() <= 1) return true;
        for (String m : roster) if (!now.contains(m)) return false;
        return true;
    }

    private static boolean containsResult(String result, long after) {
        if (result.isEmpty()) return false;
        for (Entry e : history) if (e.id > after && e.text.contains(result)) return true;
        return false;
    }

    private static void onChat(String raw) {
        Matcher env = ENVELOPE.matcher(StringUtil.strip(raw).trim());
        if (!env.matches()) return;
        String sender = env.group(1);
        String body = env.group(2).trim();
        history.addLast(new Entry(++cursor, body));
        while (history.size() > 200) history.removeFirst();

        Command cmd = parse(sender, body);
        if (cmd == null) return;
        if (cmd.kind == Kind.MISSING_RUN) {
            if (!SafariTracker.isActive()) return;
            if (cmd.biome == null) {
                SafariData.Biome here = SafariData.currentBiome();
                if (here == null) return;
                cmd = new Command(Kind.MISSING_RUN, cmd.ign, here);
            }
        }
        String me = me();
        if (me == null || Minecraft.getInstance().getConnection() == null) return;

        Request req = new Request(sender, cmd, token(sender, cmd));
        long now = System.currentTimeMillis();
        if (now - recent.getOrDefault(req.token, 0L) < 10000L) return;
        recent.put(req.token, now);
        Set<String> roster = roster();
        pending.put(req.token, new Pending(req, roster, new Election(me.toLowerCase(Locale.ROOT), roster, now), cursor));
    }

    static Command parse(String sender, String body) {
        String low = body.toLowerCase(Locale.ROOT);
        if (low.equals("!mutual") || low.equals("!mutuals")) return new Command(Kind.MUTUALS, "", null);
        if (low.equals("!commands")) return new Command(Kind.HELP, "", null);
        Matcher m = MISSING_RUN.matcher(body);
        if (m.matches()) {
            if (m.group(1) == null) return new Command(Kind.MISSING_RUN, "", null);
            SafariData.Biome b = biome(m.group(1));
            return b == null ? null : new Command(Kind.MISSING_RUN, "", b);
        }
        m = LOOKUP.matcher(body);
        if (!m.matches()) return null;
        String ign = m.group(2) == null || m.group(2).isBlank() ? sender : m.group(2);
        return new Command(m.group(1).equalsIgnoreCase("missing") ? Kind.MISSING : Kind.TICKETS, ign, null);
    }

    static SafariData.Biome biome(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "cavern", "c", "cav", "cave", "caves" -> SafariData.Biome.CAVERN;
            case "forest", "f", "for", "fore", "wood", "woods" -> SafariData.Biome.FOREST;
            case "haunted", "h", "haunt", "ghost", "spooky" -> SafariData.Biome.HAUNTED;
            case "icy", "i", "ice", "cold", "frozen" -> SafariData.Biome.ICY;
            default -> null;
        };
    }

    private static String token(String sender, Command cmd) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(
                    (sender.toLowerCase(Locale.ROOT) + ":" + cmd.text().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i] & 255));
            return sb.toString();
        } catch (Exception e) {
            return sender.toLowerCase(Locale.ROOT) + ":" + cmd.text().toLowerCase(Locale.ROOT);
        }
    }

    private static boolean stillFor(Command cmd) {
        return cmd.kind != Kind.MISSING_RUN || SafariTracker.isActive();
    }

    private static void tick() {
        if (Minecraft.getInstance().getConnection() == null) {
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        Queued q = replies.peekFirst();
        if (q != null) {
            if (stillFor(q.command) && sameParty(q.roster) && !containsResult(q.body, q.cursor)) {
                if (q.part == 0 || now - sentReplyAt >= PART_GAP) {
                    replies.removeFirst();
                    Announce.party(q.body);
                    sentReplyAt = now;
                }
            } else {
                replies.removeFirst();
            }
        }

        recent.entrySet().removeIf(e -> now - e.getValue() > 40000L && !pending.containsKey(e.getKey()));
        Iterator<Map.Entry<String, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Pending> e = it.next();
            Pending p = e.getValue();
            boolean stale = p.election.started && p.chunks == null && now - p.election.startedAt > 60000L;
            if (stale || p.election.expired(now) || !stillFor(p.request.command) || !sameParty(p.roster)) {
                it.remove();
                continue;
            }
            if (p.chunks != null && p.election.canPublish(now)) {
                for (int i = 0; i < p.chunks.size(); i++) {
                    String body = p.chunks.get(i);
                    if (!containsResult(body, p.cursor)) replies.addLast(new Queued(body, p.roster, p.cursor, i, p.request.command));
                }
                it.remove();
            } else if (p.election.shouldStartLookup(now)) {
                String token = e.getKey();
                lookup(p).whenComplete((msg, err) -> Minecraft.getInstance().execute(() -> {
                    if (pending.get(token) != p || !on()) return;
                    if (err != null || msg == null) {
                        pending.remove(token);
                        var pl = Minecraft.getInstance().player;
                        if (pl != null) pl.sendSystemMessage(Component.literal("§a[TeslaMaps] §7" + p.request.command.text()
                                + ": §c" + SparklingLookup.failure(err)));
                        return;
                    }
                    p.chunks = split(msg);
                    p.election.lookupReady(System.currentTimeMillis());
                }));
            }
        }
    }

    private static CompletableFuture<String> lookup(Pending p) {
        Command c = p.request.command;
        boolean ts = cfg().safariPartyCommandsTimesave;
        return switch (c.kind) {
            case HELP -> CompletableFuture.completedFuture("[TM] Commands: Party: !mutuals, !missing <IGN>, !tickets <IGN>, !m [biome], !commands");
            case MISSING_RUN -> {
                List<String> left = SafariTracker.missingIn(c.biome);
                yield CompletableFuture.completedFuture(left == null ? null
                        : "Missing " + c.biome.label + ": " + (left.isEmpty() ? "None" : String.join(", ", left)));
            }
            case MISSING -> SparklingLookup.fetch(c.ign, false).thenApply(pr ->
                    "Missing " + (ts ? "Timesave " : "") + "Shinies for " + c.ign + ": " + SparklingLookup.missingResult(pr.discoveries(), ts));
            case TICKETS -> SparklingLookup.fetch(c.ign, true).thenApply(pr -> c.ign
                    + ": Basic (" + pr.tickets().get("basic") + "), Economy (" + pr.tickets().get("economy")
                    + "), Premium (" + pr.tickets().get("premium") + "), First-Class (" + pr.tickets().get("first_class")
                    + "), Total (" + pr.ticketTotal() + ")");
            case MUTUALS -> {
                List<CompletableFuture<SparklingLookup.Profile>> all = new ArrayList<>();
                for (String m : partyNames()) all.add(SparklingLookup.fetch(m, false));
                yield CompletableFuture.allOf(all.toArray(new CompletableFuture[0])).thenApply(v -> {
                    List<SparklingLookup.Profile> got = new ArrayList<>();
                    for (var f : all) got.add(f.join());
                    return "Mutual " + (ts ? "Timesave " : "") + "Shiny Critters: "
                            + SparklingLookup.mutualResult(SparklingLookup.intersect(got), ts);
                });
            }
        };
    }

    private static List<String> partyNames() {
        List<String> out = new ArrayList<>();
        String me = me();
        if (me != null) out.add(me);
        for (String m : PartyTracker.members()) if (me == null || !m.equalsIgnoreCase(me)) out.add(m);
        return out;
    }

    /** "Prefix: a, b, c" -> "Prefix [1/2]: a, b" ... when over the chat limit. */
    static List<String> split(String text) {
        if (text.length() <= LIMIT) return List.of(text);
        int sep = text.indexOf(": ");
        if (sep < 0) return List.of(text.substring(0, LIMIT));
        String prefix = text.substring(0, sep);
        String[] items = text.substring(sep + 2).split(", ");
        int capacity = LIMIT - prefix.length() - (2 * String.valueOf(items.length).length() + 6);
        List<String> groups = new ArrayList<>();
        String cur = "";
        for (String item : items) {
            String next = cur.isEmpty() ? item : cur + ", " + item;
            if (next.length() > capacity && !cur.isEmpty()) {
                groups.add(cur);
                cur = item;
            } else {
                cur = next;
            }
        }
        if (!cur.isEmpty()) groups.add(cur);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) out.add(prefix + " [" + (i + 1) + "/" + groups.size() + "]: " + groups.get(i));
        return out;
    }

    private static void reset() {
        pending.clear();
        replies.clear();
        recent.clear();
        history.clear();
        cursor = 0;
        sentReplyAt = 0;
    }
}
