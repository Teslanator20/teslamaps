package com.teslamaps.safari;

// Ported from Sparkling Mutuals' SparklingLookup / SafariProfile / SafariLookup
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.utils.PartyTracker;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SparklingLookup {

    private static final String PREFIX = "§a[TeslaMaps] §7";
    public static final List<String> TICKET_TIERS = List.of("basic", "economy", "premium", "first_class");
    private static final String[] TICKET_LABELS = {"§aBasic", "§9Economy", "§5Premium", "§6First-Class"};
    static final List<String> TIMESAVES = List.of("Rockmite", "Snoozle", "Gemzie", "Honeybug", "Gazer", "Gimmiegold", "Doomspiral", "Wumpa");
    static final List<String> BIRDS = List.of("Bluebird", "Parakeet", "Macaw");
    private static final long[] PER_LEVEL = {50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000,
            20000, 30000, 50000, 75000, 100000, 200000, 300000, 400000, 500000, 600000, 700000, 800000, 900000, 1000000,
            1100000, 1200000, 1300000, 1400000, 1500000, 1600000, 1700000, 1800000, 1900000, 2000000, 2100000, 2200000,
            2300000, 2400000, 2500000, 2600000, 2750000, 2900000, 3100000, 3400000, 3700000, 4000000, 4300000, 4600000,
            4900000, 5200000, 5500000, 5800000, 6100000, 6400000, 6700000, 7000000};

    private SparklingLookup() {}

    public static final class Failed extends RuntimeException {
        public Failed(String msg) { super(msg, null, false, false); }
    }

    public record Profile(Set<String> discoveries, Map<String, Long> tickets, Double huntingXp, Long essence, Long totalCaptured) {
        public List<String> caught() {
            List<String> out = new ArrayList<>();
            for (SafariData.Critter c : SafariData.ALL) if (discoveries.contains(key(c.name()))) out.add(c.name());
            return out;
        }

        public List<String> missing() {
            List<String> out = new ArrayList<>();
            for (SafariData.Critter c : SafariData.ALL) if (!discoveries.contains(key(c.name()))) out.add(c.name());
            return out;
        }

        public long ticketTotal() {
            long n = 0;
            for (long v : tickets.values()) n += v;
            return n;
        }
    }

    public static String key(String name) {
        return name.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public static double levelFromXp(double xp) {
        int level = 0;
        double remaining = Math.max(0, xp);
        for (long need : PER_LEVEL) {
            if (remaining < need) return level + remaining / need;
            remaining -= need;
            level++;
        }
        long slope = 600000L;
        long need = 7000000L + slope;
        while (remaining >= need) {
            remaining -= need;
            if (++level % 10 == 0) slope *= 2;
            need += slope;
        }
        return level + remaining / need;
    }

    /** Selected profile of a player. Fails with a readable message. */
    public static CompletableFuture<Profile> fetch(String name, boolean fresh) {
        return HypixelApi.nameToUuid(name).thenCompose(uuid -> {
            if (uuid == null) throw new Failed("Minecraft player not found: " + name);
            if (fresh) HypixelApi.invalidateProfiles(uuid);
            return HypixelApi.getProfiles(uuid);
        }).thenApply(resp -> {
            if (resp == null || !resp.has("profiles")) {
                throw new Failed(resp != null && resp.has("error") ? resp.get("error").getAsString() : "Lookup failed. Try again.");
            }
            JsonObject member = selected(resp.getAsJsonObject("profiles"));
            if (member == null) throw new Failed(name + " has no SkyBlock profile.");
            return from(member);
        });
    }

    public static String failure(Throwable t) {
        while (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
        return t instanceof Failed ? t.getMessage() : "Lookup failed. Try again.";
    }

    private static JsonObject selected(JsonObject profiles) {
        JsonObject fallback = null;
        for (Map.Entry<String, JsonElement> e : profiles.entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            JsonObject data = p.has("data") ? p.getAsJsonObject("data") : null;
            if (data == null) continue;
            if (fallback == null) fallback = data;
            if (p.has("current") && p.get("current").getAsBoolean()) return data;
        }
        return fallback;
    }

    static Profile from(JsonObject member) {
        Set<String> found = new HashSet<>();
        JsonElement list = at(member, "safari.discovered_sparkling_critters");
        if (list != null && list.isJsonArray()) {
            for (JsonElement e : list.getAsJsonArray()) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) found.add(e.getAsString());
            }
        }
        Map<String, Long> tickets = new LinkedHashMap<>();
        for (String t : TICKET_TIERS) {
            JsonElement n = number(member, "safari.tickets." + t);
            tickets.put(t, n == null ? 0L : n.getAsLong());
        }
        JsonElement xp = number(member, "player_data.experience.SKILL_HUNTING");
        JsonElement ess = number(member, "currencies.essence.SAFARI.current");
        JsonElement total = number(member, "safari.total_captured_sparkling_critters");
        Long totalCaptured = total != null ? Long.valueOf(total.getAsLong()) : found.isEmpty() ? Long.valueOf(0L) : null;
        return new Profile(found, tickets, xp == null ? null : xp.getAsDouble(), ess == null ? null : ess.getAsLong(), totalCaptured);
    }

    private static JsonElement at(JsonObject root, String path) {
        JsonElement cur = root;
        for (String step : path.split("\\.")) {
            if (cur == null || !cur.isJsonObject()) return null;
            cur = cur.getAsJsonObject().get(step);
        }
        return cur;
    }

    private static JsonElement number(JsonObject root, String path) {
        JsonElement e = at(root, path);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e : null;
    }

    /** Critters every list contains, roster order. */
    public static List<String> shared(List<List<String>> caught) {
        List<String> out = new ArrayList<>();
        for (SafariData.Critter c : SafariData.ALL) {
            boolean all = true;
            for (List<String> l : caught) if (!l.contains(c.name())) { all = false; break; }
            if (all) out.add(c.name());
        }
        return out;
    }

    // ---- /sparkling ----

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(node("sparkling"));
            d.register(ClientCommands.literal("tmap").then(node("sparkling")));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> node(String name) {
        return ClientCommands.literal(name)
                .executes(c -> {
                    String me = me();
                    if (me != null) card(me);
                    return 1;
                })
                .then(ClientCommands.argument("players", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            String rem = b.getRemaining();
                            int cut = rem.lastIndexOf(' ') + 1;
                            String word = rem.substring(cut).toLowerCase(Locale.ROOT);
                            var sb = b.createOffset(b.getStart() + cut);
                            if ("party".startsWith(word) && !"party".equals(word)) sb.suggest("party");
                            for (String m : PartyTracker.members()) {
                                if (m.toLowerCase(Locale.ROOT).startsWith(word) && !m.equalsIgnoreCase(word)) sb.suggest(m);
                            }
                            return sb.buildFuture();
                        })
                        .executes(c -> {
                            List<String> names = new ArrayList<>();
                            Set<String> seen = new HashSet<>();
                            for (String s : StringArgumentType.getString(c, "players").trim().split("\\s+")) {
                                if (!s.isEmpty() && seen.add(s.toLowerCase(Locale.ROOT))) names.add(s);
                            }
                            if (names.size() == 1 && names.get(0).equalsIgnoreCase("party")) party();
                            else if (names.size() == 1) card(names.get(0));
                            else if (!names.isEmpty()) compare(names);
                            return 1;
                        }));
    }

    private static String me() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.getGameProfile().name();
    }

    private static void tell(Component c) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(c);
        });
    }

    private static void tell(String text) {
        tell(Component.literal(PREFIX + text));
    }

    private static void card(String name) {
        tell("Looking up " + name + "...");
        fetch(name, false).whenComplete((p, err) -> {
            if (err != null) tell("§c" + failure(err));
            else show(name, p);
        });
    }

    private static void party() {
        if (!PartyTracker.inParty()) {
            tell("You're not in a party. §8(or not seen yet - try /p list)");
            return;
        }
        List<String> names = new ArrayList<>();
        String me = me();
        if (me != null) names.add(me);
        for (String m : PartyTracker.members()) if (me == null || !m.equalsIgnoreCase(me)) names.add(m);
        if (names.size() < 2) {
            tell("You're not in a party.");
            return;
        }
        compare(names);
    }

    private static void compare(List<String> names) {
        tell("Looking up " + String.join(", ", names) + "...");
        Map<String, CompletableFuture<Profile>> all = new LinkedHashMap<>();
        for (String n : names) all.put(n, fetch(n, false));
        CompletableFuture.allOf(all.values().toArray(new CompletableFuture[0])).handle((v, e) -> {
            report(all);
            return null;
        });
    }

    private static void report(Map<String, CompletableFuture<Profile>> results) {
        Map<String, Profile> found = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, CompletableFuture<Profile>> e : results.entrySet()) {
            Profile p = e.getValue().isCompletedExceptionally() ? null : e.getValue().getNow(null);
            if (p == null) failed.add(e.getKey());
            else found.put(e.getKey(), p);
        }
        for (Map.Entry<String, Profile> e : found.entrySet()) {
            Profile p = e.getValue();
            tell(Component.literal(PREFIX + "§f" + e.getKey() + " §7- §f" + p.caught().size() + "§7/" + SafariData.ALL.size()
                    + " sparklings §8(hover)").withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover(p.caught(), p.missing())))));
        }
        if (!failed.isEmpty()) tell("§cCouldn't look up: §f" + String.join(", ", failed));
        if (found.size() >= 2) {
            List<List<String>> caught = new ArrayList<>();
            List<List<String>> missing = new ArrayList<>();
            for (Profile p : found.values()) {
                caught.add(p.caught());
                missing.add(p.missing());
            }
            List<String> have = shared(caught);
            List<String> lack = shared(missing);
            tell("Sparklings all " + found.size() + " have (" + have.size() + "): "
                    + (have.isEmpty() ? "§8none" : "§f" + String.join(", ", have)));
            tell("Sparklings none of them have (" + lack.size() + "): "
                    + (lack.isEmpty() ? "§8none" : "§f" + String.join(", ", lack)));
        }
    }

    private static void show(String name, Profile p) {
        MutableComponent hover = hover(p.caught(), p.missing());
        List<String> lines = new ArrayList<>();
        lines.add("§6§l" + name);
        Double xp = p.huntingXp();
        String lvl = "?";
        if (xp != null) {
            double l = levelFromXp(xp);
            lvl = l > 60 ? String.format(Locale.ROOT, "%.2f", l) : String.valueOf((int) l);
        }
        lines.add("§7Hunting level §f" + lvl);
        lines.add("§f" + p.caught().size() + "§7/" + SafariData.ALL.size() + " sparklings §8(hover to expand)");
        if (p.totalCaptured() != null) {
            long total = p.totalCaptured();
            long repeats = Math.max(0, total - p.caught().size());
            lines.add("§7Total sparklings: §f" + total + " §8(" + repeats + " duplicate" + (repeats == 1 ? "" : "s") + ")");
        }
        StringBuilder t = new StringBuilder("§7Tickets: ");
        for (int i = 0; i < TICKET_TIERS.size(); i++) {
            if (i > 0) t.append(" §8| ");
            t.append(TICKET_LABELS[i]).append(" §f").append(p.tickets().getOrDefault(TICKET_TIERS.get(i), 0L));
        }
        t.append(" §8| §b(").append(p.ticketTotal()).append(")");
        lines.add(t.toString());
        if (p.essence() != null) lines.add("§7Safari Essence: §d" + String.format(Locale.ROOT, "%,d", p.essence()));

        Component rule = Component.literal("§6§m                    ");
        tell(rule);
        for (String l : lines) tell(Component.literal(l).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover))));
        tell(rule);
    }

    private static MutableComponent hover(List<String> caught, List<String> missing) {
        MutableComponent out = Component.literal("§a§lSparklings caught §7(" + caught.size() + ")\n");
        table(out, caught);
        out.append("\n§c§lSparklings missing §7(" + missing.size() + ")\n");
        table(out, missing);
        return out;
    }

    private static void table(MutableComponent out, List<String> names) {
        if (names.isEmpty()) {
            out.append("§7none\n");
            return;
        }
        for (SafariData.Biome b : SafariData.Biome.values()) {
            List<SafariData.Critter> in = new ArrayList<>();
            for (String n : names) {
                SafariData.Critter c = SafariData.byName(n);
                if (c != null && c.biome() == b) in.add(c);
            }
            if (in.isEmpty()) continue;
            in.sort((x, y) -> x.rarity().compareTo(y.rarity()));
            out.append(Component.literal(b.color + "§l" + b.label + " (" + in.size() + ")§r§8: "));
            for (int i = 0; i < in.size(); i++) {
                if (i > 0) out.append(Component.literal("§8, "));
                int color = in.get(i).rarity().color;
                out.append(Component.literal(in.get(i).name()).withStyle(s -> s.withColor(color)));
            }
            out.append("\n");
        }
    }

    // ---- party command results ----

    static String mutualResult(Set<String> discoveries, boolean timesave) {
        List<String> l = formatDiscoveries(discoveries, timesave);
        return l.isEmpty() ? "None. :skull:" : String.join(", ", l);
    }

    static String missingResult(Set<String> discoveries, boolean timesave) {
        List<String> missing = formatMissing(discoveries, timesave);
        if (missing.isEmpty()) return "None";
        if (missing.equals(formatMissing(Set.of(), timesave))) return "All. :skull:";
        return String.join(", ", missing);
    }

    private static List<String> formatDiscoveries(Set<String> d, boolean timesave) {
        List<String> out = new ArrayList<>();
        if (timesave) {
            for (String n : TIMESAVES) if (d.contains(key(n))) out.add(n);
            for (String n : BIRDS) if (d.contains(key(n))) out.add(n);
        } else {
            for (SafariData.Critter c : SafariData.ALL) if (d.contains(key(c.name()))) out.add(c.name());
        }
        return out;
    }

    private static List<String> formatMissing(Set<String> d, boolean timesave) {
        List<String> out = new ArrayList<>();
        if (timesave) {
            for (String n : TIMESAVES) if (!d.contains(key(n))) out.add(n);
            for (String n : BIRDS) if (!d.contains(key(n))) out.add(n);
        } else {
            for (SafariData.Critter c : SafariData.ALL) if (!d.contains(key(c.name()))) out.add(c.name());
        }
        return out;
    }

    static Set<String> intersect(List<Profile> profiles) {
        Set<String> out = null;
        for (Profile p : profiles) {
            if (out == null) out = new LinkedHashSet<>(p.discoveries());
            else out.retainAll(p.discoveries());
        }
        return out == null ? Set.of() : out;
    }
}
