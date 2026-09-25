package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import com.teslamaps.profileviewer.ui.Kit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BingoPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int CELL_SIZE = 40;
    private static final int GRID_SIZE = 5;

    private JsonObject bingoData;
    private boolean loading = true;

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Bingo";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.FILLED_MAP);
    }

    @Override
    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        loadBingoData();
    }

    /** Only a bingo profile has a board, everyone else never sees the tab. */
    @Override
    public boolean isAvailable(SkyblockProfiles profiles) {
        if (profiles == null || profiles.getSelectedProfile() == null) return false;
        JsonObject full = profiles.getSelectedProfile().getFullProfileJson();
        return full != null && full.has("game_mode") && full.get("game_mode").isJsonPrimitive()
                && "bingo".equalsIgnoreCase(full.get("game_mode").getAsString());
    }

    private void loadBingoData() {
        if (profiles == null) return;
        loading = true;
        cells = null;
        goals = null;
        HypixelApi.getBingo(profiles.getUuid()).thenAccept(data -> {
            this.bingoData = data;
            this.loading = false;
        });
        if (extra(com.teslamaps.config.TeslaMapsConfig.get().pvBingoGoals)) {
            HypixelApi.getBingoGoals().thenAccept(data -> this.goals = data);
        }
    }

    // Ported from NEU's profileviewer/BingoPage.java
    private record Cell(List<net.minecraft.util.FormattedCharSequence> name, boolean done, boolean community,
                        List<net.minecraft.network.chat.Component> tooltip) {}

    private volatile JsonObject goals;
    private List<Cell> cells;
    private List<net.minecraft.network.chat.Component> sideLines = List.of();

    private void buildCells(Font tr) {
        JsonArray list = goals.has("goals") && goals.get("goals").isJsonArray() ? goals.getAsJsonArray("goals") : new JsonArray();
        int eventId = goals.has("id") && goals.get("id").isJsonPrimitive() ? goals.get("id").getAsInt() : -1;

        Set<String> completed = new HashSet<>();
        int points = 0;
        int totalPoints = 0;
        boolean current = false;
        JsonArray events = bingoData != null && bingoData.has("events") && bingoData.get("events").isJsonArray()
                ? bingoData.getAsJsonArray("events") : new JsonArray();
        for (JsonElement e : events) {
            if (e.isJsonObject()) totalPoints += asInt(e.getAsJsonObject().get("points"));
        }
        if (!events.isEmpty() && events.get(events.size() - 1).isJsonObject()) {
            JsonObject last = events.get(events.size() - 1).getAsJsonObject();
            current = asInt(last.get("key")) == eventId;
            if (current) {
                points = asInt(last.get("points"));
                if (last.has("completed_goals") && last.get("completed_goals").isJsonArray()) {
                    for (JsonElement g : last.getAsJsonArray("completed_goals")) {
                        if (g.isJsonPrimitive()) completed.add(g.getAsString());
                    }
                }
            }
        }

        List<Cell> out = new ArrayList<>();
        List<String> community = new ArrayList<>();
        int personal = 0;
        for (JsonElement element : list) {
            if (!element.isJsonObject()) continue;
            JsonObject goal = element.getAsJsonObject();
            String name = goal.has("name") ? goal.get("name").getAsString() : "?";
            List<String> tip = new ArrayList<>();
            boolean isCommunity = goal.has("tiers") && goal.get("tiers").isJsonArray();
            boolean done;
            if (isCommunity) {
                JsonArray tiers = goal.getAsJsonArray("tiers");
                long progress = goal.has("progress") ? (long) goal.get("progress").getAsDouble() : 0;
                int tier = 0;
                for (JsonElement t : tiers) if (progress >= t.getAsLong()) tier++;
                done = tier == tiers.size();
                long next = tiers.isEmpty() ? 0 : tiers.get(Math.min(tier, tiers.size() - 1)).getAsLong();
                tip.add("§a" + name + " " + tier);
                tip.add("§8Community Goal");
                tip.add("");
                tip.add("§7Progress: §e" + Kit.compact(progress) + "§6/§e" + Kit.compact(next));
                if (done) tip.add("§aGOAL REACHED");
                community.add("§b" + name + " §7Tier §f" + tier + "§7/§f" + tiers.size());
            } else {
                done = completed.contains(goal.has("id") ? goal.get("id").getAsString() : "");
                if (done) personal++;
                tip.add("§a" + name);
                tip.add("§8Personal Goal");
                tip.add("");
                if (goal.has("lore") && goal.get("lore").isJsonPrimitive()) {
                    for (String line : goal.get("lore").getAsString().split("\n")) tip.add("§7" + line);
                    tip.add("");
                }
                tip.add("§7Reward: §61 Bingo Point");
                tip.add(done ? "§aGOAL REACHED" : "§cNot reached yet");
            }
            List<net.minecraft.network.chat.Component> parsed = new ArrayList<>(tip.size());
            for (String line : tip) parsed.add(com.teslamaps.profileviewer.data.LegacyText.parse(line));
            List<net.minecraft.util.FormattedCharSequence> lines = tr.split(net.minecraft.network.chat.Component.literal(name), 72);
            if (lines.size() > 4) lines = lines.subList(0, 4);
            out.add(new Cell(lines, done, isCommunity, parsed));
        }

        List<String> side = new ArrayList<>();
        if (goals.has("name") && goals.get("name").isJsonPrimitive()) side.add("§e" + goals.get("name").getAsString());
        if (!current) side.add("§cNo data for this event");
        side.add("§bPoints: §f" + points);
        side.add("§bPersonal: §f" + personal + "§6/§f20");
        side.add("§bAll events: §f" + totalPoints);
        if (!community.isEmpty()) {
            side.add("");
            side.add("§7Community Goals");
            side.addAll(community);
        }
        List<net.minecraft.network.chat.Component> parsed = new ArrayList<>(side.size());
        for (String line : side) {
            parsed.add(com.teslamaps.profileviewer.data.LegacyText.parse(tr.plainSubstrByWidth(line, 118)));
        }
        sideLines = parsed;
        cells = out;
    }

    private void renderNamed(GuiGraphicsExtractor ctx, Font tr, int left, int top, int mouseX, int mouseY) {
        if (cells == null) buildCells(tr);
        int sy = top + 38;
        for (net.minecraft.network.chat.Component line : sideLines) {
            ctx.text(tr, line, left + 8, sy, -1);
            sy += 10;
        }

        int cw = 58;
        int ch = 37;
        int gx = left + 128;
        int gy = top + 8;
        for (int i = 0; i < cells.size() && i < 25; i++) {
            Cell cell = cells.get(i);
            int cx = gx + (i % 5) * cw;
            int cy = gy + (i / 5) * ch;
            int bg = cell.done() ? 0xFF225522 : cell.community() ? 0xFF222844 : 0xFF333333;
            ctx.fill(cx, cy, cx + cw - 2, cy + ch - 2, bg);
            drawBorder(ctx, cx, cy, cw - 2, ch - 2, cell.done() ? TEXT_GREEN : cell.community() ? 0xFF5555FF : 0xFF555555);
            int lineY = cy + (ch - 2 - cell.name().size() * 7) / 2;
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx + 2, lineY);
            ctx.pose().scale(0.75f, 0.75f);
            for (int l = 0; l < cell.name().size(); l++) {
                ctx.text(tr, cell.name().get(l), 0, l * 9, cell.done() ? TEXT_GREEN : TEXT_WHITE);
            }
            ctx.pose().popMatrix();
            if (mouseX >= cx && mouseX < cx + cw - 2 && mouseY >= cy && mouseY < cy + ch - 2 && parent != null) {
                parent.setHoveredLines(cell.tooltip());
            }
        }
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        int padding = 15;
        int contentX = x + padding;

        int lineY = y + padding;

        ctx.text(tr, "Bingo Card", contentX, lineY, TEXT_GREEN);
        lineY += 20;

        if (loading) {
            ctx.text(tr, "Loading bingo data...", contentX, lineY, TEXT_GRAY);
            return;
        }

        if (bingoData == null || bingoData.has("error")) {
            String error = bingoData != null && bingoData.has("error") ?
                    bingoData.get("error").getAsString() : "Failed to load bingo data";
            ctx.text(tr, error, contentX, lineY, TEXT_GRAY);
            return;
        }

        if (goals != null && goals.has("goals") && extra(com.teslamaps.config.TeslaMapsConfig.get().pvBingoGoals)) {
            renderNamed(ctx, tr, x - 8, y - 8, mouseX, mouseY);
            return;
        }

        Set<Integer> completedGoals = new HashSet<>();
        if (bingoData.has("events")) {
            JsonArray events = bingoData.getAsJsonArray("events");
            if (!events.isEmpty()) {
                JsonObject latestEvent = events.get(events.size() - 1).getAsJsonObject();
                if (latestEvent.has("completed_goals")) {
                    JsonArray completed = latestEvent.getAsJsonArray("completed_goals");
                    for (JsonElement elem : completed) {
                        if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isNumber()) completedGoals.add(elem.getAsInt());
                    }
                }
            }
        }

        int gridX = contentX + (width - padding * 2 - CELL_SIZE * GRID_SIZE) / 2;
        int gridY = lineY;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int goalIndex = row * GRID_SIZE + col;
                int cellX = gridX + col * CELL_SIZE;
                int cellY = gridY + row * CELL_SIZE;

                boolean completed = completedGoals.contains(goalIndex);

                int bgColor = completed ? 0xFF225522 : 0xFF333333;
                ctx.fill(cellX, cellY, cellX + CELL_SIZE - 2, cellY + CELL_SIZE - 2, bgColor);

                int borderColor = completed ? TEXT_GREEN : 0xFF555555;
                drawBorder(ctx, cellX, cellY, CELL_SIZE - 2, CELL_SIZE - 2, borderColor);

                String num = String.valueOf(goalIndex + 1);
                int numX = cellX + (CELL_SIZE - 2 - tr.width(num)) / 2;
                int numY = cellY + (CELL_SIZE - 2 - 8) / 2;
                ctx.text(tr, num, numX, numY, completed ? TEXT_GREEN : TEXT_GRAY);

                if (completed) {
                    ctx.text(tr, "", cellX + 2, cellY + 2, TEXT_GREEN);
                }
            }
        }

        int statsY = gridY + GRID_SIZE * CELL_SIZE + 15;
        int completedCount = completedGoals.size();
        int totalGoals = GRID_SIZE * GRID_SIZE;

        ctx.text(tr, "Completed: " + completedCount + "/" + totalGoals,
                contentX, statsY, TEXT_WHITE);

        int bingos = countBingos(completedGoals);
        if (bingos > 0) {
            ctx.text(tr, "Bingos: " + bingos, contentX + 150, statsY, TEXT_GOLD);
        }
    }

    private int countBingos(Set<Integer> completed) {
        int count = 0;

        for (int row = 0; row < GRID_SIZE; row++) {
            boolean bingo = true;
            for (int col = 0; col < GRID_SIZE; col++) {
                if (!completed.contains(row * GRID_SIZE + col)) {
                    bingo = false;
                    break;
                }
            }
            if (bingo) count++;
        }

        for (int col = 0; col < GRID_SIZE; col++) {
            boolean bingo = true;
            for (int row = 0; row < GRID_SIZE; row++) {
                if (!completed.contains(row * GRID_SIZE + col)) {
                    bingo = false;
                    break;
                }
            }
            if (bingo) count++;
        }

        boolean diag1 = true, diag2 = true;
        for (int i = 0; i < GRID_SIZE; i++) {
            if (!completed.contains(i * GRID_SIZE + i)) diag1 = false;
            if (!completed.contains(i * GRID_SIZE + (GRID_SIZE - 1 - i))) diag2 = false;
        }
        if (diag1) count++;
        if (diag2) count++;

        return count;
    }
}
