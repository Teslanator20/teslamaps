package com.teslamaps.features.hud;

// Ported from SkyHanni's features/gui/BeaconPower.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BeaconPower {

    private static final int POWER_SLOT = 22, STAT_SLOT = 23;
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final List<String> SAMPLE = List.of("§eBeacon: §b5h 53m §7(+5✯ Magic Find)");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, timer;

    private BeaconPower() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("beaconPower", "Beacon Power")
                .enabled(() -> cfg().beaconPower)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("BeaconPower").when(() -> cfg().beaconPower)
                .tick(BeaconPower::tick)
                .hudHiddenInInventory("beaconpower", BeaconPower::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Beacon Power");
                    b.toggle("Beacon Power", () -> c.beaconPower, v -> c.beaconPower = v)
                            .desc("Time left on your island Beacon's power. Open the Beacon once to read it. Position in /tmap gui.");
                    b.toggle("  Show Stat", () -> c.beaconPowerStat, v -> c.beaconPowerStat = v);
                    b.toggle("  Short Stat", () -> c.beaconPowerCompress, v -> c.beaconPowerCompress = v)
                            .desc("Only the number, e.g. +5✯");
                });
    }

    private static String key() {
        String k = SbProfile.key();
        return k == null ? "default" : k;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs && timer % 10 == 0
                && StringUtil.stripTrim(cs.getTitle().getString()).equals("Beacon")) {
            read(cs);
        }
        if (timer++ % 20 != 0) return;
        if (!SkyblockUtils.isInSkyblock()) {
            lines = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        long left = c.beaconPowerExpiry.getOrDefault(key(), 0L) - System.currentTimeMillis();
        String line;
        if (left <= 0) line = "§eBeacon: §cNot active";
        else {
            line = "§eBeacon: §b" + format(left);
            if (c.beaconPowerStat) {
                String stat = c.beaconPowerStats.get(key());
                if (stat == null) stat = "§cNo stat";
                else if (c.beaconPowerCompress && stat.indexOf(' ') > 0) stat = stat.substring(0, stat.indexOf(' '));
                line += " §7(" + stat + "§7)";
            }
        }
        lines = List.of(line);
        width = TextHud.width(lines);
    }

    private static void read(AbstractContainerScreen<?> cs) {
        var slots = cs.getMenu().slots;
        if (slots.size() <= STAT_SLOT) return;
        TeslaMapsConfig c = cfg();
        boolean changed = false;
        for (String l : ItemUtil.loreLines(slots.get(POWER_SLOT).getItem())) {
            String t = l.trim();
            if (t.startsWith("Beacon Deactivated")) {
                Long old = c.beaconPowerExpiry.put(key(), 0L);
                changed = old == null || old != 0L;
                break;
            }
            if (t.startsWith("Power Remaining: ")) {
                long ms = parse(t.substring(17));
                if (ms > 0) {
                    long exp = System.currentTimeMillis() + ms;
                    Long old = c.beaconPowerExpiry.put(key(), exp);
                    if (old == null || Math.abs(old - exp) > 30_000) changed = true;
                }
                break;
            }
        }
        Slot stat = slots.get(STAT_SLOT);
        for (String l : ItemUtil.loreLines(stat.getItem())) {
            String t = l.trim();
            if (t.startsWith("Current Stat: ")) {
                String s = t.substring(14).trim();
                if (!s.equals(c.beaconPowerStats.get(key()))) {
                    c.beaconPowerStats.put(key(), s);
                    changed = true;
                }
                break;
            }
        }
        if (changed) TeslaMapsConfig.save();
    }

    private static long parse(String s) {
        Matcher m = PART.matcher(s);
        long ms = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        return ms;
    }

    private static String format(long ms) {
        long s = ms / 1000;
        if (s >= 86400) return s / 86400 + "d " + s / 3600 % 24 + "h";
        if (s >= 3600) return s / 3600 + "h " + s / 60 % 60 + "m";
        if (s >= 60) return s / 60 + "m " + s % 60 + "s";
        return s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
