package com.teslamaps.features.mining;

// After SkyHanni's MiningEventTracker/MiningEventDisplay (local lobby only) and Skytils' raffle warning + raffle box
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Active mining events of this lobby with time left, Star Cult countdown, raffle helpers. */
public final class MiningEvents {

    private static final Pattern BAR = Pattern.compile("^(?:PASSIVE )?EVENT (.+?) (?:ACTIVE IN .+? |RUNNING )[Ff][Oo][Rr] (\\d+):(\\d+)$");
    private static final Pattern STARTED = Pattern.compile("^([A-Z0-9 ]+) STARTED!$");
    private static final Pattern ENDED = Pattern.compile("^([A-Z0-9 ]+) ENDED!$");
    private static final long SB_EPOCH = 1560275700L;
    private static final String[] SAMPLE = {"§6§lMining Events", "§bMithril Gourmand §f8:12", "§92x Powder §f3:40", "§bStar Cult §7in 1h 4m"};

    // event -> {display, default length s}
    private static final Map<String, Object[]> KNOWN = Map.of(
            "GONE WITH THE WIND", new Object[]{"§9Gone With The Wind", 18 * 60},
            "2X POWDER", new Object[]{"§b2x Powder", 15 * 60},
            "GOBLIN RAID", new Object[]{"§cGoblin Raid", 5 * 60},
            "BETTER TOGETHER", new Object[]{"§dBetter Together", 18 * 60},
            "RAFFLE", new Object[]{"§6Raffle", 160},
            "MITHRIL GOURMAND", new Object[]{"§bMithril Gourmand", 10 * 60});

    private static final Map<String, Long> active = new LinkedHashMap<>();
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, ticks;
    private static boolean on;
    private static Object lastLevel;
    private static BlockPos lastJukebox, raffleBox;
    private static long lastWarn;

