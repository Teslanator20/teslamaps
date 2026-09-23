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
        for (String line : tooltip) w = Math.max(w, tr.width(line));
        int h = tooltip.length * 10 + 4;
        int tx = Math.min(tooltipX + 8, NeuStyle.PANEL_WIDTH - w - 8);
        int ty = Math.max(2, tooltipY - h);
        NeuStyle.window(ctx, tx - 3, ty - 2, w + 8, h);
        for (int i = 0; i < tooltip.length; i++) {
            ctx.text(tr, tooltip[i], tx, ty + 2 + i * 10, i == 0 ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
        }
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
