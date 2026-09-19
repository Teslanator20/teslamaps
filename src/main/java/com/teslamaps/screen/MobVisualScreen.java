package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.mobvisuals.Mob;
import com.teslamaps.features.mobvisuals.MobArea;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.render.gui.HudStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * `/tmap mobs` - picks which mobs the glow, tracer and nameplate apply to.
 * Areas down the left, the mobs of the picked area on the right, same flat idiom as
 * {@link EspListScreen}. Replaces GanKura's per-area drag lists.
 */
public class MobVisualScreen extends Screen {

    private static final int PANEL_W = 470, PANEL_H = 306;
    private static final int ROW_H = 16, VISIBLE = 12;
    private static final int SIDE_W = 136;
    private static final int FOOTER_H = 22, BTN_W = 78, DONE_W = 60;

    private final Screen parent;

    private int panelX, panelY;
    private int sideX, sideY;
    private int listX, listY, listW;
    private int footerY, allX, noneX, doneX;
    private int scroll = 0;

    private MobArea area = MobArea.GARDEN;
    private EditBox search;
    private List<Mob> shown = List.of();

    public MobVisualScreen(Screen parent) {
        super(Component.literal("Mob Visuals"));
        this.parent = parent;
    }

    private static java.util.Set<String> picked() {
        return TeslaMapsConfig.get().mobVisualTargets;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        sideX = panelX + 14;
        sideY = panelY + 52;

        listX = sideX + SIDE_W + 12;
        listY = panelY + 52;
        listW = PANEL_W - (listX - panelX) - 14 - 10;   // 10 for the scrollbar gutter

        footerY = panelY + PANEL_H - 32;
        allX = listX;
        noneX = allX + BTN_W + 8;
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
        List<Mob> out = new ArrayList<>();
        for (Mob mob : Mob.values()) {
            if (!query.isEmpty()) {
                // a search spans every area, otherwise you have to know where a mob lives
                if (!mob.plainLabel().toLowerCase(Locale.ROOT).contains(query)) continue;
            } else if (mob.area() != area) {
                continue;
            }
            out.add(mob);
        }
        shown = out;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - VISIBLE)));
    }

    // --- input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        MobArea[] areas = MobArea.values();
        for (int i = 0; i < areas.length; i++) {
            int ry = sideY + i * ROW_H;
            if (!in(mx, my, sideX, ry, SIDE_W, ROW_H)) continue;
            GuiSounds.press();
            area = areas[i];
            search.setValue("");
            scroll = 0;
            rebuildList();
            return true;
        }

        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= shown.size()) break;
            int ry = listY + row * ROW_H;
            if (!in(mx, my, listX, ry, listW, ROW_H)) continue;

            Mob mob = shown.get(idx);
            boolean was = picked().remove(mob.name());
            if (!was) picked().add(mob.name());
            GuiSounds.toggle(!was);
            TeslaMapsConfig.save();
            return true;
        }

        if (in(mx, my, allX, footerY, BTN_W, FOOTER_H)) {
            GuiSounds.press();
            for (Mob mob : shown) picked().add(mob.name());
            TeslaMapsConfig.save();
            return true;
        }
        if (in(mx, my, noneX, footerY, BTN_W, FOOTER_H)) {
            GuiSounds.press();
            for (Mob mob : shown) picked().remove(mob.name());
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
        if (shown.size() > VISIBLE && mouseX >= listX && mouseX < listX + listW + 10) {
            scroll = Math.max(0, Math.min(shown.size() - VISIBLE, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // --- render ---

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§f§lMob Visuals", panelX + 14, panelY + 12, GuiStyle.text());
        ctx.text(this.font, "§8pick the mobs the glow, tracer and nameplate apply to",
                panelX + 14, panelY + 24, GuiStyle.text());

        drawAreas(ctx, mouseX, mouseY);
        drawMobs(ctx, mouseX, mouseY);
        drawScrollbar(ctx);
        drawFooter(ctx, mouseX, mouseY);

        GuiSounds.endFrame();
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawAreas(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        MobArea[] areas = MobArea.values();
        boolean searching = !search.getValue().trim().isEmpty();

        for (int i = 0; i < areas.length; i++) {
            MobArea a = areas[i];
            int ry = sideY + i * ROW_H;
            boolean hovered = in(mouseX, mouseY, sideX, ry, SIDE_W, ROW_H);
            if (hovered) GuiSounds.hover("mob:area:" + a.name());
            if (a == area && !searching) ctx.fill(sideX, ry, sideX + SIDE_W, ry + ROW_H, GuiStyle.rowHover());
            else if (hovered) ctx.fill(sideX, ry, sideX + SIDE_W, ry + ROW_H, GuiStyle.rowHover());

            int on = 0;
            List<Mob> mobs = Mob.of(a);
            for (Mob mob : mobs) if (picked().contains(mob.name())) on++;

            int color = a == area && !searching ? GuiStyle.text() : GuiStyle.textDim();
            ctx.text(this.font, a.title(), sideX + 6, ry + 4, color);
            String count = on + "/" + mobs.size();
            ctx.text(this.font, on > 0 ? "§a" + count : "§8" + count,
                    sideX + SIDE_W - 6 - this.font.width(count), ry + 4, GuiStyle.text());
        }
    }

    private void drawMobs(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (shown.isEmpty()) {
            ctx.text(this.font, "§7Nothing here.", listX, listY + 4, GuiStyle.text());
            return;
        }

        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= shown.size()) break;

            Mob mob = shown.get(idx);
            int ry = listY + row * ROW_H;
            boolean hovered = in(mouseX, mouseY, listX, ry, listW, ROW_H);
            if (hovered) {
                GuiSounds.hover("mob:" + mob.name());
                ctx.fill(listX, ry, listX + listW, ry + ROW_H, GuiStyle.rowHover());
            }

            boolean on = picked().contains(mob.name());
            // colour chip, so the list doubles as the legend for the glow colours
            ctx.fill(listX + 4, ry + 5, listX + 12, ry + 13, 0xFF000000 | mob.glowColorRGB());
            ctx.text(this.font, on ? "§a✔" : "§8✘", listX + 18, ry + 4, GuiStyle.text());
            ctx.text(this.font, mob.label(), listX + 32, ry + 4, GuiStyle.text());
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor ctx) {
        int trackH = VISIBLE * ROW_H;
        int x = listX + listW + 4;
        ctx.fill(x, listY, x + 4, listY + trackH, GuiStyle.scrollTrack());
        if (shown.size() <= VISIBLE) return;

        int maxScroll = shown.size() - VISIBLE;
        int thumbH = Math.max(18, trackH * VISIBLE / shown.size());
        int thumbY = listY + (trackH - thumbH) * scroll / maxScroll;
        ctx.fill(x, thumbY, x + 4, thumbY + thumbH, GuiStyle.scrollThumb());
    }

    private void drawFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        flatButton(ctx, allX, BTN_W, "All Shown", mouseX, mouseY);
        flatButton(ctx, noneX, BTN_W, "None", mouseX, mouseY);
        flatButton(ctx, doneX, DONE_W, "Done", mouseX, mouseY);
    }

    private void flatButton(GuiGraphicsExtractor ctx, int x, int w, String label, int mouseX, int mouseY) {
        boolean hovered = in(mouseX, mouseY, x, footerY, w, FOOTER_H);
        if (hovered) GuiSounds.hover("mob:" + label);
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
