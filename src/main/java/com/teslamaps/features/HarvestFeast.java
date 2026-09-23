package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.GardenRegistry;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.screen.AppleColors;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Crops in-season during the Harvest / Grand Feast, read from Ted's "All Crops" menu. Ported from SkyHanni. */
public final class HarvestFeast {

    private static final Pattern IN_SEASON = Pattern.compile("Will be in-season in (.+)!");
    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([dhms])");
    private static final String OUT_OF_SEASON = "Out of season until the next Feast!";

    private static final int[] CURRENT_SLOTS = {11, 12, 14, 15};
    private static final int ALL_FIRST = 27;
    private static final int ALL_LAST = 44;

    private static final long SB_EPOCH = 1559829300000L;
    private static final long SB_YEAR = 124L * 60 * 60 * 1000;
    private static final long SB_MONTH = SB_YEAR / 12;

    private static final int PADDING = 6;
    private static final int ICON = 16;

    private static final List<String> SAMPLE = List.of("Wheat", "Carrot", "Potato", "Pumpkin");

    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private static long lastRead;
    private static int lastVersion = -1;

    private HarvestFeast() {}

    public record Season(List<String> crops, long until) {}

    public static void tick() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.feastSeasonDisplay) return;
        if (!SkyblockUtils.isInSkyblock()) return;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!StringUtil.strip(cs.getTitle().getString()).trim().equals("All Crops")) return;

        long now = System.currentTimeMillis();
        int version = com.teslamaps.utils.ContainerState.version();
        boolean packetReady = version != lastVersion
                && com.teslamaps.utils.ContainerState.fullyOpened(cs.getMenu().containerId);
        if (!packetReady && now - lastRead < 500L) return;
        lastRead = now;
        lastVersion = version;
        read(config, cs, now);
    }

    private static void read(TeslaMapsConfig config, AbstractContainerScreen<?> cs, long now) {
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() <= ALL_LAST) return;

        List<String> current = new ArrayList<>();
        for (int slot : CURRENT_SLOTS) {
            String crop = cropName(slots.get(slot).getItem());
            if (crop != null) current.add(crop);
        }
        if (current.size() != 4) return;

        Map<String, Long> next = new LinkedHashMap<>();
        for (int slot = ALL_FIRST; slot <= ALL_LAST; slot++) {
            ItemStack stack = slots.get(slot).getItem();
            String crop = cropName(stack);
            if (crop == null) continue;
            next.put(crop, inSeasonAt(stack, now));
        }

        int month = monthIndex(now);
        if (month == config.feastMonthIndex && current.equals(config.feastCrops)
                && next.equals(config.feastNextSeason)) {
            return;
        }
        config.feastCrops = current;
        config.feastNextSeason = next;
        config.feastMonthIndex = month;
        TeslaMapsConfig.save();
    }

    /** Epoch millis the crop comes back in season, 0 when it is in season now or gone for this feast. */
    private static long inSeasonAt(ItemStack stack, long now) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return 0L;
        for (Component line : lore.lines()) {
            String text = StringUtil.strip(line.getString()).trim();
            if (text.equals(OUT_OF_SEASON)) return 0L;
            Matcher matcher = IN_SEASON.matcher(text);
            if (matcher.find()) {
                long duration = parseDuration(matcher.group(1));
                if (duration > 0) return now + duration;
            }
        }
        return 0L;
    }

    private static long parseDuration(String text) {
        Matcher matcher = DURATION.matcher(text.toLowerCase());
        long total = 0L;
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "d" -> amount * 86_400_000L;
                case "h" -> amount * 3_600_000L;
                case "m" -> amount * 60_000L;
                default -> amount * 1000L;
            };
        }
        return total;
    }

    private static String cropName(ItemStack stack) {
        if (stack.isEmpty()) return null;
        String name = StringUtil.strip(stack.getHoverName().getString()).trim();
        for (GardenRegistry.Crop crop : GardenRegistry.CROPS) {
            if (crop.display().equalsIgnoreCase(name)) return crop.display();
        }
        return null;
    }

    private static int monthIndex(long now) {
        return (int) Math.floorDiv(now - SB_EPOCH, SB_MONTH);
    }

    private static long monthEnd(long now) {
        return SB_EPOCH + (Math.floorDiv(now - SB_EPOCH, SB_MONTH) + 1) * SB_MONTH;
    }

    /** Current four crops and when they rotate, null when there is no usable data. */
    public static Season season() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (config.feastCrops.size() != 4) return null;
        long now = System.currentTimeMillis();
        // data from the previous skyblock month still carries the upcoming rotations
        if (monthIndex(now) - config.feastMonthIndex > 1) return null;

        Map<String, Long> next = config.feastNextSeason;
        List<String> crops = config.feastCrops;

        long passed = 0L;
        for (long time : next.values()) {
            if (time <= 0L || time > now || time <= passed) continue;
            List<String> group = new ArrayList<>();
            for (Map.Entry<String, Long> entry : next.entrySet()) {
                if (entry.getValue() == time) group.add(entry.getKey());
            }
            if (group.size() == 4) {
                passed = time;
                crops = group;
            }
        }

        long until = Long.MAX_VALUE;
        for (long time : next.values()) {
            if (time > now && time < until) until = time;
        }
        if (until == Long.MAX_VALUE) until = monthEnd(now);
        return new Season(crops, until);
    }

    private static ItemStack icon(String display) {
        return ICONS.computeIfAbsent(display, name -> {
            for (GardenRegistry.Crop crop : GardenRegistry.CROPS) {
                if (!crop.display().equals(name)) continue;
                return BuiltInRegistries.ITEM
                        .get(Identifier.fromNamespaceAndPath("minecraft", crop.icon()))
                        .map(holder -> new ItemStack(holder.value()))
                        .orElse(ItemStack.EMPTY);
            }
            return ItemStack.EMPTY;
        });
    }

    private static String format(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60L) + "s";
        return seconds + "s";
    }

    private static final String TITLE = "§aIn-season:";

    public static boolean shouldRender() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.feastSeasonDisplay || !SkyblockUtils.isInSkyblock()) return false;
        if (!config.feastSeasonEverywhere && !SkyblockUtils.isInGarden()) return false;
        return season() != null;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!shouldRender()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        draw(context, Minecraft.getInstance(), config.feastSeasonX, config.feastSeasonY,
                config.feastSeasonScale, false);
    }

    private static List<String> crops(boolean preview) {
        Season season = season();
        if (season != null) return season.crops();
        return preview ? SAMPLE : List.of();
    }

    private static String time(boolean preview) {
        Season season = season();
        if (season != null) return "§7(§b" + format(season.until() - System.currentTimeMillis()) + "§7)";
        return preview ? "§7(§b1h 4m§7)" : "";
    }

    private static int panelWidth(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        return PADDING * 2 + mc.font.width(TITLE) + 4 + crops(preview).size() * (ICON + 2)
                + 2 + mc.font.width(time(preview));
    }

    private static int panelHeight() {
        return PADDING * 2 + ICON;
    }

    public static int width(float scale) {
        return (int) (panelWidth(false) * scale);
    }

    public static int height(float scale) {
        return (int) (panelHeight() * scale);
    }

    public static int previewWidth(float scale) {
        return (int) (panelWidth(true) * scale);
    }

    public static int previewHeight(float scale) {
        return (int) (panelHeight() * scale);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale,
                            boolean preview) {
        List<String> crops = crops(preview);
        int width = panelWidth(preview);
        int height = panelHeight();

        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);

        HudStyle.panel(context, 0, 0, width, height, 6);

        int textY = PADDING + (ICON - 8) / 2;
        context.text(mc.font, Component.literal(TITLE), PADDING, textY, AppleColors.TEXT_PRIMARY, true);

        int iconX = PADDING + mc.font.width(TITLE) + 4;
        for (String crop : crops) {
            ItemStack stack = icon(crop);
            if (!stack.isEmpty()) context.item(stack, iconX, PADDING);
            iconX += ICON + 2;
        }
        context.text(mc.font, Component.literal(time(preview)), iconX + 2, textY,
                HudStyle.label(), true);

        context.pose().popMatrix();
    }
}