    private MiningEvents() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("miningEvents", "Mining Events").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().miningEvents)
                .size(() -> Math.max(90, Math.max(width, width(SAMPLE))), () -> Math.max(SAMPLE.length, lines.size()) * 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? List.of(SAMPLE) : lines));
        Feature.of("MiningEvents").section("Mining").when(() -> cfg().miningEvents)
                .tick(MiningEvents::tick)
                .serverChat(MiningEvents::onChat)
                .hud("miningevents", MiningEvents::render)
                .world(MiningEvents::renderWorld)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Mining Events Display", () -> c.miningEvents, v -> c.miningEvents = v)
                            .desc("Mining events running in this lobby (2x Powder, Goblin Raid, Raffle, Gourmand, ...) with time left, read from the boss bar and chat. Move it in /tmap gui.")
                            .tags("event", "2x powder", "goblin raid", "raffle", "gourmand", "better together", "wind", "star cult");
                    b.toggle("  Star Cult Countdown", () -> c.miningEventsStarCult, v -> c.miningEventsStarCult = v)
                            .desc("Cult of the Fallen Star: 7th, 14th, 21st and 28th of every SkyBlock month");
                    b.toggle("  Event Ending Warning", () -> c.miningEventsWarn, v -> c.miningEventsWarn = v)
                            .desc("Title while Raffle or Mithril Gourmand is about to end");
                    b.slider("    Warn At (seconds)", 5, 60, () -> (float) c.miningEventsWarnSeconds, v -> c.miningEventsWarnSeconds = Math.round(v), 0);
                    b.toggle("  Raffle Box Waypoint", () -> c.miningEventsRaffleBox, v -> c.miningEventsRaffleBox = v)
                            .desc("Marks the jukebox you put your tickets in until the raffle ends");
                    b.toggle("  Mute Wind Change Messages", () -> c.miningEventsMuteWind, v -> c.miningEventsMuteWind = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            active.clear();
            raffleBox = null;
            lastJukebox = null;
        }
        if (++ticks % 10 != 0) return;
        on = Mining.onMiningIsland();
        if (!on) { lines = List.of(); return; }
        long now = System.currentTimeMillis();
        var events = ((com.teslamaps.mixin.BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).teslamaps$getEvents();
        if (events != null) {
            for (LerpingBossEvent ev : events.values()) {
                if (ev.getName() == null) continue;
                Matcher m = BAR.matcher(StringUtil.stripTrim(ev.getName().getString()));
                if (!m.matches()) continue;
                long left = (Integer.parseInt(m.group(2)) * 60L + Integer.parseInt(m.group(3))) * 1000L;
                active.put(m.group(1).trim(), now + left);
            }
        }
        for (Iterator<Map.Entry<String, Long>> it = active.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue() < now) it.remove();
        }
        warn(mc, now);

        List<String> out = new ArrayList<>();
        out.add("§6§lMining Events");
        for (Map.Entry<String, Long> e : active.entrySet()) {
            out.add(name(e.getKey()) + " §f" + clock(e.getValue() - now));
        }
        if (cfg().miningEventsStarCult) out.add(starCult());
        if (out.size() == 1) out.add("§7No active event");
        lines = out;
        width = width(out.toArray(new String[0]));
    }

    private static void warn(Minecraft mc, long now) {
        if (!cfg().miningEventsWarn) return;
        for (String ev : new String[]{"RAFFLE", "MITHRIL GOURMAND"}) {
            Long end = active.get(ev);
            if (end == null) continue;
            long left = (end - now + 999) / 1000;
            if (left <= 0 || left > cfg().miningEventsWarnSeconds || now - lastWarn < 900) continue;
            lastWarn = now;
            mc.gui.hud.setTimes(0, 25, 0);
            mc.gui.hud.setTitle(Component.literal("§c" + StringUtil.strip(name(ev)) + " ending in §a" + left + "s"));
            mc.gui.hud.setSubtitle(Component.empty());
        }
    }

    private static String starCult() {
        long sec = System.currentTimeMillis() / 1000L - SB_EPOCH;
        long day = sec / 1200;
        long inDay = sec % 1200;
        for (int d = 0; d < 32; d++) {
            long dom = (day + d) % 31 + 1;
            if (dom % 7 != 0) continue;
            if (d == 0 && inDay < 300) return "§bStar Cult §aACTIVE §f" + clock((300 - inDay) * 1000);
            if (d == 0) continue;
            return "§bStar Cult §7in " + span(((day + d) * 1200 - sec) * 1000);
        }
        return "§bStar Cult §7?";
    }

    private static String name(String ev) {
        Object[] k = KNOWN.get(ev);
        if (k != null) return (String) k[0];
        StringBuilder sb = new StringBuilder("§e");
        for (String w : ev.toLowerCase().split(" ")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 2) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static void onChat(String text) {
        if (!on) return;
        String t = StringUtil.stripTrim(text);
        Matcher m = STARTED.matcher(t);
        if (m.matches() && KNOWN.containsKey(m.group(1).trim())) {
            String ev = m.group(1).trim();
            active.putIfAbsent(ev, System.currentTimeMillis() + ((Integer) KNOWN.get(ev)[1]) * 1000L);
            return;
        }
        m = ENDED.matcher(t);
        if (m.matches()) {
            String ev = m.group(1).trim();
            active.remove(ev);
            if (ev.equals("RAFFLE")) raffleBox = null;
            return;
        }
        if (lastJukebox != null && ((t.startsWith("You registered ") && t.endsWith("in the raffle event!"))
                || t.equals("No tickets to put in the box..."))) {
            raffleBox = lastJukebox;
        }
    }

    /** ClientPlayerInteractionManagerMixin, right-click on a jukebox. */
    public static void onJukeboxClick(BlockPos pos) {
        if (on && cfg().miningEvents && cfg().miningEventsRaffleBox) lastJukebox = pos.immutable();
    }

    /** From MiningChatHooks: wind compass spam. */
    public static boolean hideChat(String stripped) {
        TeslaMapsConfig c = cfg();
        return c.miningEvents && c.miningEventsMuteWind && on && stripped.contains("The wind has changed direction");
    }

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        if (raffleBox == null || !cfg().miningEventsRaffleBox || !active.containsKey("RAFFLE")) return;
        WaypointSet.draw(pose, cam, raffleBox, 0xFFAA00, "§6Raffle Box", false, true, true);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!on || lines.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || com.teslamaps.utils.HudVisibility.suppressed()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), lines);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> text) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        for (int i = 0; i < text.size(); i++) g.text(mc.font, text.get(i), 0, i * 10, 0xFFFFFFFF);
        pose.popMatrix();
    }

    private static int width(String[] text) {
        int w = 0;
        for (String s : text) w = Math.max(w, Minecraft.getInstance().font.width(s));
        return w;
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static String span(long ms) {
        long m = ms / 60000;
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }
}
