package com.teslamaps.features.garden;

// Ported from SkyHanni's JacobContestTimeNeeded.kt + FarmingContestApi.calculateAverages
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.garden.JacobContestMenus.Bracket;
import com.teslamaps.features.garden.JacobContestMenus.Contest;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JacobContestTimes {

    private record Line(String text, List<Component> tip, double sort) {}

    private static final int LINE = 10;
    private static List<Line> lines = List.of();
    private static int width;
    private static int ticks;
    private static int px, py, pw, ph;
    private static Feature feature;

    private JacobContestTimes() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("JacobContestTimes").section("Garden").when(() -> cfg().jacobContestTimes)
                .tick(JacobContestTimes::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Time Needed For Medal", () -> c.jacobContestTimes, v -> c.jacobContestTimes = v)
                            .desc("In Jacob's \"Your Contests\" menu: every crop sorted by the farming time your latest fortune and BPS need for the chosen medal. Click the Bracket line to switch, hover a crop for all brackets. Open a few pages so the brackets are known.")
                            .tags("jacob", "contest", "medal", "bracket", "time");
                    b.toggle("  Use Custom BPS", () -> c.jacobContestCustomBps, v -> c.jacobContestCustomBps = v);
                    b.slider("    Custom BPS", 15f, 20f, () -> c.jacobContestBps, v -> c.jacobContestBps = v, 1);
                });
    }

    private static Bracket bracket() {
        try { return Bracket.valueOf(cfg().jacobContestBracket); } catch (IllegalArgumentException e) { return Bracket.GOLD; }
    }

    private static Map<Bracket, Integer> averages(GardenCrop crop) {
        List<Map.Entry<Long, Contest>> list = new ArrayList<>();
        for (Map.Entry<Long, Contest> e : JacobContestMenus.contests().entrySet()) if (e.getValue().crop() == crop) list.add(e);
        list.sort(Map.Entry.<Long, Contest>comparingByKey().reversed());
        Map<Bracket, long[]> sums = new EnumMap<>(Bracket.class);
        for (int i = 0; i < Math.min(10, list.size()); i++) {
            for (Map.Entry<Bracket, Integer> b : list.get(i).getValue().brackets().entrySet()) {
                long[] s = sums.computeIfAbsent(b.getKey(), k -> new long[2]);
                s[0] += b.getValue();
                s[1]++;
            }
        }
        Map<Bracket, Integer> out = new EnumMap<>(Bracket.class);
        for (Map.Entry<Bracket, long[]> e : sums.entrySet()) out.put(e.getKey(), (int) (e.getValue()[0] / e.getValue()[1]));
        return out;
    }

    private static String time(double seconds) {
        long s = (long) seconds;
        long h = s / 3600, m = s % 3600 / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    private static Double bps(GardenCrop crop) {
        return cfg().jacobContestCustomBps ? (double) cfg().jacobContestBps : GardenCropData.latestBps(crop);
    }

    private static void tick() {
        if (!JacobContestMenus.inContestMenu()) {
            if (!lines.isEmpty()) lines = List.of();
            return;
        }
        if (++ticks % 10 != 0) return;
        Bracket current = bracket();
        List<Line> crops = new ArrayList<>();
        for (GardenCrop crop : GardenCrop.ALL) crops.add(line(crop, current));
        crops.sort(Comparator.comparingDouble(Line::sort));
        List<Line> out = new ArrayList<>();
        out.add(new Line("§e§lTime Needed for " + current.display() + " §eMedal!", List.of(), 0));
        out.add(new Line("§7Bracket: §e" + current.name().charAt(0) + current.name().substring(1).toLowerCase(Locale.ROOT) + " §8(click)", List.of(), 0));
        out.addAll(crops);
        int w = 0;
        Minecraft mc = Minecraft.getInstance();
        for (Line l : out) w = Math.max(w, mc.font.width(l.text));
        lines = out;
        width = w;
    }

    private static Line line(GardenCrop crop, Bracket current) {
        String name = "§9" + crop.cropName;
        Double bps = bps(crop);
        if (bps == null) return new Line(name + " §cNo speed data!", List.of(Component.literal("§cFarm " + crop.cropName + " to show data!")), Double.MAX_VALUE);
        Double ff = GardenCropData.latestFortune(crop);
        if (ff == null) return new Line(name + " §cNo Farming Fortune data!", List.of(
                Component.literal("§cHold a " + crop.cropName + " farming tool"), Component.literal("§cwith the Stats tab widget on")), Double.MAX_VALUE);
        Map<Bracket, Integer> avg = averages(crop);
        if (avg.isEmpty()) return new Line(name + " §cNo contest data!", List.of(Component.literal("§cOpen more pages or join"),
                Component.literal("§ca " + crop.cropName + " Contest to show data!")), Double.MAX_VALUE - 2);

        int speed = (int) (Math.round(ff * crop.baseDrops * bps / 100 * 10) / 10.0);
        double formula = bps < 15 ? (int) (speed / bps * 19.9) : speed;
        boolean low = bps < 15;
        String mark = low ? "§4§l !" : "";
        String show = null;
        double sort = Double.MAX_VALUE - 1;
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal("§7Time Needed for §9" + crop.cropName + " Medals§7:"));
        for (Bracket b : Bracket.values()) {
            Integer amount = avg.get(b);
            if (amount == null) {
                tip.add(Component.literal(b.display() + " §cBracket not revealed!"));
                if (b == current) show = name + " §cBracket not revealed!";
                continue;
            }
            double secs = amount / Math.max(1.0, formula);
            boolean possible = secs < 20 * 60;
            String text = b.display() + " " + (possible ? "§b" : "§c") + time(secs);
            String row;
            if (possible) {
                row = name + " §7in §b" + time(secs) + mark;
            } else {
                double useBps = low ? 19.9 : bps;
                double needed = amount / useBps / 60 * 100 / 20 / crop.baseDrops;
                text += String.format(Locale.US, " §7(Need %,d FF more)", (int) (needed - ff));
                row = name + " §cNo " + current.display() + " §cmedal possible!" + mark;
            }
            tip.add(Component.literal(text));
            if (b == current) {
                show = row;
                sort = secs;
            }
        }
        tip.add(Component.empty());
        tip.add(Component.literal(String.format(Locale.US, "§7Latest FF: §e%,.0f", ff)));
        tip.add(Component.literal(String.format(Locale.US, "§7%s§e%.1f", cfg().jacobContestCustomBps ? "Custom Blocks/Second: " : "Your Blocks/Second: ", bps)));
        if (low) {
            tip.add(Component.literal("§cYour Blocks/second is too low,"));
            tip.add(Component.literal("§cshowing 19.9 Blocks/second instead!"));
        }
        return new Line(show == null ? name : show, tip, sort);
    }

    /** From ContainerOverlays. */
    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (lines.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        pw = width + 8;
        ph = lines.size() * LINE + 6;
        px = Math.max(2, acc.getX() - pw - 6);
        py = Math.max(2, acc.getY());
        HudStyle.panel(ctx, px, py, pw, ph);
        Line hovered = null;
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            int y = py + 4 + i * LINE;
            ctx.text(mc.font, l.text, px + 4, y, 0xFFFFFFFF, true);
            if (!l.tip.isEmpty() && mouseX >= px && mouseX < px + pw && mouseY >= y - 1 && mouseY < y + LINE - 1) hovered = l;
        }
        if (hovered != null) ctx.setComponentTooltipForNextFrame(mc.font, hovered.tip, mouseX, mouseY);
    }

    /** From HandledScreenClickMixin mouseClicked; the Bracket line cycles the medal. */
    public static boolean handleClick(double mx, double my, int button) {
        if (lines.isEmpty() || button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || feature == null || !feature.active()) return false;
        int y = py + 4 + LINE;
        if (mx < px || mx >= px + pw || my < y - 1 || my >= y + LINE - 1) return false;
        Bracket[] all = Bracket.values();
        cfg().jacobContestBracket = all[(bracket().ordinal() + 1) % all.length].name();
        ticks = 9;
        return true;
    }
}
