package com.teslamaps.screen;

import com.teslamaps.features.ItemIndex;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /tmap search - finds an item in every container the mod has seen. */
public class ItemSearchScreen extends Screen {

    private static final int ROW_H = 20, MARGIN = 10;
    private static final int MAX_ROWS_KEPT = 300;

    private record Where(String source, String type, int count, net.minecraft.core.BlockPos pos) {}
    private record Row(ItemStack stack, String name, long total, List<Where> where) {}

    private static String lastQuery = "";

    private EditBox field;
    private List<Row> rows = new ArrayList<>();
    private int scroll = 0;
    private String status = "";

    private int panelX, panelY, panelW, panelH, listY, listH, visibleRows;

    public ItemSearchScreen(String query) {
        super(Component.literal("Item Search"));
        if (query != null && !query.isBlank()) lastQuery = query.trim();
    }

    @Override
    protected void init() {
        panelW = Math.min(width - MARGIN * 2, 460);
        panelH = Math.min(height - MARGIN * 2, 300);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        listY = panelY + 62;
        listH = panelH - 62 - 34;
        visibleRows = Math.max(1, listH / ROW_H);

        field = new EditBox(font, panelX + 12, panelY + 34, panelW - 24, 16, Component.literal("Search"));
        field.setHint(Component.literal("Item name..."));
        field.setMaxLength(64);
        field.setResponder(q -> {
            lastQuery = q;
            scroll = 0;
            rebuild();
        });
        field.setValue(lastQuery);
        addRenderableWidget(field);
        setInitialFocus(field);

        int by = panelY + panelH - 26;
        addRenderableWidget(new TeslaButton(panelX + panelW - 74, by, 62, 18, "§fClose", this::onClose));

        rebuild();
        ScreenFit.clamp(this);
    }

