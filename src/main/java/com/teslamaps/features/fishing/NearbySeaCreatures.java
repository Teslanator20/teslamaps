package com.teslamaps.features.fishing;

// Ported from SkyHanni's SeaCreatureFeatures (alertOtherCatches) + MobHealthDisplay, echo counter from GanKura
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.RareSeaCreature;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SeaCreatureList;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Sea creature nametags near you: a title for other players' rare ones, and an HP list. */
public final class NearbySeaCreatures {

    private static final class Tracked {
        final String name;
        final SeaCreatureList.Info info;
        final boolean own;
        String health = "";

        Tracked(String name, SeaCreatureList.Info info, boolean own) {
            this.name = name;
            this.info = info;
            this.own = own;
        }
    }

    private static final Pattern HEALTH = Pattern.compile("([\\d.,]+[kKmMbB]?)/([\\d.,]+[kKmMbB]?)❤");
    private static final List<String> SAMPLE = List.of("§3§lSea Creature Health",
            "§aThunder §a35M§f/§a35M §c❤", "§cLord Jawbus §e40M§f/§a100M §c❤");
    private static final long ECHO_MS = 5_000;
    private static final long DEDUPE_MS = 6 * 60_000;

    private static final Map<Integer, Tracked> tracked = new LinkedHashMap<>();
    private static final Set<Integer> ignored = new HashSet<>();
    private static final Map<Integer, Long> alerted = new HashMap<>();
    private static final Map<String, Integer> echo = new HashMap<>();
    private static long echoAt;
    private static long doubleHookAt;
    private static List<String> names = List.of();
    private static int namesSize = -1;
    private static java.lang.ref.WeakReference<net.minecraft.world.level.Level> lastLevel = new java.lang.ref.WeakReference<>(null);
    private static int ticks;
    private static List<String> lines = List.of();
    private static int width;
    private static HudEditElement hud;

    private NearbySeaCreatures() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean alertOn() { return cfg().rareCatchAlert && cfg().rareCatchAlertOthers; }

