package com.teslamaps.features.fishing;

// Ported from Skyblocker's SeaCreatureTracker.java + FishingHudWidget.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SeaCreatureList;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Own living sea creatures: the catch line is paired with the nametag that spawns right after it. */
public final class LiveSeaCreatures {

    public static final class Live {
        final String name;
        final SeaCreatureList.Info info;
        final long spawnedAt;
        double x, y, z;

        Live(String name, SeaCreatureList.Info info, long spawnedAt) {
            this.name = name;
            this.info = info;
            this.spawnedAt = spawnedAt;
        }
    }

    private record Pending(String name, long at) {}

    private static final List<String> SAMPLE = List.of("§3§lSea Creatures", "§7Alive: §e3§7/§e10 §a■■■§8■■■■■■■",
            "§7Despawn: §e3m 12s");
    private static final long PENDING_MS = 3_000;
    private static final double BARN_X = 108, BARN_Z = -252;

    private static final Map<Integer, Live> alive = new LinkedHashMap<>();
    private static final List<Pending> pending = new ArrayList<>();
    private static long doubleHookAt;
    private static java.lang.ref.WeakReference<net.minecraft.world.level.Level> lastLevel = new java.lang.ref.WeakReference<>(null);
    private static boolean timerWarned;
    private static List<String> lines = List.of();
    private static int width;
    private static HudEditElement hud;
    private static Feature feature;
    /** Own creatures that died near you, for the Dolphin milestone estimate. */
    public static int killsSinceLoad;

