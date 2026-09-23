package com.teslamaps.features.foraging;

// Ported from Skysoft's HoneyhiveHelper.kt / HoneyhiveDisplay.kt
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Every Torrhus Canyon honeyhive with its own refill time, read off the hive nametags. */
public final class HoneyhiveHelper {

    static final class Hive {
        int x, y, z;
        long readyAt;
        boolean observed;

        Hive() {}

        Hive(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        String key() { return x + ":" + y + ":" + z; }
        boolean known() { return observed; }
        boolean ready(long now) { return observed && readyAt <= now; }
    }

    private record Row(int number, Hive hive, String status) {}

    private static final int[][] KNOWN = {
            {-696, 93, 150}, {-693, 94, 147}, {-693, 93, 153}, {-664, 96, 170}, {-665, 97, 167},
            {-724, 93, 211}, {-721, 92, 207}, {-724, 92, 204}, {-707, 92, 220}, {-705, 92, 225},
            {-611, 98, 274}, {-606, 98, 275}, {-572, 101, 206}, {-577, 102, 205}, {-588, 150, 257},
            {-581, 152, 258}, {-578, 151, 256},
    };
    private static final String NAME = "Honeyhive";
    private static final String LOOT = "Click to loot!";
    private static final String REFILL = "Refill in: ";
    private static final Pattern PART = Pattern.compile("(\\d+)([hms])");
    private static final double ARRIVAL_SQ = 12.0 * 12.0;
    private static final int ROW_H = 10;
    private static final int COLOR = 0xE6FFAA00;
    private static final int FILL = 0x46FFAA00;
    private static ItemStack icon;

    private static final Gson GSON = new Gson();
    private static final ProfiledFile FILE = new ProfiledFile("honeyhives.json", null);
    private static List<Hive> hives = new ArrayList<>();
    private static boolean loaded, dirty;
    private static Path path;

    private static final Set<String> selected = new HashSet<>();
    private static final Set<String> dismissedReady = new HashSet<>();
    private static final Set<String> alerted = new HashSet<>();
    private static int ticks, soundsLeft, soundDelay;

    private static List<Row> rows = List.of();
    private static int total, scroll;
    private static int hudW = 80, hudH = 20;

    private HoneyhiveHelper() {}

