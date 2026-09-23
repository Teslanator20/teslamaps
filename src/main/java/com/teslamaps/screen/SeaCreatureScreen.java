package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.RareSeaCreature;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SeaCreatureList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Per sea creature: rare catch alert and party announce, on top of the Counts As Rare rule. */
public class SeaCreatureScreen extends Screen {

    private static final int PANEL_W = 360, PANEL_H = 306;
    private static final int ROW_H = 16, VISIBLE = 12;
    private static final int COL_W = 60;
    private static final int FOOTER_H = 22, BTN_W = 78, DONE_W = 60;

    private final Screen parent;

    private int panelX, panelY;
    private int listX, listY, listW;
    private int alertX, announceX;
    private int footerY, resetX, doneX;
    private int scroll = 0;

    private EditBox search;
    private List<String> shown = List.of();

    public SeaCreatureScreen(Screen parent) {
        super(Component.literal("Sea Creatures"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 14;
        listY = panelY + 64;
        listW = PANEL_W - 28 - 10;
        announceX = listX + listW - COL_W;
        alertX = announceX - COL_W;
        footerY = panelY + PANEL_H - 32;
        resetX = listX;
        doneX = panelX + PANEL_W - 14 - DONE_W;

        search = new EditBox(font, listX, panelY + 28, listW, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search"));
        search.setResponder(v -> {
            scroll = 0;
            rebuildList();
        });
        addWidget(search);

        GuiSounds.resetHover();
        rebuildList();
        ScreenFit.clamp(this);
    }

    private void rebuildList() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String name : SeaCreatureList.names()) {
            if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)) out.add(name);
        }
        shown = out;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - VISIBLE)));
    }

    private static boolean effective(Map<String, Boolean> overrides, String name) {
        return overrides.getOrDefault(name, RareSeaCreature.ruleRare(name));
    }

    private static void flip(Map<String, Boolean> overrides, String name) {
        boolean next = !effective(overrides, name);
        if (next == RareSeaCreature.ruleRare(name)) overrides.remove(name);
        else overrides.put(name, next);
        GuiSounds.toggle(next);
        TeslaMapsConfig.save();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        TeslaMapsConfig c = TeslaMapsConfig.get();

        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= shown.size()) break;
            int ry = listY + row * ROW_H;
            String name = shown.get(idx);
            if (in(mx, my, alertX, ry, COL_W, ROW_H)) {
                flip(c.seaCreatureAlert, name);
                return true;
            }
            if (in(mx, my, announceX, ry, COL_W, ROW_H)) {
                flip(c.seaCreatureAnnounce, name);
                return true;
            }
        }

        if (in(mx, my, resetX, footerY, BTN_W, FOOTER_H)) {
            GuiSounds.press();
            c.seaCreatureAlert.clear();
            c.seaCreatureAnnounce.clear();
            TeslaMapsConfig.save();
            return true;
        }
        if (in(mx, my, doneX, footerY, DONE_W, FOOTER_H)) {
            GuiSounds.press();
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (shown.size() > VISIBLE) {
            scroll = Math.max(0, Math.min(shown.size() - VISIBLE, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§f§lSea Creatures", panelX + 14, panelY + 12, GuiStyle.text());
        search.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.text(this.font, "§8dim = Counts As Rare rule, bright = your pick", listX, listY - 14, GuiStyle.text());
        ctx.centeredText(this.font, "§7Alert", alertX + COL_W / 2, listY - 14, GuiStyle.text());
        ctx.centeredText(this.font, "§7Announce", announceX + COL_W / 2, listY - 14, GuiStyle.text());

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (shown.isEmpty()) {
            ctx.text(this.font, SeaCreatureList.ready() ? "§7Nothing here." : "§7Sea creature list is still loading.",
                    listX, listY + 4, GuiStyle.text());
        }
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= shown.size()) break;
            String name = shown.get(idx);
            int ry = listY + row * ROW_H;
            if (in(mouseX, mouseY, listX, ry, listW, ROW_H)) {
                ctx.fill(listX, ry, listX + listW, ry + ROW_H, GuiStyle.rowHover());
            }
            SeaCreatureList.Info info = SeaCreatureList.info(name);
            ctx.text(this.font, (info == null ? "§f" : info.color()) + name, listX + 4, ry + 4, GuiStyle.text());
            mark(ctx, c.seaCreatureAlert, name, alertX, ry);
            mark(ctx, c.seaCreatureAnnounce, name, announceX, ry);
        }

        int trackH = VISIBLE * ROW_H;
        int sx = listX + listW + 4;
        ctx.fill(sx, listY, sx + 4, listY + trackH, GuiStyle.scrollTrack());
        if (shown.size() > VISIBLE) {
            int maxScroll = shown.size() - VISIBLE;
            int thumbH = Math.max(18, trackH * VISIBLE / shown.size());
            int thumbY = listY + (trackH - thumbH) * scroll / maxScroll;
            ctx.fill(sx, thumbY, sx + 4, thumbY + thumbH, GuiStyle.scrollThumb());
        }

        flatButton(ctx, resetX, BTN_W, "Reset", mouseX, mouseY);
        flatButton(ctx, doneX, DONE_W, "Done", mouseX, mouseY);

        GuiSounds.endFrame();
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void mark(GuiGraphicsExtractor ctx, Map<String, Boolean> overrides, String name, int x, int ry) {
        boolean on = effective(overrides, name);
        boolean own = overrides.containsKey(name);
        String s = on ? (own ? "§a✔" : "§2✔") : (own ? "§c✘" : "§8✘");
        ctx.centeredText(this.font, s, x + COL_W / 2, ry + 4, GuiStyle.text());
    }

    private void flatButton(GuiGraphicsExtractor ctx, int x, int w, String label, int mouseX, int mouseY) {
        boolean hovered = in(mouseX, mouseY, x, footerY, w, FOOTER_H);
        if (hovered) GuiSounds.hover("sc:" + label);
        GuiStyle.panel(ctx, x, footerY, w, FOOTER_H, hovered);
        int border = hovered ? GuiStyle.accent() : GuiStyle.border();
        ctx.fill(x, footerY, x + w, footerY + 1, border);
        ctx.fill(x, footerY + FOOTER_H - 1, x + w, footerY + FOOTER_H, border);
        ctx.fill(x, footerY, x + 1, footerY + FOOTER_H, border);
        ctx.fill(x + w - 1, footerY, x + w, footerY + FOOTER_H, border);
        ctx.centeredText(this.font, label, x + w / 2, footerY + 7, GuiStyle.text());
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
