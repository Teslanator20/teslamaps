package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.esp.StarredMobESP;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.render.gui.HudStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * `/tmap esp` - the custom ESP entries with their tracer flag and colour. Rows are drawn
 * flat in the config screen's idiom rather than as buttons, so a list of them does not
 * turn into a wall of chrome.
 */
public class EspListScreen extends Screen {
    private static final int PANEL_W = 460, PANEL_H = 300;
    private static final int ROW_H = 26, VISIBLE = 7;
    private static final int SWATCH_W = 26, TRACER_W = 58, REMOVE_W = 20, GAP = 6;
    private static final int FOOTER_H = 22, ADD_W = 92, DEFAULT_W = 96, DONE_W = 60;

    private int panelX, panelY, listX, listY, contentW, nameW;
    private int swatchX, tracerX, removeX;
    private int addX, defaultX, doneX, footerY;
    private int scroll = 0;

    /** Live count per entry, refreshed once a frame instead of per row. */
    private Map<String, Integer> counts = Map.of();

    public EspListScreen() { super(Component.literal("Custom ESP")); }

    private List<String> list() { return TeslaMapsConfig.get().customESPMobs; }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 58;
        contentW = PANEL_W - 32 - 10;                 // 10 for the scrollbar gutter
        nameW = contentW - SWATCH_W - TRACER_W - REMOVE_W - GAP * 3;
        swatchX = listX + nameW + GAP;
        tracerX = swatchX + SWATCH_W + GAP;
        removeX = tracerX + TRACER_W + GAP;

        footerY = panelY + PANEL_H - 32;
        addX = listX;
        defaultX = addX + ADD_W + 8;
        doneX = panelX + PANEL_W - 16 - DONE_W;

        rebuild();