    public static void register() {
        Feature.of("HoneyhiveHelper").section("Foraging").when(() -> cfg().honeyhiveHelper)
                .tick(HoneyhiveHelper::tick)
                .world(HoneyhiveHelper::render)
                .hudForeground("honeyhives", HoneyhiveHelper::renderHud);
        HudEditElement.of("honeyhive", "Honeyhives").ctx(HudEditElement.HudCtx.FORAGING).section("Foraging")
                .enabled(() -> cfg().honeyhiveHelper && cfg().honeyhiveDisplay)
                .size(() -> hudW, () -> hudH)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, null));
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> covered(s, event.x(), event.y())
                    || !onClick(event.x(), event.y(), event.button()));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h2, v) -> covered(s, mx, my) || !onScroll(mx, my, v));
        });
    }

    private static boolean covered(net.minecraft.client.gui.screens.Screen s, double mx, double my) {
        if (!(s instanceof com.teslamaps.mixin.HandledScreenAccessor a)) return false;
        return mx >= a.getX() && mx < a.getX() + a.getImageWidth() && my >= a.getY() && my < a.getY() + a.getImageHeight();
    }

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    // --- storage ---

    private static void ensure() {
        if (!loaded) load();
        else if (FILE.stale()) {
            save();
            load();
        }
    }

    private static void load() {
        hives = new ArrayList<>();
        selected.clear();
        dismissedReady.clear();
        alerted.clear();
        dirty = false;
        path = FILE.path();
        try {
            if (path != null && Files.exists(path)) {
                List<Hive> read = GSON.fromJson(Files.readString(path), new TypeToken<List<Hive>>() {}.getType());
                if (read != null) for (Hive h : read) if (h != null) hives.add(h);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load honeyhives.json", e);
        }
        for (int[] k : KNOWN) if (find(k[0], k[1], k[2]) == null) hives.add(new Hive(k[0], k[1], k[2]));
        loaded = true;
    }

    private static void save() {
        if (!dirty || path == null) return;
        dirty = false;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(hives));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save honeyhives.json", e);
        }
    }

    private static Hive find(int x, int y, int z) {
        for (Hive h : hives) if (h.x == x && h.y == y && h.z == z) return h;
        return null;
    }

    // --- tick ---

    private static void tick() {
        ensure();
        long now = System.currentTimeMillis();
        boolean canyon = Foraging.inTorrhus();
        ticks++;
        if (canyon) {
            if (ticks % 5 == 0) scan(now);
            clearReached(now);
            alertReady(now);
            playSound();
        } else {
            soundsLeft = 0;
        }
        if (dirty && ticks % 100 == 0) save();
        if (ticks % 10 == 0) buildRows(now);
    }

    private static void scan(long now) {
        List<ArmorStand> named = com.teslamaps.utils.EntityCache.namedArmorStands();
        List<ArmorStand> statusStands = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        for (ArmorStand as : named) {
            if (as.getCustomName() == null) continue;
            String n = StringUtil.strip(as.getCustomName().getString()).trim();
            if (n.equals(LOOT) || n.startsWith(REFILL)) {
                statusStands.add(as);
                statuses.add(n);
            }
        }
        if (statusStands.isEmpty()) return;
        for (ArmorStand tag : named) {
            if (tag.getCustomName() == null || !StringUtil.strip(tag.getCustomName().getString()).trim().equals(NAME)) continue;
            String status = null;
            double best = 0.25;
            for (int i = 0; i < statusStands.size(); i++) {
                double d = statusStands.get(i).distanceToSqr(tag);
                if (d <= best) {
                    best = d;
                    status = statuses.get(i);
                }
            }
            if (status == null) continue;
            long readyAt;
            if (status.equals(LOOT)) readyAt = 0;
            else {
                long ms = refillMillis(status.substring(REFILL.length()));
                if (ms < 0) continue;
                readyAt = now + ms;
            }
            BlockPos p = tag.blockPosition().above();
            Hive h = find(p.getX(), p.getY(), p.getZ());
            if (h == null) {
                h = new Hive(p.getX(), p.getY(), p.getZ());
                h.readyAt = readyAt;
                h.observed = true;
                hives.add(h);
                dirty = true;
            } else if (!h.observed || shouldUpdate(h.readyAt, readyAt, now)) {
                h.readyAt = readyAt;
                h.observed = true;
                dirty = true;
            }
        }
    }

    private static long refillMillis(String s) {
        if (s.isBlank()) return -1;
        long total = 0;
        for (String part : s.trim().split(" ")) {
            Matcher m = PART.matcher(part);
            if (!m.matches()) return -1;
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) {
                case "h" -> 3600L;
                case "m" -> 60L;
                default -> 1L;
            };
        }
        return total * 1000L;
    }

    private static boolean shouldUpdate(long current, long observed, long now) {
        return (current <= now) != (observed <= now) || observed > now && Math.abs(current - observed) > 2000;
    }

    private static void clearReached(long now) {
        Set<String> ready = new HashSet<>();
        for (Hive h : hives) if (h.ready(now)) ready.add(h.key());
        dismissedReady.retainAll(ready);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || selected.isEmpty()) return;
        for (Hive h : hives) {
            if (!selected.contains(h.key())) continue;
            if (mc.player.position().distanceToSqr(h.x + 0.5, h.y + 0.5, h.z + 0.5) <= ARRIVAL_SQ) dismiss(h, now);
        }
    }

    private static void alertReady(long now) {
        Set<String> ready = new HashSet<>();
        for (Hive h : hives) if (h.ready(now)) ready.add(h.key());
        alerted.retainAll(ready);
        boolean fresh = !alerted.containsAll(ready);
        alerted.addAll(ready);
        if (cfg().honeyhiveReadySound && fresh && soundsLeft == 0) {
            soundsLeft = 3;
            soundDelay = 0;
        }
    }

    private static void playSound() {
        if (!cfg().honeyhiveReadySound) soundsLeft = 0;
        if (soundsLeft == 0 || soundDelay-- > 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
        soundsLeft--;
        soundDelay = 4;
    }

    private static boolean hasWaypoint(Hive h, long now) {
        return selected.contains(h.key()) || cfg().honeyhiveReadyWaypoints && h.ready(now) && !dismissedReady.contains(h.key());
    }

    private static void toggle(Hive h) {
        long now = System.currentTimeMillis();
        if (hasWaypoint(h, now)) dismiss(h, now);
        else {
            selected.add(h.key());
            dismissedReady.remove(h.key());
        }
    }

    private static void dismiss(Hive h, long now) {
        selected.remove(h.key());
        if (h.ready(now)) dismissedReady.add(h.key());
    }

    // --- world ---

    private static void render(PoseStack pose, Vec3 cam) {
        if (!Foraging.inTorrhus()) return;
        long now = System.currentTimeMillis();
        for (Hive h : hives) {
            if (!hasWaypoint(h, now)) continue;
            AABB box = new AABB(h.x, h.y, h.z, h.x + 1, h.y + 1, h.z + 1);
            ESPRenderer.drawFilledBox(pose, box, FILL, cam, true);
            ESPRenderer.drawBoxOutline(pose, box, COLOR, 2f, cam, true);
            Vec3 at = new Vec3(h.x + 0.5, h.y + 2.0, h.z + 0.5);
            ESPRenderer.drawText(pose, "§6§lHoneyhive", at.add(0, 0.35, 0), 1f, cam, true, true);
            ESPRenderer.drawText(pose, status(h, now), at, 1f, cam, true, true);
        }
    }

    private static String status(Hive h, long now) {
        if (!h.known()) return "§7Unchecked";
        if (h.readyAt <= now) return "§aReady";
        long secs = (h.readyAt - now + 999) / 1000;
        long hours = secs / 3600, mins = (secs % 3600) / 60, s = secs % 60;
        StringBuilder sb = new StringBuilder("§e");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m ");
        return sb.append(s).append('s').toString();
    }

    // --- hud ---

    private static void buildRows(long now) {
        List<Row> all = new ArrayList<>();
        for (int i = 0; i < hives.size(); i++) all.add(new Row(i + 1, hives.get(i), status(hives.get(i), now)));
        all.sort((a, b) -> {
            if (a.hive.known() != b.hive.known()) return a.hive.known() ? -1 : 1;
            long ra = Math.max(0, a.hive.readyAt - now), rb = Math.max(0, b.hive.readyAt - now);
            if (ra != rb) return Long.compare(ra, rb);
            return Integer.compare(a.number, b.number);
        });
        int max = maxLines();
        total = all.size();
        scroll = Math.max(0, Math.min(scroll, total - max));
        rows = all.subList(scroll, Math.min(total, scroll + max));
        Font font = Minecraft.getInstance().font;
        int w = font.width("§6§lHoneyhives");
        for (Row r : rows) w = Math.max(w, 10 + font.width("§6Hive " + r.number) + 8 + font.width(r.status));
        hudW = w;
        hudH = ROW_H + rows.size() * ROW_H + (total > rows.size() ? ROW_H : 0);
    }

    private static int maxLines() {
        return Math.max(1, Math.min(30, (int) cfg().honeyhiveMaxLines));
    }

    private static boolean visible() {
        TeslaMapsConfig c = cfg();
        return c.honeyhiveDisplay && (c.honeyhiveDisplayOutside || Foraging.inTorrhus()) && !hives.isEmpty()
                && c.section("Foraging") && c.honeyhiveHelper;
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!visible() || com.teslamaps.utils.HudVisibility.suppressed()) return;
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        Hive hovered = null;
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
            double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
            if (!covered(mc.screen, mx, my)) hovered = hiveAt(mx, my);
        }
        draw(g, mc, c.honeyhiveX, c.honeyhiveY, c.honeyhiveScale, hovered);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, Hive hovered) {
        long now = System.currentTimeMillis();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int pad = HudStyle.padding();
        HudStyle.panel(g, -pad, -pad, hudW + pad * 2, hudH + pad * 2);
        g.text(mc.font, "§6§lHoneyhives", 0, 1, 0xFFFFFFFF);
        int ry = ROW_H;
        for (Row r : rows) {
            if (r.hive == hovered || hasWaypoint(r.hive, now)) g.fill(-1, ry - 1, hudW + 1, ry + ROW_H - 1, 0x40FFFFFF);
            pose.pushMatrix();
            pose.translate(0, ry);
            pose.scale(0.5f, 0.5f);
            if (icon == null) icon = new ItemStack(Items.BEE_NEST);
            g.item(icon, 0, 0);
            pose.popMatrix();
            g.text(mc.font, "§6Hive " + r.number, 10, ry, 0xFFFFFFFF);
            g.text(mc.font, r.status, hudW - mc.font.width(r.status), ry, 0xFFFFFFFF);
            ry += ROW_H;
        }
        if (total > rows.size()) {
            String more = "§7" + (scroll > 0 ? "▲ " : "") + (total - rows.size()) + " more" + (scroll + rows.size() < total ? " ▼" : "");
            g.text(mc.font, more, (hudW - mc.font.width(more)) / 2, ry, 0xFFFFFFFF);
        }
        pose.popMatrix();
    }

    private static Hive hiveAt(double mx, double my) {
        TeslaMapsConfig c = cfg();
        float s = c.honeyhiveScale;
        double lx = (mx - c.honeyhiveX) / s, ly = (my - c.honeyhiveY) / s;
        if (lx < 0 || lx > hudW || ly < ROW_H) return null;
        int i = (int) ((ly - ROW_H) / ROW_H);
        return i >= 0 && i < rows.size() ? rows.get(i).hive : null;
    }

    private static boolean overHud(double mx, double my) {
        TeslaMapsConfig c = cfg();
        float s = c.honeyhiveScale;
        double lx = (mx - c.honeyhiveX) / s, ly = (my - c.honeyhiveY) / s;
        return lx >= 0 && lx <= hudW && ly >= 0 && ly <= hudH;
    }

    private static boolean onClick(double mx, double my, int button) {
        if (button != 0 || !visible()) return false;
        Hive h = hiveAt(mx, my);
        if (h == null) return false;
        toggle(h);
        buildRows(System.currentTimeMillis());
        return true;
    }

    private static boolean onScroll(double mx, double my, double amount) {
        if (amount == 0 || !visible() || !overHud(mx, my)) return false;
        int max = Math.max(0, total - maxLines());
        if (max == 0) return false;
        scroll = Math.max(0, Math.min(max, scroll + (amount > 0 ? -1 : 1)));
        buildRows(System.currentTimeMillis());
        return true;
    }
}
