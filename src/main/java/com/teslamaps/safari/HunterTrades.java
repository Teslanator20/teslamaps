package com.teslamaps.safari;

// Ported from critterMod's client/TraderWatch.java, parse/TraderParser.java, client/TradeHud.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonInfoHud;
import com.teslamaps.features.Announce;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HunterTrades {

    private static final Pattern NPC_LINE = Pattern.compile("^\\[NPC] ([^:]{1,40}): (.*)$");
    private static final Pattern PRICE = Pattern.compile(
            "(?:in exchange for(?:,\\s*say,)?|give m-m-me)\\s+(?:an?|the)\\s+(.+?)\\s*[.!?…]*$");
    private static final Pattern SHARED_TRADE = Pattern.compile(
            "^(.+?) \\((?:([A-Za-z]+) )?(-?\\d+) (-?\\d+) (-?\\d+)\\): (.+?) Shard for (.+)$");
    private static final Pattern TRADE_TAKEN = Pattern.compile("^You have been given an? (.+)!$");
    private static final Pattern SHARD_RECEIVED = Pattern.compile("^You received (?:an? |\\d+x )?(.+) Shard!$");
    private static final Pattern SHARED_TAKEN = Pattern.compile("^Trade taken: (.+) Shard$");
    private static final Pattern COLOR = Pattern.compile("§.");

    private static final long REPEAT_MS = 5 * 60_000L;
    private static final int MAX = 6;
    private static final List<String> SAMPLE = List.of("§6Hunter Trades",
            "§6Gimmiegold §7← §ePurple Gem", "§8  Icy -106 87 -7 · 34m");

    record Trade(String npc, SafariData.Critter critter, String item, BlockPos pos, SafariData.Biome biome) {}

    private static final Map<String, SafariData.Critter> pending = new HashMap<>();
    private static final Map<String, BlockPos> offerSpots = new HashMap<>();
    private static final Map<String, BlockPos> lastSeen = new HashMap<>();
    private static final Map<String, Long> announced = new HashMap<>();
    private static final List<Trade> found = new ArrayList<>();
    private static List<String> lines = List.of();
    private static List<Trade> drawn = List.of();
    private static List<String> labels = List.of();
    private static int width;
    private static int ticks;
    private static boolean here;
    private static ClientLevel lastLevel;
    private static HudEditElement el;

    private HunterTrades() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        el = HudEditElement.of("hunterTrades", "Hunter Trades").ctx(HudEditElement.HudCtx.SAFARI).section("Critter Safari")
                .enabled(() -> cfg().hunterTrades)
                .size(() -> sampleWidth(), () -> SAMPLE.size() * 10)
                .preview((g, mc, x, y, s) -> DungeonInfoHud.draw(g, mc, x, y, s, SAMPLE, sampleWidth()));
        Feature.of("HunterTrades").section("Critter Safari").when(() -> cfg().hunterTrades)
                .tick(HunterTrades::tick)
                .chat(HunterTrades::onChat)
                .world(HunterTrades::render)
                .hud("huntertrades", HunterTrades::renderHud)
                .config("Critter Safari", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Hunter Trades");
                    b.toggle("Hunter Trade Tracker", () -> c.hunterTrades, v -> c.hunterTrades = v)
                            .desc("Reads the Hunter NPCs' shard-for-item offers: HUD list (/tmap gui), waypoint box, party sharing");
                    b.toggle("  Chat Line Per Offer", () -> c.hunterTradesChat, v -> c.hunterTradesChat = v);
                    b.channel("  Post Offers To", () -> c.hunterTradesChannel, v -> c.hunterTradesChannel = v);
                    b.toggle("  Take Offers From Party Chat", () -> c.hunterTradesAcceptShared, v -> c.hunterTradesAcceptShared = v)
                            .desc("Offers other players post in the same format land in your list too (critterMod compatible)");
                    b.toggle("  Waypoint Boxes", () -> c.hunterTradesWaypoints, v -> c.hunterTradesWaypoints = v);
                });
    }

    private static int sampleWidth() {
        int w = 0;
        for (String s : SAMPLE) w = Math.max(w, Minecraft.getInstance().font.width(s));
        return w;
    }

    private static void reset() {
        pending.clear();
        offerSpots.clear();
        lastSeen.clear();
        announced.clear();
        found.clear();
        lines = List.of();
        drawn = List.of();
        labels = List.of();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        if (++ticks < 20) return;
        ticks = 0;
        here = SafariData.inSafari();
        if (!here || mc.level == null) {
            lines = List.of();
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            String n = nameOf(e);
            if (n != null && (n.startsWith("Hunter ") || n.startsWith("Huntress "))) lastSeen.put(n, e.blockPosition());
        }
        buildLines();
    }

    private static void buildLines() {
        List<String> lb = new ArrayList<>();
        for (Trade t : found) lb.add(t.critter().name() + " §7← §e" + t.item());
        labels = lb;
        drawn = new ArrayList<>(found);
        if (found.isEmpty()) {
            lines = List.of();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<Trade> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingDouble(HunterTrades::distance));
        List<String> out = new ArrayList<>();
        out.add("§6Hunter Trades");
        for (Trade t : sorted) {
            out.add(rarity(t.critter()) + t.critter().name() + " §7← §e" + t.item());
            if (t.pos() == null) out.add("§8  " + t.npc());
            else {
                double d = distance(t);
                out.add("§8  " + (t.biome() == null ? "" : t.biome().label + " ") + t.pos().getX() + " " + t.pos().getY() + " "
                        + t.pos().getZ() + (d < Double.MAX_VALUE ? " · " + Math.round(d) + "m" : ""));
            }
        }
        int w = 0;
        for (String s : out) w = Math.max(w, mc.font.width(s));
        width = w;
        lines = out;
    }

    private static double distance(Trade t) {
        var p = Minecraft.getInstance().player;
        if (t.pos() == null || p == null) return Double.MAX_VALUE;
        return Math.sqrt(p.distanceToSqr(t.pos().getX() + 0.5, t.pos().getY() + 0.5, t.pos().getZ() + 0.5));
    }

    private static void onChat(String raw) {
        PlayerChat.Msg pm = PlayerChat.current();
        if (pm != null && pm.channel() == PlayerChat.Channel.PARTY) {
            if (!pm.own()) acceptShared(StringUtil.stripTrim(pm.message()));
            return;
        }
        if (!StringUtil.isServerMessage(raw)) return;
        String line = StringUtil.stripTrim(raw);
        if (takeTrade(line)) return;
        Matcher npc = NPC_LINE.matcher(line);
        if (!npc.matches()) return;
        String name = npc.group(1).trim();
        String text = npc.group(2);
        Matcher price = PRICE.matcher(text);
        if (price.find()) {
            SafariData.Critter critter = pending.remove(name);
            String item = price.group(1).trim();
            if (critter == null || item.isEmpty()) return;
            onOffer(name, critter, item);
            return;
        }
        if (text.contains("Shard")) {
            SafariData.Critter critter = findIn(text);
            if (critter != null) {
                pending.put(name, critter);
                BlockPos at = locate(name);
                if (at != null) offerSpots.put(name, at);
            }
        }
    }

    private static void onOffer(String npc, SafariData.Critter critter, String item) {
        String key = npc + "|" + critter.name() + "|" + item;
        long now = System.currentTimeMillis();
        Long last = announced.get(key);
        if (last != null && now - last < REPEAT_MS) return;
        announced.put(key, now);
        BlockPos pos = offerSpots.remove(npc);
        SafariData.Biome biome = pos == null ? null : SafariData.biomeAt(pos);
        if (pos != null && biome == null) biome = SafariData.currentBiome();
        record(new Trade(npc, critter, item, pos, biome));
        String where = pos == null ? "" : " (" + (biome == null ? "" : biome.label + " ") + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ")";
        var p = Minecraft.getInstance().player;
        if (cfg().hunterTradesChat && p != null) p.sendSystemMessage(Component.literal("§a[TeslaMaps] §b" + npc + where + ": "
                + rarity(critter) + critter.name() + " Shard §7for §e" + item));
        Announce.send(cfg().hunterTradesChannel, npc + where + ": " + critter.name() + " Shard for " + item);
    }

    private static boolean takeTrade(String line) {
        Matcher m = TRADE_TAKEN.matcher(line);
        if (!m.matches()) {
            m = SHARD_RECEIVED.matcher(line);
            if (!m.matches()) return false;
        }
        SafariData.Critter critter = SafariData.byName(m.group(1));
        if (critter == null) return false;
        if (drop(critter)) Announce.send(cfg().hunterTradesChannel, "Trade taken: " + critter.name() + " Shard");
        return true;
    }

    private static void acceptShared(String said) {
        Matcher taken = SHARED_TAKEN.matcher(said);
        if (taken.matches()) {
            SafariData.Critter c = SafariData.byName(taken.group(1));
            if (c != null) drop(c);
            return;
        }
        if (!cfg().hunterTradesAcceptShared) return;
        Matcher m = SHARED_TRADE.matcher(said);
        if (!m.matches()) return;
        SafariData.Critter critter = SafariData.byName(m.group(6));
        if (critter == null) return;
        SafariData.Biome biome = null;
        if (m.group(2) != null) for (SafariData.Biome b : SafariData.Biome.values()) if (b.label.equalsIgnoreCase(m.group(2))) biome = b;
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)));
            record(new Trade(m.group(1), critter, m.group(7), pos, biome));
        } catch (NumberFormatException ignored) {}
    }

    private static boolean drop(SafariData.Critter critter) {
        boolean any = found.removeIf(t -> t.critter().equals(critter));
        if (any) buildLines();
        return any;
    }

    private static void record(Trade t) {
        found.removeIf(o -> o.npc().equals(t.npc()));
        found.add(t);
        while (found.size() > MAX) found.removeFirst();
        buildLines();
    }

    private static SafariData.Critter findIn(String text) {
        SafariData.Critter best = null;
        for (SafariData.Critter c : SafariData.ALL) {
            if (text.contains(c.name() + " Shard") && (best == null || c.name().length() > best.name().length())) best = c;
        }
        return best;
    }

    private static BlockPos locate(String npc) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                String n = nameOf(e);
                if (n != null && n.contains(npc)) return e.blockPosition();
            }
        }
        for (Map.Entry<String, BlockPos> s : lastSeen.entrySet()) if (s.getKey().contains(npc)) return s.getValue();
        return mc.player.blockPosition();
    }

    private static String nameOf(Entity e) {
        if (!e.hasCustomName()) return null;
        Component n = e.getCustomName();
        if (n == null) return null;
        String t = COLOR.matcher(n.getString()).replaceAll("").trim();
        return t.isEmpty() ? null : t;
    }

    private static String rarity(SafariData.Critter c) {
        return switch (c.rarity()) {
            case LEGENDARY -> "§6";
            case EPIC -> "§5";
            case RARE -> "§9";
            case UNCOMMON -> "§a";
            case COMMON -> "§f";
        };
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Trade> ts = drawn;
        List<String> lb = labels;
        if (!cfg().hunterTradesWaypoints || ts.isEmpty() || ts.size() != lb.size() || !here) return;
        for (int i = 0, n = ts.size(); i < n; i++) {
            Trade t = ts.get(i);
            if (t.pos() == null) continue;
            WaypointSet.draw(pose, cam, t.pos(), 0xFFFFAA00, lb.get(i), false, true, true);
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || el == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        DungeonInfoHud.draw(g, mc, el.x(), el.y(), el.scale(), l, width);
    }
}
