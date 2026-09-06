// Ported from SkyHanni's DeployableDisplay.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Power orbs, lanterns, the Black Hole and the Trophy Fishing Umberella all announce their
 * own remaining time in their nametag ("Umberella 298s"), so the timer is just read off it.
 */
public class Deployables {

    public enum Type { FLUX, LANTERN, UMBERELLA, BLACK_HOLE }

    public enum Deployable {
        RADIANT("Radiant", "§aRadiant", 18, Type.FLUX, 1),
        MANA_FLUX("Mana Flux", "§9Mana Flux", 18, Type.FLUX, 2),
        OVERFLUX("Overflux", "§5Overflux", 18, Type.FLUX, 3),
        PLASMAFLUX("Plasmaflux", "§d§lPlasmaflux", 20, Type.FLUX, 4),
        DWARVEN_LANTERN("Dwarven Lantern", "§fDwarven Lantern", 30, Type.LANTERN, 1),
        MITHRIL_LANTERN("Mithril Lantern", "§aMithril Lantern", 30, Type.LANTERN, 2),
        TITANIUM_LANTERN("Titanium Lantern", "§9Titanium Lantern", 30, Type.LANTERN, 3),
        GLACITE_LANTERN("Glacite Lantern", "§5Glacite Lantern", 30, Type.LANTERN, 4),
        WILL_O_WISP("Will-o'-wisp", "§6§lWill-o'-wisp", 30, Type.LANTERN, 5),
        BLACK_HOLE("Black Hole", "§5Black Hole", 20, Type.BLACK_HOLE, 0),
        UMBERELLA("Umberella", "§9Umberella", 30, Type.UMBERELLA, 0);

        public final String displayName;
        public final int range;
        public final Type type;
        public final int tier;
        private final Pattern pattern;

        Deployable(String name, String displayName, int range, Type type, int tier) {
            this.displayName = displayName;
            this.range = range;
            this.type = type;
            this.tier = tier;
            this.pattern = Pattern.compile(Pattern.quote(name) + " (?<time>\\d+)s");
        }
    }

    private static final Map<Deployable, Long> expiry = new EnumMap<>(Deployable.class);

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.deployableOverlay && !c.umbrellaWarning) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            expiry.clear();
            return;
        }

        long now = System.currentTimeMillis();
        expiry.entrySet().removeIf(e -> e.getValue() <= now);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || stand.getCustomName() == null) continue;
            String name = StringUtil.strip(stand.getCustomName().getString()).trim();
            if (name.isEmpty()) continue;

            for (Deployable deployable : Deployable.values()) {
                Matcher m = deployable.pattern.matcher(name);
                if (!m.matches()) continue;
                // out of range it does nothing for you, so it should not be on the HUD either
                if (entity.distanceToSqr(mc.player) > (double) deployable.range * deployable.range) break;
                long end = now + Integer.parseInt(m.group("time")) * 1000L;
                Long known = expiry.get(deployable);
                if (known == null || end > known) expiry.put(deployable, end);
                break;
            }
        }
    }

    /** @return seconds left, or -1 when that deployable is not up. */
    public static long secondsLeft(Deployable deployable) {
        Long end = expiry.get(deployable);
        if (end == null) return -1;
        return Math.max(0, (end - System.currentTimeMillis() + 999) / 1000);
    }

    public static void reset() {
        expiry.clear();
    }

    public static void clear(Deployable deployable) {
        expiry.remove(deployable);
    }

    private static boolean shows(TeslaMapsConfig c, Type type) {
        return switch (type) {
            case FLUX -> c.deployableFlux;
            case LANTERN -> c.deployableLantern;
            case UMBERELLA -> c.deployableUmbrella;
            case BLACK_HOLE -> c.deployableBlackHole;
        };
    }

    public static List<String> lines() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        List<String> out = new ArrayList<>();
        for (Deployable deployable : Deployable.values()) {
            if (!shows(c, deployable.type)) continue;
            long left = secondsLeft(deployable);
            if (left < 0) continue;
            // a Radiant next to an Overflux is just noise
            if (c.deployableHighestTierOnly && outranked(deployable)) continue;
            out.add(deployable.displayName + " §e" + format(left));
        }
        return out;
    }

    private static boolean outranked(Deployable deployable) {
        for (Deployable other : Deployable.values()) {
            if (other.type != deployable.type || other.tier <= deployable.tier) continue;
            if (secondsLeft(other) >= 0) return true;
        }
        return false;
    }

    private static String format(long seconds) {
        return seconds >= 60 ? String.format("%dm %02ds", seconds / 60, seconds % 60) : seconds + "s";
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.deployableOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        List<String> lines = lines();
        if (lines.isEmpty()) return;
        draw(context, mc, c.deployableX, c.deployableY, c.deployableScale, lines);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        List<String> lines = lines();
        draw(context, mc, x, y, scale, lines.isEmpty() ? List.of("§9Umberella §e4m 58s") : lines);
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

    public static int[] previewSize(Font font) {
        List<String> lines = lines();
        if (lines.isEmpty()) lines = List.of("§9Umberella §e4m 58s");
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        return new int[] { w, lines.size() * (font.lineHeight + 1) };
    }
}
