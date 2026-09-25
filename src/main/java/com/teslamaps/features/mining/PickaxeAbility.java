package com.teslamaps.features.mining;

// Bar mode ported from Skysoft's MiningAbilityCooldownDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Drill / pickaxe ability cooldown, read off the "Pickaxe Ability:" tab widget.
 * Needs the widget enabled via /tab.
 */
public class PickaxeAbility {

    private static final String HEADER = "Pickaxe Ability:";
    private static final Pattern ENTRY = Pattern.compile("^([A-Za-z' ]+): (.+)$");
    private static final Pattern SECONDS = Pattern.compile("^(?:(\\d+)m ?)?(?:(\\d+)s)?$");
    private static final Pattern USED = Pattern.compile("^You used your .+ Pickaxe Ability!$");

    private static final String[] SAMPLE = { "§6Pickobulus§8: §a Available" };
    private static final String[] MISSING = { "§cPickaxe Ability Widget Missing", "§cEnable it with /tab" };

    private static final String READY = "Ready";
    private static final long NANOS = 1_000_000_000L;
    private static final long EMPTY_ANIM = 180_000_000L;
    private static final int SLIM = 7, LONG = 42, GAP = 3;
    private static final int TRACK = 0xC0101010;

    private static String ability = "";
    private static String state = "";
    private static int ticks = 0;

    // bar timer: -2 no widget, -1 ready, else seconds left
    private static int status = -2;
    private static long startNanos, durationNanos;
    private static boolean animateEmpty, waitingForTab;
    private static long missingSince = -1;

    public static boolean tabWidgetMissing() {
        return missingSince >= 0 && System.currentTimeMillis() - missingSince >= 3000;
    }

    public static void register() {
        com.teslamaps.registry.Feature.of("PickaxeAbilityUse").section("Mining")
                .when(() -> TeslaMapsConfig.get().pickaxeAbilityDisplay && TeslaMapsConfig.get().pickaxeAbilityBar)
                .serverChat(PickaxeAbility::onChat);
    }

    private static void onChat(String text) {
        if (ability.isEmpty() || status == -2) return;
        if (!USED.matcher(StringUtil.stripTrim(text)).matches()) return;
        status = 0;
        startNanos = System.nanoTime();
        durationNanos = Long.MAX_VALUE;
        animateEmpty = true;
        waitingForTab = true;
    }

    private static void updateStatus(int next) {
        long now = System.nanoTime();
        int previous = status;
        if (next == previous || waitingForTab && next == -1) return;
        status = next;
        if (waitingForTab && next >= 0) {
            durationNanos = Math.max(NANOS, Math.max(0, now - startNanos) + next * NANOS);
            waitingForTab = false;
        } else if (next < 0) {
            startNanos = durationNanos = 0;
            animateEmpty = waitingForTab = false;
        } else if (previous < 0 || next > previous) {
            startNanos = now;
            durationNanos = Math.max(NANOS, next * NANOS);
            animateEmpty = previous == -1;
        }
    }

    private static float progress() {
        if (status == -1) return 1f;
        long elapsed = Math.max(0, System.nanoTime() - startNanos);
        double fill = Math.min(1.0, Math.max(0.0, elapsed / (double) Math.max(1L, durationNanos)));
        if (!animateEmpty || elapsed >= EMPTY_ANIM) return (float) fill;
        double t = elapsed / (double) EMPTY_ANIM;
        double smooth = t * t * (3 - 2 * t);
        return (float) (1.0 + (fill - 1.0) * smooth);
    }

