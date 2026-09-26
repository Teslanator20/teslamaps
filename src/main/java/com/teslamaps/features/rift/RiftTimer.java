package com.teslamaps.features.rift;

// Ported from SkyHanni's rift/everywhere/RiftTimer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.Placeholders;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RiftTimer {

    private static final List<String> SAMPLE = List.of("§eRift Timer: §b12m 30s§7/§b45m 0s §7(27%)", "§c-30s");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static String lastBar = "";
    private static int maxSec, currentSec, latestSec = -1;
    private static final List<long[]> changes = new ArrayList<>();
    private static Object lastLevel;
    private static volatile boolean nametags;
    private static final Map<String, Component> tagCache = new HashMap<>();

    private RiftTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        hud = HudEditElement.of("riftTimer", "Rift Timer").section("Rift")
                .enabled(() -> cfg().riftTimer)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("RiftTimer").section("Rift").when(() -> cfg().riftTimer)
                .tick(RiftTimer::tick)
                .hudHiddenInInventory("rifttimer", RiftTimer::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Rift Timer", () -> c.riftTimer, v -> c.riftTimer = v)
                            .desc("Rift time from the action bar as a HUD, yellow under 5 min, red under 1 min, every gain/loss shows for 4s (/tmap gui).");
                    b.toggle("  Show Max Time", () -> c.riftTimerMax, v -> c.riftTimerMax = v);
                    b.toggle("  Show Percentage", () -> c.riftTimerPercent, v -> c.riftTimerPercent = v);
                    b.toggle("  Readable Nametag Time", () -> c.riftTimerNametag, v -> c.riftTimerNametag = v)
                            .desc("Other players' \"3150 ф\" below their name becomes \"52m 30s ф\".");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            maxSec = 0;
            currentSec = 0;
            latestSec = -1;
            changes.clear();
            lines = List.of();
            tagCache.clear();
        }
        boolean rift = RiftZone.inRift();
        nametags = rift && cfg().riftTimerNametag;
        if (!rift) {
            lines = List.of();
            return;
        }
        String bar = Placeholders.actionBar;
        if (bar != lastBar) {
            lastBar = bar;
            int t = parseBar(StringUtil.strip(bar));
            if (t >= 0) {
                if (t > maxSec) maxSec = t;
                currentSec = t;
                if (latestSec >= 0 && t != latestSec) {
                    int diff = t - latestSec + 1;
                    if (t != maxSec && diff != 0) changes.add(new long[]{System.currentTimeMillis(), diff});
                }
                latestSec = t;
            }
        }
        if (latestSec < 0) {
            lines = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        changes.removeIf(ch -> now > ch[0] + 4000);
        TeslaMapsConfig c = cfg();
        String color = currentSec <= 60 ? "§c" : currentSec <= 300 ? "§e" : "§b";
        StringBuilder first = new StringBuilder("§eRift Timer: ").append(color).append(fmt(currentSec));
        if (c.riftTimerMax && maxSec > 0) first.append("§7/§b").append(fmt(maxSec));
        if (c.riftTimerPercent && maxSec > 0) first.append(" §7(").append(currentSec * 100 / maxSec).append("%)");
        List<String> out = new ArrayList<>(1 + changes.size());
        out.add(first.toString());
        for (long[] ch : changes) out.add(ch[1] > 0 ? "§a+" + fmt((int) ch[1]) : "§c-" + fmt((int) -ch[1]));
        lines = out;
        width = TextHud.width(out);
    }

    private static boolean isIcon(char ch) {
        return ch == '' || ch == 'ф';
    }

    // "12m 30sф Left" -> 750
    static int parseBar(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!isIcon(s.charAt(i))) continue;
            int j = i;
            while (j > 0) {
                char ch = s.charAt(j - 1);
                if (Character.isDigit(ch) || ch == 'm' || ch == 's' || ch == ' ') j--;
                else break;
            }
            int t = parseDuration(s.substring(j, i));
            if (t >= 0) return t;
        }
        return -1;
    }

    private static int parseDuration(String s) {
        int total = 0, num = 0;
        boolean any = false, digits = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) {
                num = num * 10 + (ch - '0');
                digits = true;
            } else if (ch == 'm' || ch == 's') {
                if (!digits) continue;
                total += ch == 'm' ? num * 60 : num;
                num = 0;
                digits = false;
                any = true;
            }
        }
        return any ? total : -1;
    }

    static String fmt(int sec) {
        int m = sec / 60, s = sec % 60;
        if (m >= 60) return (m / 60) + "h " + (m % 60) + "m";
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null || RiftZone.inMirrorverse()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    /** From PlayerCosmeticsMixin, after a player's render state is extracted. */
    public static void nametag(AvatarRenderState state) {
        if (!nametags || state.scoreText == null) return;
        String raw = state.scoreText.getString();
        Component cached = tagCache.get(raw);
        if (cached == null) {
            cached = rewrite(raw);
            if (tagCache.size() > 256) tagCache.clear();
            tagCache.put(raw, cached);
        }
        if (cached != NONE) state.scoreText = cached;
    }

    private static final Component NONE = Component.empty();

    private static Component rewrite(String raw) {
        String s = StringUtil.stripTrim(raw);
        int sp = s.indexOf(' ');
        if (sp <= 0 || sp + 1 >= s.length() || !isIcon(s.charAt(sp + 1))) return NONE;
        int sec = 0;
        for (int i = 0; i < sp; i++) {
            char ch = s.charAt(i);
            if (!Character.isDigit(ch)) return NONE;
            sec = sec * 10 + (ch - '0');
        }
        return Component.literal(fmt(sec) + " §a" + s.charAt(sp + 1));
    }
}
