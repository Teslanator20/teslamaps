package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.api.DungeonData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

public class DungeonPage extends ProfileViewerPage {
    private static final int SECTION = 110;
    private static final int CATA_COLOR = 0xFFAA0000;
    private static final int CLASS_COLOR = 0xFFAA00AA;

    // static so the choice survives closing the viewer, master mode people never want normal
    private static boolean masterMode = false;

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Dungeons";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.WITHER_SKELETON_SKULL);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        SkyblockProfile profile = getProfile();
        if (profile == null || profile.getDungeonData() == null) {
            ctx.text(tr, "No dungeon data", x + 10, y + 10, NeuStyle.TEXT_GRAY);
            return;
        }

        int left = x - 8;
        int top = y - 8;
        lastLeft = left;
        lastTop = top;
        DungeonData data = profile.getDungeonData();

        // two switchable headers, so the master mode side is actually findable
        int titleX = left + NeuStyle.PANEL_WIDTH / 2;
        String normal = "Catacombs";
        String master = "Master Mode";
        int gap = 12;
        int totalWidth = tr.width(normal) + gap + tr.width(master);
        int normalX = titleX - totalWidth / 2;
        int masterX = normalX + tr.width(normal) + gap;

        // real buttons, the plain text version was too easy to miss
        NeuStyle.window(ctx, normalX - 5, top + 2, tr.width(normal) + 10, 14);
        NeuStyle.window(ctx, masterX - 5, top + 2, tr.width(master) + 10, 14);
        if (!masterMode) NeuStyle.slimBox(ctx, normalX - 5, top + 2, tr.width(normal) + 10, 14);
        else NeuStyle.slimBox(ctx, masterX - 5, top + 2, tr.width(master) + 10, 14);

        ctx.text(tr, normal, normalX, top + 5, masterMode ? NeuStyle.TEXT_GRAY : NeuStyle.TEXT_RED);
        ctx.text(tr, master, masterX, top + 5, masterMode ? NeuStyle.TEXT_RED : NeuStyle.TEXT_GRAY);

        lastNormalX = normalX;
        lastNormalW = tr.width(normal);
        lastMasterX = masterX;
        lastMasterW = tr.width(master);

        // catacombs level
        int cx = left + 23;
        int cy = top + 25;
        int cataLevel = data.getCatacombsLevel();
        ctx.text(tr, "Catacombs", cx, cy, NeuStyle.TEXT_RED);
        String lvl = String.valueOf(cataLevel);
        ctx.text(tr, lvl, cx + SECTION - tr.width(lvl), cy, NeuStyle.TEXT);
        NeuStyle.bar(ctx, cx, cy + 10, SECTION, data.getCatacombsProgress(), CATA_COLOR);

        aligned(ctx, tr, "Total XP:", formatXp(data.getCatacombsXp()), cx, cy + 20, NeuStyle.TEXT);
        aligned(ctx, tr, "Secrets:", String.valueOf(data.getSecretsFound()), cx, cy + 32, NeuStyle.TEXT);

        // the achievement counts every profile the account ever had, deleted ones included
        long totalSecrets = profiles == null ? -1 : profiles.getTotalSecrets();
        if (totalSecrets >= 0) {
            aligned(ctx, tr, "Total Secrets:", String.valueOf(totalSecrets), cx, cy + 44, NeuStyle.TEXT_AQUA);
        }

        int totalRuns = 0;
        for (DungeonData.FloorData floor : data.getNormalFloors().values()) totalRuns += floor.getCompletions();
        for (DungeonData.FloorData floor : data.getMasterFloors().values()) totalRuns += floor.getCompletions();
        aligned(ctx, tr, "Runs:", String.valueOf(totalRuns), cx, cy + 56, NeuStyle.TEXT);

        double secretsPerRun = totalRuns > 0 ? (double) data.getSecretsFound() / totalRuns : 0;
        aligned(ctx, tr, "S/R:", String.format("%.2f", secretsPerRun), cx, cy + 68, NeuStyle.TEXT);