    public static void tick() {
        if (++ticks < 5) return;
        ticks = 0;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.pickaxeAbilityDisplay) return;
        if (c.pickaxeAbilityMiningOnly && !Mining.onMiningIsland()) return;
        read();
        if (c.pickaxeAbilityBar) updateStatus(ability.isEmpty() ? -2 : ready() ? -1 : Math.max(0, secondsLeft()));
        if (!ability.isEmpty()) missingSince = -1;
        else if (missingSince < 0) missingSince = System.currentTimeMillis();
    }

    private static void read() {
        boolean inWidget = false;
        for (String raw : TabListUtils.getOrderedLines()) {
            String line = StringUtil.stripTrim(raw);
            if (line.equals(HEADER)) {
                inWidget = true;
                continue;
            }
            if (!inWidget) continue;
            Matcher m = ENTRY.matcher(line);
            if (m.matches()) {
                ability = m.group(1);
                state = m.group(2);
                return;
            }
            break;
        }
        ability = "";
        state = "";
    }

    public static boolean ready() {
        return state.equalsIgnoreCase("Available");
    }

    /** Remaining cooldown in seconds, or -1 when ready / unknown. */
    public static int secondsLeft() {
        if (state.isEmpty() || ready()) return -1;
        Matcher m = SECONDS.matcher(state.trim());
        if (!m.matches()) return -1;
        int minutes = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
        int seconds = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        return minutes * 60 + seconds;
    }

    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        if (ability.isEmpty()) return out;
        int left = secondsLeft();
        String color = ready() ? "§a" : left >= 0 && left <= 5 ? "§e" : "§c";
        out.add("§6" + ability + "§8: " + color + state);
        return out;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.pickaxeAbilityDisplay) return;
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        if (c.pickaxeAbilityMiningOnly && !Mining.onMiningIsland()) return;
        if (c.pickaxeAbilityOnlyOnCooldown && ready()) return;

        Minecraft mc = Minecraft.getInstance();
        if (c.pickaxeAbilityBar) {
            if (!ability.isEmpty()) {
                drawBar(context, mc, c.pickaxeAbilityX, c.pickaxeAbilityY, c.pickaxeAbilityScale, ready(), progress());
            } else if (missingSince >= 0 && System.currentTimeMillis() - missingSince >= 3000) {
                draw(context, mc, c.pickaxeAbilityX, c.pickaxeAbilityY, c.pickaxeAbilityScale, List.of(MISSING));
            }
            return;
        }

        List<String> lines = lines();
        if (lines.isEmpty()) {
            if (com.teslamaps.features.TabWidgetHint.on() && com.teslamaps.utils.SkyblockUtils.isInSkyblock() && missingSince >= 0 && System.currentTimeMillis() - missingSince >= 3000) {
                draw(context, mc, c.pickaxeAbilityX, c.pickaxeAbilityY, c.pickaxeAbilityScale, List.of(MISSING));
            }
            return;
        }
        draw(context, mc, c.pickaxeAbilityX, c.pickaxeAbilityY, c.pickaxeAbilityScale, lines);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        if (TeslaMapsConfig.get().pickaxeAbilityBar) {
            drawBar(context, mc, x, y, scale, true, 1f);
            return;
        }
        List<String> lines = lines();
        draw(context, mc, x, y, scale, lines.isEmpty() ? List.of(SAMPLE) : lines);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, List<String> lines) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        for (String line : lines) {
            context.text(mc.font, line, 0, row * (mc.font.lineHeight + 1), 0xFFFFFFFF);
            row++;
        }
        pose.popMatrix();
    }

    private static void drawBar(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, boolean ready, float progress) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean vertical = c.pickaxeAbilityBarVertical;
        int bw = vertical ? SLIM : LONG, bh = vertical ? LONG : SLIM;
        int readyColor = 0xFF000000 | TeslaMapsConfig.parseColor(c.pickaxeAbilityReadyColor);
        int emptyColor = 0xFF000000 | TeslaMapsConfig.parseColor(c.pickaxeAbilityEmptyColor);
        String pos = c.pickaxeAbilityReadyText;
        int tw = mc.font.width(READY), th = mc.font.lineHeight;
        int[] size = barSize(mc.font);
        int bx = 0, by = 0, tx = 0, ty = 0;
        switch (pos) {
            case "Right" -> { by = (size[1] - bh) / 2; tx = bw + GAP; ty = (size[1] - th) / 2 + 1; }
            case "Left" -> { bx = tw + GAP; by = (size[1] - bh) / 2; ty = (size[1] - th) / 2 + 1; }
            case "Top" -> { bx = (size[0] - bw) / 2; by = th + GAP; tx = (size[0] - tw) / 2; }
            case "Bottom" -> { bx = (size[0] - bw) / 2; tx = (size[0] - tw) / 2; ty = bh + GAP; }
            default -> { }
        }
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        roundedRect(g, bx, by, bw, bh, TRACK);
        float p = Math.max(0f, Math.min(1f, progress));
        int color = lerp(emptyColor, readyColor, p);
        if (vertical) {
            int fh = Math.round((bh - 2) * p);
            if (fh > 0) glossy(g, bx + 1, by + bh - 1 - fh, bw - 2, fh, color);
        } else {
            int fw = Math.round((bw - 2) * p);
            if (fw > 0) glossy(g, bx + 1, by + 1, fw, bh - 2, color);
        }
        if (ready && !"Off".equals(pos)) g.text(mc.font, READY, tx, ty, readyColor);
        pose.popMatrix();
    }

    private static int[] barSize(Font font) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean vertical = c.pickaxeAbilityBarVertical;
        int bw = vertical ? SLIM : LONG, bh = vertical ? LONG : SLIM;
        int tw = font.width(READY), th = font.lineHeight;
        return switch (c.pickaxeAbilityReadyText) {
            case "Right", "Left" -> new int[] { bw + GAP + tw, Math.max(bh, th) };
            case "Top", "Bottom" -> new int[] { Math.max(bw, tw), bh + GAP + th };
            default -> new int[] { bw, bh };
        };
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bb = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bb;
    }

    private static int shade(int color, int delta) {
        int r = Math.max(0, Math.min(255, ((color >> 16) & 0xFF) + delta));
        int gg = Math.max(0, Math.min(255, ((color >> 8) & 0xFF) + delta));
        int b = Math.max(0, Math.min(255, (color & 0xFF) + delta));
        return (color & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    private static void roundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int r = 2;
        if (h <= r * 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        if (w <= r * 2) {
            for (int o = 0; o < w; o++) {
                int inset = Math.max(0, r - Math.min(o, w - o - 1));
                g.fill(x + o, y + inset, x + o + 1, y + h - inset, color);
            }
            return;
        }
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + r, color);
        g.fill(x, y + r, x + w, y + h - r, color);
        g.fill(x + 1, y + h - r, x + w - 1, y + h - 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
    }

    private static void glossy(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        roundedRect(g, x, y, w, h, color);
        if (w <= 4 || h <= 4) return;
        g.fill(x + 2, y + 1, x + w - 2, y + 2, shade(color, 0x2A));
        g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, shade(color, -0x30));
    }

    public static int[] previewSize(Font font) {
        if (TeslaMapsConfig.get().pickaxeAbilityBar) return barSize(font);
        List<String> lines = lines();
        if (lines.isEmpty()) lines = List.of(SAMPLE);
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