    public static void register() {
        hud = HudEditElement.of("seaCreatureHealthHud", "Sea Creature Health").section("Fishing")
                .enabled(() -> cfg().seaCreatureHealthHud)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height((lines.isEmpty() ? SAMPLE : lines).size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("NearbySeaCreatures").section("Fishing")
                .when(() -> alertOn() || cfg().seaCreatureHealthHud)
                .tick(NearbySeaCreatures::tick)
                .serverChat(NearbySeaCreatures::onChat)
                .hudHiddenInInventory("seacreaturehealth", NearbySeaCreatures::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Sea Creature Health");
                    b.toggle("Sea Creature Health HUD", () -> c.seaCreatureHealthHud, v -> c.seaCreatureHealthHud = v)
                            .desc("Living sea creatures near you with their HP. Green name = yours, red = someone else's. "
                                    + "Position in /tmap gui")
                            .tags("sea creature", "health", "hp", "jawbus", "thunder", "hud");
                    b.toggle("  Only Rare Ones", () -> c.seaCreatureHealthRareOnly, v -> c.seaCreatureHealthRareOnly = v)
                            .desc("Uses Counts As Rare and the per-creature alert picks");
                    b.slider("  Limit", 1, 20, () -> (float) c.seaCreatureHealthLimit, v -> c.seaCreatureHealthLimit = Math.round(v), 0);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        long now = System.currentTimeMillis();
        if (t.startsWith("It's a Double Hook!") || t.equals("Double Hook!")) {
            doubleHookAt = now;
            return;
        }
        String name = SeaCreatureList.nameFor(t);
        if (name == null) return;
        if (now - echoAt > ECHO_MS) echo.clear();
        echo.merge(name, now - doubleHookAt < 2_000 ? 2 : 1, Integer::sum);
        echoAt = now;
        doubleHookAt = 0;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel.get()) {
            lastLevel = new java.lang.ref.WeakReference<>(mc.level);
            tracked.clear();
            ignored.clear();
            alerted.clear();
            echo.clear();
            lines = List.of();
        }
        if (++ticks % 4 != 0) return;
        if (!SeaCreatureList.ready()) return;
        if (namesSize != SeaCreatureList.size()) {
            namesSize = SeaCreatureList.size();
            List<String> n = new ArrayList<>(SeaCreatureList.names());
            n.sort((a, b) -> b.length() - a.length());
            names = n;
        }
        long now = System.currentTimeMillis();
        alerted.values().removeIf(at -> now - at > DEDUPE_MS);

        for (ArmorStand s : EntityCache.namedArmorStands()) {
            int id = s.getId();
            if (s.tickCount < 5 || tracked.containsKey(id) || ignored.contains(id) || s.getCustomName() == null) continue;
            String plain = StringUtil.stripTrim(s.getCustomName().getString());
            if (plain.indexOf('❤') < 0) continue;
            String name = match(plain);
            if (name == null) {
                ignored.add(id);
                continue;
            }
            SeaCreatureList.Info info = SeaCreatureList.info(name);
            boolean own = consumeEcho(name, now);
            Tracked t = new Tracked(name, info, own);
            tracked.put(id, t);
            if (!own && info != null && alertOn() && !alerted.containsKey(id) && !hydraSecondHalf(name, plain)) {
                alerted.put(id, now);
                if (cfg().seaCreatureAlert.getOrDefault(name, RareSeaCreature.ruleRare(name))) alert(mc, info);
            }
        }

        for (Iterator<Map.Entry<Integer, Tracked>> it = tracked.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Tracked> e = it.next();
            Entity ent = mc.level.getEntity(e.getKey());
            if (ent == null || ent.isRemoved() || ent.getCustomName() == null) {
                it.remove();
                continue;
            }
            if (!cfg().seaCreatureHealthHud) continue;
            Matcher m = HEALTH.matcher(StringUtil.stripTrim(ent.getCustomName().getString()));
            e.getValue().health = m.find() ? colorHealth(m.group(1), m.group(2)) : "";
        }
        buildLines();
    }

    private static String match(String plain) {
        for (String n : names) if (plain.contains(n)) return n;
        return null;
    }

    private static boolean consumeEcho(String name, long now) {
        if (now - echoAt > ECHO_MS) return false;
        Integer left = echo.get(name);
        if (left == null || left <= 0) return false;
        if (left == 1) echo.remove(name);
        else echo.put(name, left - 1);
        return true;
    }

    // SkyHanni skips the Hydra that splits off at half HP
    private static boolean hydraSecondHalf(String name, String plain) {
        if (!name.equals("Water Hydra")) return false;
        Matcher m = HEALTH.matcher(plain);
        if (!m.find()) return false;
        double cur = parse(m.group(1)), max = parse(m.group(2));
        return max > 0 && Math.abs(cur - max / 2) <= max * 0.01;
    }

    private static void alert(Minecraft mc, SeaCreatureList.Info info) {
        if (mc.player == null) return;
        TeslaMapsConfig c = cfg();
        String title = c.rareCatchName ? info.color() + "§l" + info.name() + " NEARBY!" : info.color() + "§lRARE SEA CREATURE!";
        mc.gui.setTimes(0, 30, 5);
        mc.gui.setTitle(Component.literal(title));
        mc.gui.setSubtitle(Component.literal(""));
        if (c.rareCatchVolume > 0f) LoudSound.play(SoundOptions.resolve(c.rareCatchSound), c.rareCatchVolume, 1.4f);
    }

    private static void buildLines() {
        TeslaMapsConfig c = cfg();
        if (!c.seaCreatureHealthHud || tracked.isEmpty()) {
            lines = List.of();
            return;
        }
        int limit = Math.max(1, c.seaCreatureHealthLimit);
        List<String> out = new ArrayList<>(limit + 1);
        out.add("§3§lSea Creature Health");
        for (Tracked t : tracked.values()) {
            if (t.health.isEmpty()) continue;
            if (c.seaCreatureHealthRareOnly && !c.seaCreatureAlert.getOrDefault(t.name, RareSeaCreature.ruleRare(t.name))) continue;
            out.add((t.own ? "§a" : "§c") + t.name + " " + t.health);
            if (out.size() > limit) break;
        }
        if (out.size() == 1) {
            lines = List.of();
            return;
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static String colorHealth(String current, String max) {
        double now = parse(current), full = parse(max);
        String color = "§a";
        if (full > 0) {
            double f = now / full;
            if (f < 0.25) color = "§c";
            else if (f < 0.5) color = "§6";
            else if (f < 0.75) color = "§e";
        }
        return color + current + "§f/§a" + max + " §c❤";
    }

    private static double parse(String s) {
        try {
            s = s.replace(",", "");
            if (s.isEmpty()) return 0;
            double mult = 1;
            char last = Character.toLowerCase(s.charAt(s.length() - 1));
            if (last == 'k') mult = 1e3;
            else if (last == 'm') mult = 1e6;
            else if (last == 'b') mult = 1e9;
            if (mult != 1) s = s.substring(0, s.length() - 1);
            return Double.parseDouble(s) * mult;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty() || !cfg().seaCreatureHealthHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
