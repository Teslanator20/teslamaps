package com.teslamaps.features.inspector;

import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.screen.ScreenFit;
import com.teslamaps.screen.TeslaButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /tmap debug inspect — entity list on the left, rows of the selected entity or item on the right. */
public class InspectorScreen extends Screen {

    private static final int ROW_H = 11, MARGIN = 10, LIST_W = 170;

    private static boolean raw = false;
    private static String lastFilter = "";

    private final ItemStack item;
    private final Screen parent;
    private final int radius;
    private final List<Entity> entities = new ArrayList<>();
    private final List<String> entityLabels = new ArrayList<>();
    private List<Inspector.Row> rows = List.of();
    private Entity selected;
    private EditBox filter;
    private TeslaButton rawBtn;
    private int listScroll, rowScroll, ticks;
    private String status = "";
    private long statusUntil;

    private int px, py, pw, ph, top, visible;

    public InspectorScreen(Entity focus, int radius) {
        super(Component.literal("Entity Inspector"));
        this.item = null;
        this.parent = null;
        this.radius = radius;
        this.selected = focus;
    }

    public InspectorScreen(ItemStack stack, Screen parent) {
        super(Component.literal("Item Inspector"));
        this.item = stack;
        this.parent = parent;
        this.radius = 0;
    }

    private boolean entityMode() { return item == null; }

    @Override
    protected void init() {
        pw = Math.min(width - MARGIN * 2, 720);
        ph = Math.min(height - MARGIN * 2, 380);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        top = py + 54;
        visible = Math.max(1, (ph - 54 - 32) / ROW_H);

        int bw = 64;
        int bx = px + pw - 12 - (bw + 4) * 3 + 4;
        if (entityMode()) {
            filter = new EditBox(font, px + 12, py + 30, LIST_W - 8, 16, Component.literal("Filter"));
            filter.setHint(Component.literal("Filter type / name..."));
            filter.setMaxLength(64);
            filter.setValue(lastFilter);
            filter.setResponder(v -> { lastFilter = v; listScroll = 0; refresh(); });
            addRenderableWidget(filter);
        }
        rawBtn = addRenderableWidget(new TeslaButton(bx, py + 30, bw, 16, rawLabel(), () -> {
            raw = !raw;
            rawBtn.setMessage(Component.literal(rawLabel()));
            rebuildRows();
        }));
        addRenderableWidget(new TeslaButton(bx + bw + 4, py + 30, bw, 16, "§fCopy All", this::copyAll));
        addRenderableWidget(new TeslaButton(bx + (bw + 4) * 2, py + 30, bw, 16, "§fClose", this::onClose));
        refresh();
        ScreenFit.clamp(this);
    }