        String highest = data.getHighestMaster() > 0 ? "M" + data.getHighestMaster()
                : data.getHighestNormal() > 0 ? "F" + data.getHighestNormal() : "-";
        aligned(ctx, tr, "Highest Floor:", highest, cx, cy + 80, NeuStyle.TEXT);
        drawExtras(ctx, tr, profile, data, cx, cy + 92, mouseX, mouseY);

        // completions and personal bests per floor
        int fx = left + 161;
        int fy = top + 27;
        String header = masterMode ? "Master Floors" : "Floor Completions";
        ctx.text(tr, header, fx + SECTION / 2 - tr.width(header) / 2, fy, NeuStyle.TEXT_RED);

        Map<String, DungeonData.FloorData> floors = masterMode ? data.getMasterFloors() : data.getNormalFloors();
        int mostRuns = 1;
        for (DungeonData.FloorData floor : floors.values()) mostRuns = Math.max(mostRuns, floor.getCompletions());

        // always all eight rows, so switching modes never moves the layout
        int rowY = fy + 12;
        for (int i = 0; i <= 7; i++) {
            DungeonData.FloorData floor = floors.get(FLOOR_KEYS[i]);
            String name = i == 0 ? "Entrance" : (masterMode ? "M" : "F") + i;
            int runsCount = floor == null ? 0 : floor.getCompletions();
            boolean played = runsCount > 0;

            ctx.text(tr, name, fx, rowY, played ? NeuStyle.TEXT : NeuStyle.TEXT_DARK);
            // four digit completion counts used to run straight into the floor name
            String runs = String.valueOf(runsCount);
            ctx.text(tr, runs, fx + 52 - tr.width(runs), rowY, played ? NeuStyle.TEXT_GRAY : NeuStyle.TEXT_DARK);
            if (played) {
                int barWidth = Math.max(1, 52 * runsCount / mostRuns);
                ctx.fill(fx, rowY + 9, fx + barWidth, rowY + 10, CATA_COLOR);
            }

            String grade = floor == null ? null : floor.getBestGrade();
            if (floor != null && floor.getFastestSPlusTime() > 0) {
                String sPlus = floor.getFormattedSPlusTime();
                ctx.text(tr, sPlus, fx + SECTION - tr.width(sPlus), rowY, NeuStyle.TEXT_GOLD);
            } else if (grade != null) {
                ctx.text(tr, grade, fx + SECTION - tr.width(grade), rowY, gradeColor(grade));
            } else {
                ctx.text(tr, "-", fx + SECTION - tr.width("-"), rowY, NeuStyle.TEXT_DARK);
            }

            if (floor != null && mouseX > fx && mouseX < fx + SECTION && mouseY > rowY - 2 && mouseY < rowY + 10) {
                tooltip = grade == null ? new String[]{
                        name,
                        "Completions: " + runsCount,
                        "Fastest: " + floor.getFormattedFastestTime(),
                        "Fastest S: " + floor.getFormattedSTime(),
                        "Fastest S+: " + floor.getFormattedSPlusTime(),
                } : new String[]{
                        name,
                        "Completions: " + runsCount,
                        "Fastest: " + floor.getFormattedFastestTime(),
                        "Fastest S: " + floor.getFormattedSTime(),
                        "Fastest S+: " + floor.getFormattedSPlusTime(),
                        "Best Run: " + grade + " (" + floor.getBestScore() + ")" + bestDate(floor),
                };
                tooltipX = mouseX;
                tooltipY = mouseY;
            }
            rowY += 12;
        }

        // one floor completion is one kill of its boss, normal and master together
        int bossY = top + 160;
        String bossHeader = "Boss Collection";
        ctx.text(tr, bossHeader, left + NeuStyle.PANEL_WIDTH / 2 - tr.width(bossHeader) / 2, bossY, NeuStyle.TEXT_RED);
        int cellWidth = 55;
        int stripX = left + 23;
        for (int i = 1; i <= 7; i++) {
            DungeonData.FloorData normalFloor = data.getNormalFloors().get(FLOOR_KEYS[i]);
            DungeonData.FloorData masterFloor = data.getMasterFloors().get(FLOOR_KEYS[i]);
            int kills = (normalFloor == null ? 0 : normalFloor.getCompletions())
                    + (masterFloor == null ? 0 : masterFloor.getCompletions());
            int center = stripX + (i - 1) * cellWidth + cellWidth / 2;
            String boss = BOSSES[i];
            String count = String.valueOf(kills);
            ctx.text(tr, boss, center - tr.width(boss) / 2, bossY + 12, kills > 0 ? NeuStyle.TEXT : NeuStyle.TEXT_DARK);
            ctx.text(tr, count, center - tr.width(count) / 2, bossY + 22, kills > 0 ? NeuStyle.TEXT_GOLD : NeuStyle.TEXT_DARK);
        }