        ScreenFit.clamp(this);
    }

    /** Only the name fields are real widgets; everything else is drawn and hit-tested by hand. */
    private void rebuild() {
        clearWidgets();
        List<String> b = list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, b.size() - VISIBLE)));

        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= b.size()) break;
            int ry = listY + row * ROW_H;

            EditBox name = new EditBox(this.font, listX + 6, ry + 7, nameW - 34, 12, Component.literal("name"));
            name.setMaxLength(64);
            name.setBordered(false);
            name.setHint(Component.literal("Pitfighter"));
            name.setValue(b.get(idx));
            name.setResponder(v -> renameEntry(idx, v));
            addRenderableWidget(name);
        }
    }

    /** Carries the tracer flag and the colour over when a row is retyped. */
    private void renameEntry(int idx, String value) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        List<String> b = list();
        if (idx >= b.size()) return;
        String old = b.get(idx);
        if (value.equals(old)) return;

        String color = cfg.customESPColors.remove(old.toLowerCase());
        if (color != null && !value.isBlank()) cfg.customESPColors.put(value.toLowerCase(), color);

        boolean tracer = cfg.customESPTracers.removeIf(s -> s.equalsIgnoreCase(old));
        if (tracer && !value.isBlank()) cfg.customESPTracers.add(value);

        b.set(idx, value);
    }

    private boolean hasTracer(String entry) {
        return TeslaMapsConfig.get().customESPTracers.stream().anyMatch(s -> s.equalsIgnoreCase(entry));
    }

    private String colorHex(String entry) {
        String hex = TeslaMapsConfig.get().customESPColors.get(entry.toLowerCase());
        return hex == null ? TeslaMapsConfig.get().colorESPCustom : hex;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (list().size() > VISIBLE) {
            scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, list().size() - VISIBLE);
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        double mx = event.x(), my = event.y();
        List<String> b = list();

        if (in(mx, my, addX, footerY, ADD_W, FOOTER_H)) {
            GuiSounds.press();
            b.add("");
            scroll = Math.max(0, b.size() - VISIBLE);
            rebuild();
            return true;
        }
        if (in(mx, my, defaultX, footerY, DEFAULT_W, FOOTER_H)) {
            GuiSounds.press();
            minecraft.gui.setScreen(new ColorPickerWidget(this, cfg.colorESPCustom, hex -> {
                cfg.colorESPCustom = hex;
                TeslaMapsConfig.save();
            }));
            return true;
        }
        if (in(mx, my, doneX, footerY, DONE_W, FOOTER_H)) {
            GuiSounds.press();
            onClose();
            return true;
        }

        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= b.size()) break;
            int ry = listY + row * ROW_H;
            if (my < ry || my >= ry + ROW_H) continue;

            if (in(mx, my, swatchX, ry, SWATCH_W, 22)) {
                GuiSounds.press();
                final String entry = b.get(idx);
                minecraft.gui.setScreen(new ColorPickerWidget(this, colorHex(entry), hex -> {
                    if (!entry.isBlank()) cfg.customESPColors.put(entry.toLowerCase(), hex);
                    TeslaMapsConfig.save();
                }));
                return true;
            }
            if (in(mx, my, tracerX, ry, TRACER_W, 22)) {
                String entry = b.get(idx);
                boolean was = cfg.customESPTracers.removeIf(s -> s.equalsIgnoreCase(entry));
                if (!was && !entry.isBlank()) cfg.customESPTracers.add(entry);
                GuiSounds.toggle(!was);
                TeslaMapsConfig.save();
                return true;
            }
            if (in(mx, my, removeX, ry, REMOVE_W, 22)) {
                GuiSounds.press();
                String entry = b.remove(idx);
                cfg.customESPTracers.removeIf(s -> s.equalsIgnoreCase(entry));
                cfg.customESPColors.remove(entry.toLowerCase());
                TeslaMapsConfig.save();
                rebuild();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§f§lCustom ESP", panelX + 16, panelY + 16, GuiStyle.text());
        ctx.text(this.font, "§8matches any entity whose name contains the text · green = seen right now",
                panelX + 16, panelY + 30, GuiStyle.text());

        counts = StarredMobESP.customEntryCounts();
        List<String> b = list();

        if (b.isEmpty()) {
            ctx.text(this.font, "§7No entries — click \"+ Add Entry\" or use §f/tmap esp add <name>§7.",
                    listX, listY + 6, GuiStyle.text());
        }

        // rows first, the name fields are drawn over them by super
        List<int[]> rowRects = new ArrayList<>();
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= b.size()) break;
            int ry = listY + row * ROW_H;
            drawRow(ctx, idx, ry, mouseX, mouseY);
            rowRects.add(new int[] {idx, ry});
        }

        drawScrollbar(ctx, b.size());
        drawFooter(ctx, mouseX, mouseY);


        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // over the name field: the live match count, right-aligned in its cell
        for (int[] r : rowRects) {
            String entry = b.get(r[0]);
            String label;
            if (entry.isBlank()) {
                label = "§c!";
            } else {
                int n = counts.getOrDefault(entry, 0);
                label = n > 0 ? "§a" + n : "§80";
            }
            ctx.text(this.font, label, listX + nameW - 8 - this.font.width(label), r[1] + 7, GuiStyle.text());
        }
    }

    private void drawRow(GuiGraphicsExtractor ctx, int idx, int ry, int mouseX, int mouseY) {
        String entry = list().get(idx);
        boolean rowHovered = mouseX >= listX && mouseX < listX + contentW && mouseY >= ry && mouseY < ry + ROW_H;
        if (rowHovered) {
            ctx.fill(listX, ry, listX + contentW, ry + ROW_H, GuiStyle.rowHover());
        }

        // name field
        ctx.fill(listX, ry + 2, listX + nameW, ry + ROW_H - 2, GuiStyle.input());
        drawBorder(ctx, listX, ry + 2, nameW, ROW_H - 4, GuiStyle.border());

        // colour swatch
        boolean swatchHovered = in(mouseX, mouseY, swatchX, ry, SWATCH_W, 22);
        int sx = swatchX + 2, sy = ry + 6, sw = SWATCH_W - 4, sh = 14;
        ctx.fill(sx, sy, sx + sw, sy + sh, StarredMobESP.customColor(entry));
        drawBorder(ctx, sx, sy, sw, sh, swatchHovered ? GuiStyle.accent() : GuiStyle.border());

        // tracer toggle
        boolean tracer = hasTracer(entry);
        boolean tracerHovered = in(mouseX, mouseY, tracerX, ry, TRACER_W, 22);
        if (tracerHovered) ctx.fill(tracerX, ry + 3, tracerX + TRACER_W, ry + ROW_H - 3, GuiStyle.rowHover());
        int dot = tracer ? GuiStyle.accent() : GuiStyle.toggleOff();
        ctx.fill(tracerX + 5, ry + 11, tracerX + 9, ry + 15, dot);
        ctx.text(this.font, "Tracer", tracerX + 14, ry + 9,
                tracer ? GuiStyle.text() : GuiStyle.textFaint());

        // remove
        boolean removeHovered = in(mouseX, mouseY, removeX, ry, REMOVE_W, 22);
        ctx.centeredText(this.font, "✕", removeX + REMOVE_W / 2, ry + 9,
                removeHovered ? 0xFFFF5555 : GuiStyle.textFaint());
    }

    private void drawScrollbar(GuiGraphicsExtractor ctx, int size) {
        int trackH = VISIBLE * ROW_H;
        int x = listX + contentW + 4;
        ctx.fill(x, listY, x + 4, listY + trackH, GuiStyle.scrollTrack());
        if (size <= VISIBLE) return;

        int maxScroll = size - VISIBLE;
        int thumbH = Math.max(18, trackH * VISIBLE / size);
        int thumbY = listY + (trackH - thumbH) * scroll / maxScroll;
        ctx.fill(x, thumbY, x + 4, thumbY + thumbH, GuiStyle.scrollThumb());
    }

    private void drawFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        flatButton(ctx, addX, ADD_W, "+ Add Entry", mouseX, mouseY, GuiStyle.text());
        flatButton(ctx, doneX, DONE_W, "Done", mouseX, mouseY, GuiStyle.text());

        boolean hovered = in(mouseX, mouseY, defaultX, footerY, DEFAULT_W, FOOTER_H);
        GuiStyle.panel(ctx, defaultX, footerY, DEFAULT_W, FOOTER_H, hovered);
        drawBorder(ctx, defaultX, footerY, DEFAULT_W, FOOTER_H, hovered ? GuiStyle.accent() : GuiStyle.border());
        ctx.text(this.font, "Default", defaultX + 10, footerY + 7, GuiStyle.textDim());
        int sx = defaultX + DEFAULT_W - 30, sy = footerY + 4;
        ctx.fill(sx, sy, sx + 22, sy + 14, TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorESPCustom));
        drawBorder(ctx, sx, sy, 22, 14, GuiStyle.border());
    }

    private void flatButton(GuiGraphicsExtractor ctx, int x, int w, String label, int mouseX, int mouseY, int textColor) {
        boolean hovered = in(mouseX, mouseY, x, footerY, w, FOOTER_H);
        GuiStyle.panel(ctx, x, footerY, w, FOOTER_H, hovered);
        drawBorder(ctx, x, footerY, w, FOOTER_H, hovered ? GuiStyle.accent() : GuiStyle.border());
        ctx.centeredText(this.font, label, x + w / 2, footerY + 7, textColor);
    }

    private static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        // blank rows would match every entity, so they never survive the screen
        list().removeIf(String::isBlank);
        TeslaMapsConfig.save();
        Minecraft.getInstance().gui.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
