package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.profileviewer.screen.ProfileViewerScreen;
import com.teslamaps.profileviewer.ui.Kit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Ported from skyblock-pv's CombatPage mobsColumn
public class MobsPage extends ProfileViewerPage {
    private static final int ROW = 10;

    private record Row(String name, String count) {}

    private List<Row> kills = List.of();
    private List<Row> deaths = List.of();
    private String killTotal = "0";
    private String deathTotal = "0";
    private final int[] scrolls = new int[2];

    @Override
    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        super.init(parent, profiles);
        SkyblockProfile profile = getProfile();
        JsonObject stats = profile == null ? null : object(profile.getMemberData(), "player_stats");
        long[] total = new long[1];
        kills = rows(object(stats, "kills"), total);
        killTotal = Kit.compact(total[0]);
        deaths = rows(object(stats, "deaths"), total);
        deathTotal = Kit.compact(total[0]);
        scrolls[0] = 0;
        scrolls[1] = 0;
    }

    private static List<Row> rows(JsonObject map, long[] total) {
        total[0] = 0;
        if (map == null) return List.of();
        List<Map.Entry<String, Long>> entries = new ArrayList<>();
        for (var e : map.entrySet()) {
            long n = asLong(e.getValue());
            if (n <= 0 || e.getKey().equals("total")) continue;
            entries.add(Map.entry(e.getKey(), n));
            total[0] += n;
        }
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<Row> out = new ArrayList<>(entries.size());
        Font tr = Minecraft.getInstance().font;
        for (var e : entries) {
            String count = Kit.compact(e.getValue());
            out.add(new Row(tr.plainSubstrByWidth(Kit.prettify(e.getKey()), columnWidth() - tr.width(count) - 6), count));
        }
        return out;
    }

    @Override
    public boolean isAvailable(SkyblockProfiles profiles) {
        var c = com.teslamaps.config.TeslaMapsConfig.get();
        return c.pvExtras && c.pvMobsTab;
    }

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Mobs";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.ZOMBIE_HEAD);
    }

    private int lastLeft;
    private int lastTop;

    private static int columnWidth() {
        return (NeuStyle.PANEL_WIDTH - 30) / 2;
    }

    private static int visibleRows() {
        return (NeuStyle.PANEL_HEIGHT - 36) / ROW;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        lastLeft = x - 8;
        lastTop = y - 8;
        int w = columnWidth();
        column(ctx, tr, "Kills", killTotal, kills, lastLeft + 10, lastTop + 8, w, 0, NeuStyle.TEXT_GREEN);
        NeuStyle.divider(ctx, lastLeft + 10 + w + 4, lastTop + 8, NeuStyle.PANEL_HEIGHT - 16);
        column(ctx, tr, "Deaths", deathTotal, deaths, lastLeft + 20 + w, lastTop + 8, w, 1, NeuStyle.TEXT_RED);
    }

    private void column(GuiGraphicsExtractor ctx, Font tr, String title, String total, List<Row> rows,
                        int x, int y, int w, int index, int color) {
        ctx.text(tr, title, x, y, color);
        ctx.text(tr, total, x + w - tr.width(total), y, NeuStyle.TEXT_GRAY);
        if (rows.isEmpty()) {
            ctx.text(tr, "Nothing tracked", x, y + 16, NeuStyle.TEXT_DARK);
            return;
        }
        int visible = visibleRows();
        int from = Math.min(scrolls[index], Math.max(0, rows.size() - visible));
        for (int i = 0; i < visible && from + i < rows.size(); i++) {
            Row row = rows.get(from + i);
            int ry = y + 16 + i * ROW;
            ctx.text(tr, row.count(), x + w - tr.width(row.count()), ry, NeuStyle.TEXT_AQUA);
            ctx.text(tr, row.name(), x, ry, NeuStyle.TEXT);
        }
        if (rows.size() > visible) {
            int track = visible * ROW;
            int thumb = Math.max(8, track * visible / rows.size());
            int ty = y + 16 + (track - thumb) * from / Math.max(1, rows.size() - visible);
            ctx.fill(x + w + 1, ty, x + w + 2, ty + thumb, 0x80FFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int index = mouseX < lastLeft + 10 + columnWidth() + 4 ? 0 : 1;
        int size = index == 0 ? kills.size() : deaths.size();
        int max = Math.max(0, size - visibleRows());
        scrolls[index] = (int) Math.max(0, Math.min(max, scrolls[index] - Math.signum(amount) * 3));
        return true;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }
}