        // class levels
        int clx = left + 298;
        int cly = top + 27;
        ctx.text(tr, "Class Levels", clx + SECTION / 2 - tr.width("Class Levels") / 2, cly, NeuStyle.TEXT_PURPLE);

        int classRow = cly + 12;
        double classSum = 0;
        for (Map.Entry<String, DungeonData.ClassData> entry : data.getClasses().entrySet()) {
            DungeonData.ClassData cls = entry.getValue();
            // the progress into the next level counts, otherwise five 49.25s average to 49.00
            classSum += cls.getLevel() + cls.getProgress();

            boolean selected = entry.getKey().equalsIgnoreCase(data.getSelectedClass());
            ctx.text(tr, cls.getDisplayName(), clx, classRow, selected ? NeuStyle.TEXT_GREEN : NeuStyle.TEXT);
            String clevel = String.valueOf(cls.getLevel());
            ctx.text(tr, clevel, clx + SECTION - tr.width(clevel), classRow, NeuStyle.TEXT);
            NeuStyle.bar(ctx, clx, classRow + 10, SECTION, cls.getProgress(), CLASS_COLOR);

            if (mouseX > clx && mouseX < clx + SECTION && mouseY > classRow - 2 && mouseY < classRow + 15) {
                tooltip = new String[]{
                        cls.getDisplayName(),
                        "Level: " + cls.getLevel(),
                        "Total XP: " + formatXp(cls.getXp()),
                };
                tooltipX = mouseX;
                tooltipY = mouseY;
            }
            classRow += 21;
        }

        String avg = String.format("%.2f", classSum / Math.max(1, data.getClasses().size()));
        aligned(ctx, tr, "Class Average:", avg, clx, classRow + 2, NeuStyle.TEXT_PURPLE);