    private void rebuild() {
        rows = new ArrayList<>();
        String q = lastQuery == null ? "" : lastQuery.trim();
        if (q.isEmpty()) {
            status = "§8" + ItemIndex.sourceCount() + " containers · " + ItemIndex.itemCount() + " items indexed";
            return;
        }

        Map<String, List<ItemIndex.Hit>> grouped = new LinkedHashMap<>();
        for (ItemIndex.Hit hit : ItemIndex.search(q, null)) {
            String key = com.teslamaps.utils.StringUtil.strip(hit.stack().getHoverName().getString());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(hit);
        }

        for (Map.Entry<String, List<ItemIndex.Hit>> e : grouped.entrySet()) {
            long total = 0;
            Map<String, Where> bySource = new LinkedHashMap<>();
            for (ItemIndex.Hit hit : e.getValue()) {
                total += hit.count();
                Where prev = bySource.get(hit.source());
                bySource.put(hit.source(), prev == null
                        ? new Where(hit.source(), hit.type(), hit.count(), hit.pos())
                        : new Where(hit.source(), hit.type(), prev.count() + hit.count(), prev.pos()));
            }
            List<Where> where = new ArrayList<>(bySource.values());
            where.sort((a, b) -> Integer.compare(b.count(), a.count()));
            rows.add(new Row(e.getValue().get(0).stack(), e.getKey(), total, where));
        }
        rows.sort((a, b) -> Long.compare(b.total(), a.total()));
        if (rows.size() > MAX_ROWS_KEPT) rows = new ArrayList<>(rows.subList(0, MAX_ROWS_KEPT));
        status = rows.isEmpty()
                ? "§7Nothing found - open the container once so it gets indexed"
                : "§8" + rows.size() + " items · " + ItemIndex.sourceCount() + " containers indexed";
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, rows.size() - visibleRows);
        scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, max);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && field != null && field.isMouseOver(event.x(), event.y())) {
            field.setValue("");
            field.setFocused(true);
            return true;
        }
        int index = rowAt(event.x(), event.y());
        if (index >= 0) {
            Row row = rows.get(index);
            boolean chest = false;
            for (Where w : row.where()) {
                if (w.pos() == null) continue;
                com.teslamaps.features.SearchWaypoint.add(w.pos(), row.name());
                chest = true;
            }
            if (chest) {
                onClose();
                return true;
            }
            for (Where w : row.where()) {
                String cmd = commandFor(w);
                if (cmd == null) continue;
                if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand(cmd);
                onClose();
                return true;
            }
            minecraft.keyboardHandler.setClipboard(row.name());
            status = "§aCopied " + row.name();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && field != null && field.isFocused()
                && !field.getValue().isEmpty()) {
            field.setValue("");
            return true;
        }
        return super.keyPressed(event);
    }

    /** Command (without slash) that opens the container this hit sits in, null if there is none. */
    private static String commandFor(Where w) {
        if (ItemIndex.MUSEUM.equals(w.type())) return "warp museum";
        if (!ItemIndex.STORAGE.equals(w.type())) return null;
        if (w.source().startsWith("Backpack ")) return "backpack " + w.source().substring(9);
        if (w.source().startsWith("Ender Chest ")) return "enderchest " + w.source().substring(12);
        return null;
    }

    private int rowAt(double mx, double my) {
        if (mx < panelX + 8 || mx > panelX + panelW - 8) return -1;
        if (my < listY || my >= listY + visibleRows * ROW_H) return -1;
        int index = scroll + (int) ((my - listY) / ROW_H);
        return index >= 0 && index < rows.size() ? index : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);

        ctx.text(font, "§f§lItem Search", panelX + 12, panelY + 12, 0xFFFFFFFF);

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= rows.size()) break;
            Row row = rows.get(index);
            int y = listY + i * ROW_H;
            if (index == hovered) ctx.fill(panelX + 8, y, panelX + panelW - 8, y + ROW_H, 0x30FFFFFF);

            ctx.item(row.stack(), panelX + 10, y + 2);
            String count = row.total() > 1 ? " §7x" + fmt(row.total()) : "";
            ctx.text(font, "§f" + trim(row.name(), 26) + count, panelX + 30, y + 6, 0xFFFFFFFF);

            String src = sources(row.where());
            ctx.text(font, "§8" + src, panelX + panelW - 12 - font.width(src), y + 6, 0xFF909090);
        }

        if (!status.isEmpty()) ctx.text(font, status, panelX + 12, panelY + panelH - 40, 0xFFAAAAAA);
        if (rows.size() > visibleRows) {
            String pos = "§8" + (scroll + 1) + "-" + Math.min(rows.size(), scroll + visibleRows) + "/" + rows.size();
            ctx.text(font, pos, panelX + panelW - 12 - font.width(pos), panelY + panelH - 40, 0xFF808080);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        if (hovered >= 0) {
            Row row = rows.get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(row.stack().getHoverName());
            lines.add(Component.literal("§8found in"));
            for (Where w : row.where()) {
                lines.add(Component.literal("§7 " + w.source() + " §8x" + fmt(w.count()) + " §8(" + w.type() + ")"));
            }
            boolean chest = row.where().stream().anyMatch(w -> w.pos() != null);
            String hint = "§8click to copy the name";
            if (chest) {
                hint = "§8click for a waypoint on the chest";
            } else {
                for (Where w : row.where()) {
                    if (commandFor(w) == null) continue;
                    hint = "§8click to open " + w.source();
                    break;
                }
            }
            lines.add(Component.literal(hint));
            ctx.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private static String sources(List<Where> where) {
        if (where.size() == 1) return trim(where.get(0).source(), 22);
        return trim(where.get(0).source(), 16) + " +" + (where.size() - 1);
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String fmt(long v) {
        if (v < 1000) return String.valueOf(v);
        if (v < 1_000_000) return String.format(Locale.ROOT, "%.1fk", v / 1000.0);
        return String.format(Locale.ROOT, "%.1fm", v / 1_000_000.0);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