    private static String rawLabel() { return raw ? "Raw: §aOn" : "Raw: §7Off"; }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 10 == 0) refresh();
    }

    private void refresh() {
        if (entityMode()) {
            Minecraft mc = Minecraft.getInstance();
            entities.clear();
            entityLabels.clear();
            if (mc.player != null && mc.level != null) {
                String f = lastFilter.toLowerCase(Locale.ROOT).trim();
                List<Entity> all = new ArrayList<>(mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(radius), e -> true));
                all.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
                for (Entity e : all) {
                    String type = Inspector.typeName(e);
                    String name = Inspector.summary(e);
                    if (!f.isEmpty() && !type.contains(f) && !name.toLowerCase(Locale.ROOT).contains(f)) continue;
                    entities.add(e);
                    entityLabels.add(String.format(Locale.ROOT, "§8%4.1f §7%s §f%s", Math.sqrt(e.distanceToSqr(mc.player)), type, name));
                }
            }
            if (selected != null && (selected.isRemoved() || mc.level == null || mc.level.getEntity(selected.getId()) != selected)) selected = null;
            Inspector.highlighted = selected == null ? -1 : selected.getId();
        }
        rebuildRows();
    }

    private void rebuildRows() {
        rows = entityMode() ? Inspector.entity(selected, raw) : Inspector.item(item, raw);
        rowScroll = Math.min(rowScroll, Math.max(0, rows.size() - visible));
    }

    private void copyAll() {
        StringBuilder sb = new StringBuilder();
        for (Inspector.Row r : rows) {
            if (r.header()) sb.append("== ").append(r.label()).append(" ==\n");
            else sb.append(r.label()).append(": ").append(r.copy()).append('\n');
        }
        minecraft.keyboardHandler.setClipboard(sb.toString());
        flash("§aCopied " + rows.size() + " rows");
    }

    private void flash(String s) {
        status = s;
        statusUntil = System.currentTimeMillis() + 1500;
    }

    private int detailX() { return entityMode() ? px + 12 + LIST_W + 8 : px + 12; }

    private int listIndex(double mx, double my) {
        if (!entityMode() || mx < px + 8 || mx >= px + 12 + LIST_W) return -1;
        if (my < top || my >= top + visible * ROW_H) return -1;
        int i = listScroll + (int) ((my - top) / ROW_H);
        return i < entities.size() ? i : -1;
    }

    private int rowIndex(double mx, double my) {
        if (mx < detailX() - 4 || mx > px + pw - 8) return -1;
        if (my < top || my >= top + visible * ROW_H) return -1;
        int i = rowScroll + (int) ((my - top) / ROW_H);
        return i < rows.size() ? i : -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int step = (int) Math.signum(sy) * 3;
        if (listIndex(mx, my) >= 0 || (entityMode() && mx < detailX() - 4)) {
            listScroll = Math.clamp(listScroll - step, 0, Math.max(0, entities.size() - visible));
        } else {
            rowScroll = Math.clamp(rowScroll - step, 0, Math.max(0, rows.size() - visible));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int li = listIndex(event.x(), event.y());
        if (li >= 0) {
            selected = entities.get(li);
            Inspector.highlighted = selected.getId();
            rowScroll = 0;
            rebuildRows();
            return true;
        }
        int ri = rowIndex(event.x(), event.y());
        if (ri >= 0) {
            Inspector.Row r = rows.get(ri);
            minecraft.keyboardHandler.setClipboard(r.copy());
            flash("§aCopied §f" + r.label());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x90000000);
        HudStyle.screenPanel(g, px, py, pw, ph, 8);
        String title = entityMode()
                ? "§f§lEntity Inspector §8(" + entities.size() + " within " + radius + ")"
                : "§f§lItem Inspector §8" + com.teslamaps.utils.StringUtil.strip(item.getHoverName().getString());
        g.text(font, font.plainSubstrByWidth(title, pw - 24), px + 12, py + 12, 0xFFFFFFFF);

        if (entityMode()) {
            int hl = listIndex(mouseX, mouseY);
            for (int i = 0; i < visible; i++) {
                int idx = listScroll + i;
                if (idx >= entities.size()) break;
                int y = top + i * ROW_H;
                if (entities.get(idx) == selected) g.fill(px + 8, y - 1, px + 12 + LIST_W, y + ROW_H - 1, 0x5055FFFF);
                else if (idx == hl) g.fill(px + 8, y - 1, px + 12 + LIST_W, y + ROW_H - 1, 0x30FFFFFF);
                g.text(font, font.plainSubstrByWidth(entityLabels.get(idx), LIST_W - 2), px + 12, y, 0xFFFFFFFF);
            }
            g.fill(detailX() - 5, top - 2, detailX() - 4, top + visible * ROW_H, 0x40FFFFFF);
            if (selected == null) g.text(font, "§7Pick an entity on the left", detailX(), top, 0xFFAAAAAA);
        }

        int dx = detailX();
        int dw = px + pw - 12 - dx;
        int labelW = Math.max(70, dw * 2 / 5);
        int hr = rowIndex(mouseX, mouseY);
        for (int i = 0; i < visible; i++) {
            int idx = rowScroll + i;
            if (idx >= rows.size()) break;
            Inspector.Row r = rows.get(idx);
            int y = top + i * ROW_H;
            if (idx == hr) g.fill(dx - 4, y - 1, px + pw - 8, y + ROW_H - 1, 0x30FFFFFF);
            if (r.header()) {
                g.text(font, "§6§l" + r.label(), dx, y, 0xFFFFAA00);
                continue;
            }
            g.text(font, font.plainSubstrByWidth(r.label(), labelW - 4), dx, y, 0xFF909090);
            int col = r.color() == 0 ? 0xFFFFFFFF : r.color();
            g.text(font, font.plainSubstrByWidth(r.value(), dw - labelW), dx + labelW, y, col, false);
        }

        String foot = System.currentTimeMillis() < statusUntil ? status : "§8click a row to copy · scroll each column";
        g.text(font, foot, px + 12, py + ph - 20, 0xFFAAAAAA);
        if (rows.size() > visible) {
            String pos = "§8" + (rowScroll + 1) + "-" + Math.min(rows.size(), rowScroll + visible) + "/" + rows.size();
            g.text(font, pos, px + pw - 12 - font.width(pos), py + ph - 20, 0xFF808080);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);

        if (hr >= 0 && !rows.get(hr).header()) {
            Inspector.Row r = rows.get(hr);
            List<Component> lines = new ArrayList<>();
            String full = r.copy().length() > 1500 ? r.copy().substring(0, 1500) + "…" : r.copy();
            for (var part : font.getSplitter().splitLines(full, 320, net.minecraft.network.chat.Style.EMPTY))
                lines.add(Component.literal(part.getString()));
            lines.add(Component.literal("§8click to copy"));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        Inspector.closed();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