        if (tooltip != null) {
            drawTooltip(ctx, tr);
            tooltip = null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hover((int) mouseX, (int) mouseY, calcX, calcY)) {
            targetFocused = true;
            targetText = "";
            return true;
        }
        if (targetFocused) commitTarget();
        if (mouseY > lastTop + 1 && mouseY < lastTop + 17) {
            if (mouseX >= lastNormalX - 3 && mouseX <= lastNormalX + lastNormalW + 3) {
                masterMode = false;
                return true;
            }
            if (mouseX >= lastMasterX - 3 && mouseX <= lastMasterX + lastMasterW + 3) {
                masterMode = true;
                return true;
            }
        }
        return false;
    }

    private int lastLeft;
    private int lastTop;
    private int lastNormalX;
    private int lastNormalW;
    private int lastMasterX;
    private int lastMasterW;

    private String[] tooltip;
    private int tooltipX;
    private int tooltipY;

    private void drawTooltip(GuiGraphicsExtractor ctx, Font tr) {
        int w = 0;
        for (String line : tooltip) w = Math.max(w, tr.width(com.teslamaps.profileviewer.data.LegacyText.parse(line)));
        int h = tooltip.length * 10 + 4;
        int tx = Math.min(tooltipX + 8, NeuStyle.PANEL_WIDTH - w - 8);
        int ty = Math.max(2, tooltipY - h);
        NeuStyle.window(ctx, tx - 3, ty - 2, w + 8, h);
        for (int i = 0; i < tooltip.length; i++) {
            ctx.text(tr, com.teslamaps.profileviewer.data.LegacyText.parse(tooltip[i]), tx, ty + 2 + i * 10,
                    i == 0 ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
        }
    }

    // ---------------------------------------------------------------- croesus, daily runs, xp calculator

    private static int targetLevel = -1;
    private boolean targetFocused = false;

    @Override
    public boolean wantsText() { return targetFocused; }
    private String targetText = "";
    private int calcX, calcY = -1000;

    private Object treasuresFor;
    private int croesusRuns, croesusUnclaimed, dailyRuns;

    private void readTreasures(SkyblockProfile profile) {
        if (treasuresFor == profile) return;
        treasuresFor = profile;
        croesusRuns = 0;
        croesusUnclaimed = 0;
        dailyRuns = 0;
        com.google.gson.JsonObject dungeons = profile.getMemberData().has("dungeons")
                && profile.getMemberData().get("dungeons").isJsonObject()
                ? profile.getMemberData().getAsJsonObject("dungeons") : null;
        if (dungeons == null) return;

        if (dungeons.has("treasures") && dungeons.get("treasures").isJsonObject()) {
            com.google.gson.JsonObject treasures = dungeons.getAsJsonObject("treasures");
            java.util.Map<String, Boolean> claimed = new java.util.HashMap<>();
            if (treasures.has("chests") && treasures.get("chests").isJsonArray()) {
                for (com.google.gson.JsonElement element : treasures.getAsJsonArray("chests")) {
                    if (!element.isJsonObject()) continue;
                    com.google.gson.JsonObject chest = element.getAsJsonObject();
                    if (!chest.has("run_id")) continue;
                    String run = chest.get("run_id").getAsString();
                    boolean opened = bool(chest, "paid") || bool(chest, "is_opened");
                    claimed.merge(run, opened, Boolean::logicalOr);
                }
            }
            croesusRuns = claimed.size();
            for (boolean done : claimed.values()) if (!done) croesusUnclaimed++;
        }

        if (dungeons.has("daily_runs") && dungeons.get("daily_runs").isJsonObject()) {
            com.google.gson.JsonObject daily = dungeons.getAsJsonObject("daily_runs");
            long day = asLong(daily.get("current_day_stamp"));
            long today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
            dailyRuns = day == today ? Math.min(5, asInt(daily.get("completed_runs_count"))) : 0;
        }
    }

    private static boolean bool(com.google.gson.JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    private static String resetsIn() {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant next = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1)
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        long minutes = java.time.Duration.between(now, next).toMinutes();
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private void drawExtras(GuiGraphicsExtractor ctx, Font tr, SkyblockProfile profile, DungeonData data,
                            int cx, int y, int mouseX, int mouseY) {
        if (!extra(true)) {
            calcY = -1000;
            targetFocused = false;
            return;
        }
        readTreasures(profile);

        String croesus = croesusRuns + "/60" + (croesusUnclaimed > 0 ? " (" + croesusUnclaimed + ")" : "");
        aligned(ctx, tr, "Croesus:", croesus, cx, y, croesusRuns >= 60 ? NeuStyle.TEXT_RED : NeuStyle.TEXT);
        if (hover(mouseX, mouseY, cx, y)) {
            setTooltip(mouseX, mouseY, "Croesus", "Runs stored: " + croesusRuns + "/60",
                    "Unclaimed runs: " + croesusUnclaimed);
        }

        aligned(ctx, tr, "Daily Runs:", dailyRuns + "/5", cx, y + 12,
                dailyRuns >= 5 ? NeuStyle.TEXT_GREEN : NeuStyle.TEXT_YELLOW);
        if (hover(mouseX, mouseY, cx, y + 12)) {
            setTooltip(mouseX, mouseY, "Daily Runs", "Completed today: " + dailyRuns + "/5", "Resets in " + resetsIn());
        }

        int current = data.getCatacombsLevel();
        if (targetLevel <= current) targetLevel = current + 1;
        int target = targetLevel;
        double needed = Math.max(0, DungeonData.xpForLevel(target) - data.getCatacombsXp());
        calcX = cx;
        calcY = y + 24;
        String label = "Until Cata " + (targetFocused ? targetText + "_" : String.valueOf(target)) + ":";
        ctx.text(tr, label, cx, calcY, targetFocused ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
        String value = formatXp(needed);
        ctx.text(tr, value, cx + SECTION - tr.width(value), calcY, NeuStyle.TEXT_GOLD);

        if (hover(mouseX, mouseY, cx, calcY)) {
            boolean ring = !Minecraft.getInstance().hasShiftDown();
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add("Until Cata " + target + ": " + com.teslamaps.profileviewer.ui.Kit.dots((long) needed) + " XP");
            lines.add(masterMode ? "Master Mode runs:" : "Floor runs:");
            int[][] floors = masterMode ? MASTER_XP : FLOOR_XP;
            java.util.Map<String, DungeonData.FloorData> done = masterMode ? data.getMasterFloors() : data.getNormalFloors();
            for (int[] floor : floors) {
                DungeonData.FloorData f = done.get(String.valueOf(floor[0]));
                int completions = Math.min(f == null ? 0 : f.getCompletions(), floor[2]);
                double xp = floor[1] * (completions / 100.0 + 1) * (ring ? 1.1 : 1.0);
                long runs = (long) Math.ceil(needed / xp);
                lines.add((masterMode ? "M" : "F") + floor[0] + " (" + formatXp(xp) + " xp): " + runs);
            }
            lines.add(ring ? "With Expert Ring, hold SHIFT without" : "Without Expert Ring");
            lines.add("Click to type a level, scroll to change");
            setTooltip(mouseX, mouseY, lines.toArray(new String[0]));
        }
    }

    // floor, base xp, completion bonus cap
    private static final int[][] FLOOR_XP = {{5, 2400, 150}, {6, 4880, 100}, {7, 28000, 50}};
    private static final int[][] MASTER_XP = {{3, 35000, 50}, {4, 55000, 50}, {5, 70000, 50}, {6, 100000, 50}, {7, 300000, 50}};

    private static boolean hover(int mouseX, int mouseY, int x, int y) {
        return mouseX > x && mouseX < x + SECTION && mouseY > y - 2 && mouseY < y + 10;
    }

    private void setTooltip(int mouseX, int mouseY, String... lines) {
        tooltip = lines;
        tooltipX = mouseX;
        tooltipY = mouseY;
    }

    private void commitTarget() {
        if (!targetText.isEmpty()) {
            try {
                targetLevel = Math.max(1, Math.min(500, Integer.parseInt(targetText)));
            } catch (NumberFormatException ignored) {
            }
        }
        targetFocused = false;
        targetText = "";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!hover((int) mouseX, (int) mouseY, calcX, calcY)) return false;
        targetLevel = Math.max(1, Math.min(500, targetLevel + (amount > 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean charTyped(char chr) {
        if (!targetFocused) return false;
        if (chr >= '0' && chr <= '9' && targetText.length() < 3) targetText += chr;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!targetFocused) return false;
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) {
            if (!targetText.isEmpty()) targetText = targetText.substring(0, targetText.length() - 1);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) targetText = "";
            commitTarget();
            return true;
        }
        return false;
    }

    private static final String[] FLOOR_KEYS = {"0", "1", "2", "3", "4", "5", "6", "7"};
    private static final String[] BOSSES = {"", "Bonzo", "Scarf", "Professor", "Thorn", "Livid", "Sadan", "Necron"};
    private static final java.time.format.DateTimeFormatter DATE =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH);

    private static int gradeColor(String grade) {
        return switch (grade) {
            case "S+" -> NeuStyle.TEXT_GOLD;
            case "S" -> NeuStyle.TEXT_AQUA;
            case "A" -> NeuStyle.TEXT_GREEN;
            case "B" -> NeuStyle.TEXT;
            default -> NeuStyle.TEXT_GRAY;
        };
    }

    private static String bestDate(DungeonData.FloorData floor) {
        if (floor.getBestTimestamp() <= 0) return "";
        return " - " + DATE.format(java.time.Instant.ofEpochMilli(floor.getBestTimestamp())
                .atZone(java.time.ZoneId.systemDefault()));
    }

    private void aligned(GuiGraphicsExtractor ctx, Font tr, String label, String value, int x, int y, int color) {
        ctx.text(tr, label, x, y, NeuStyle.TEXT_GRAY);
        ctx.text(tr, value, x + SECTION - tr.width(value), y, color);
    }
}