    private LiveSeaCreatures() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("liveSeaCreatures", "Alive Sea Creatures").section("Fishing")
                .enabled(() -> cfg().liveSeaCreatures)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height((lines.isEmpty() ? SAMPLE : lines).size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        feature = Feature.of("LiveSeaCreatures").section("Fishing")
                .when(() -> cfg().liveSeaCreatures || cfg().fishingProgressHud && cfg().fishingProgressDolphin)
                .tick(LiveSeaCreatures::tick)
                .serverChat(LiveSeaCreatures::onChat)
                .hudHiddenInInventory("liveseacreatures", LiveSeaCreatures::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Alive Sea Creatures");
                    b.toggle("Alive Sea Creature Counter", () -> c.liveSeaCreatures, v -> c.liveSeaCreatures = v)
                            .desc("Your own living sea creatures (n/cap) and the time until the oldest despawns. "
                                    + "Pairs your catch message with the nametag that spawns. Position in /tmap gui")
                            .tags("sea creature", "cap", "despawn", "barn", "alive", "timer");
                    b.slider("  Cap", 1, 60, () -> (float) c.liveSeaCreaturesCap, v -> c.liveSeaCreaturesCap = Math.round(v), 0);
                    b.slider("  Despawn Timer (s)", 30, 600, () -> (float) c.liveSeaCreaturesTimer, v -> c.liveSeaCreaturesTimer = Math.round(v), 0);
                    b.toggle("  Cap Reached Alert", () -> c.liveSeaCreaturesCapAlert, v -> c.liveSeaCreaturesCapAlert = v);
                    b.toggle("  Timer Ran Out Alert", () -> c.liveSeaCreaturesTimerAlert, v -> c.liveSeaCreaturesTimerAlert = v);
                    b.dropdown("  Alert From Rarity", new String[] {"Off", "Rare", "Epic", "Legendary", "Mythic"},
                            () -> c.liveSeaCreaturesRarity, v -> c.liveSeaCreaturesRarity = v);
                    b.toggle("  Hub: Only In The Barn", () -> c.liveSeaCreaturesBarnOnly, v -> c.liveSeaCreaturesBarnOnly = v);
                });
    }

    public static int count() { return alive.size(); }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        long now = System.currentTimeMillis();
        if (t.startsWith("It's a Double Hook!") || t.equals("Double Hook!")) {
            doubleHookAt = now;
            return;
        }
        String name = SeaCreatureList.nameFor(t);
        if (name == null) return;
        pending.add(new Pending(name, now));
        if (now - doubleHookAt < 2_000) pending.add(new Pending(name, now));
        doubleHookAt = 0;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.level != lastLevel.get()) {
            lastLevel = new java.lang.ref.WeakReference<>(mc.level);
            alive.clear();
            pending.clear();
        }
        pending.removeIf(p -> now - p.at > PENDING_MS);
        if (!pending.isEmpty()) {
            for (ArmorStand s : EntityCache.namedArmorStands()) {
                if (pending.isEmpty()) break;
                if (s.isRemoved() || s.tickCount > 60 || alive.containsKey(s.getId())) continue;
                if (s.distanceToSqr(mc.player) > 900 || s.getCustomName() == null) continue;
                String tag = s.getCustomName().getString();
                for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
                    Pending p = it.next();
                    if (!tag.contains(p.name)) continue;
                    it.remove();
                    Live l = new Live(p.name, SeaCreatureList.info(p.name), now);
                    l.x = s.getX(); l.y = s.getY(); l.z = s.getZ();
                    alive.put(s.getId(), l);
                    onSpawn(mc, l);
                    break;
                }
            }
        }
        int timer = Math.max(1, cfg().liveSeaCreaturesTimer) * 1000;
        for (Iterator<Map.Entry<Integer, Live>> it = alive.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Live> e = it.next();
            Live l = e.getValue();
            var ent = mc.level.getEntity(e.getKey());
            if (ent == null || ent.isRemoved()) {
                if (now - l.spawnedAt < timer && mc.player.distanceToSqr(l.x, l.y, l.z) < 576) killsSinceLoad++;
                it.remove();
            } else {
                l.x = ent.getX(); l.y = ent.getY(); l.z = ent.getZ();
            }
        }
        if (alive.isEmpty()) {
            timerWarned = false;
            lines = List.of();
            return;
        }
        long oldest = alive.values().iterator().next().spawnedAt;
        long left = timer - (now - oldest);
        if (left <= 0 && !timerWarned) {
            timerWarned = true;
            if (cfg().liveSeaCreatures && cfg().liveSeaCreaturesTimerAlert) alert(mc, "§cSea creature timer ran out!");
        } else if (left > 0) {
            timerWarned = false;
        }
        int cap = Math.max(1, cfg().liveSeaCreaturesCap);
        int n = alive.size();
        StringBuilder bar = new StringBuilder(" ");
        int filled = Math.min(10, Math.round(n * 10f / cap));
        bar.append(n >= cap ? "§c" : n * 2 >= cap ? "§e" : "§a");
        for (int i = 0; i < 10; i++) {
            if (i == filled) bar.append("§8");
            bar.append('■');
        }
        List<String> out = new ArrayList<>(3);
        out.add("§3§lSea Creatures");
        out.add("§7Alive: " + (n >= cap ? "§c" : "§e") + n + "§7/§e" + cap + bar);
        out.add("§7Despawn: " + (left <= 0 ? "§cnow" : "§e" + SeaCreatureTracker.duration(left)));
        lines = out;
        width = TextHud.width(out);
    }

    private static void onSpawn(Minecraft mc, Live l) {
        TeslaMapsConfig c = cfg();
        if (!c.liveSeaCreatures) return;
        if (c.liveSeaCreaturesCapAlert && alive.size() == Math.max(1, c.liveSeaCreaturesCap)) {
            alert(mc, "§cSea creature cap reached!");
            return;
        }
        int min = rank(c.liveSeaCreaturesRarity.toUpperCase(java.util.Locale.ROOT));
        if (min > 0 && l.info != null && rank(l.info.rarity()) >= min) alert(mc, l.info.color() + l.name);
    }

    private static int rank(String rarity) {
        return switch (rarity) {
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            case "UNCOMMON" -> 1;
            default -> 0;
        };
    }

    private static void alert(Minecraft mc, String title) {
        if (mc.player == null) return;
        mc.gui.setTimes(0, 50, 10);
        mc.gui.setTitle(Component.literal(title));
        mc.gui.setSubtitle(Component.literal(""));
        mc.player.playSound(SoundEvents.ARROW_HIT_PLAYER, 1f, 0.1f);
    }

    private static boolean shown(Minecraft mc) {
        if (!cfg().liveSeaCreaturesBarnOnly || !SkyblockUtils.getArea().equalsIgnoreCase("Hub")) return true;
        double dx = mc.player.getX() - BARN_X, dz = mc.player.getZ() - BARN_Z;
        return dx * dx + dz * dz < 2500;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty() || !cfg().liveSeaCreatures) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !shown(mc)) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
