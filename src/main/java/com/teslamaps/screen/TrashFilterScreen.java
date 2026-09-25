package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.TrashFilter;
import com.teslamaps.features.TrashHighlighter;
import com.teslamaps.render.gui.HudStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TrashFilterScreen extends Screen {
    private static final int PANEL_W = 400, PANEL_H = 300;
    private static final int ROW_H = 24, VISIBLE = 8;

    private final Screen parent;
    private int panelX, panelY, listX, listY;
    private int scroll = 0;
    private final List<int[]> fields = new ArrayList<>();

    public TrashFilterScreen(Screen parent) {
        super(Component.literal("Trash Filters"));
        this.parent = parent;
    }

    private List<TrashFilter> list() { return TeslaMapsConfig.get().trashFilters; }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 12;
        listY = panelY + 40;
        rebuild();
        ScreenFit.clamp(this);
    }

    private static String next(String[] opts, String cur) {
        int i = Arrays.asList(opts).indexOf(cur);
        return opts[(i + 1) % opts.length];
    }

    private static boolean bad(TrashFilter f) {
        return "Regex".equals(f.mode) && !TrashHighlighter.validRegex(f.value);
    }

    private void rebuild() {
        clearWidgets();
        fields.clear();
        TrashHighlighter.invalidate();
        List<TrashFilter> l = list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, l.size() - VISIBLE)));

        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= l.size()) break;
            TrashFilter f = l.get(idx);
            int ry = listY + row * ROW_H;
            int x = listX;

            addRenderableWidget(new TeslaButton(x, ry, 44, 20, f.target, () -> {
                f.target = next(TrashHighlighter.TARGETS, f.target);
                rebuild();
            }));
            x += 48;
            addRenderableWidget(new TeslaButton(x, ry, 60, 20, f.mode, () -> {
                f.mode = next(TrashHighlighter.MODES, f.mode);
                rebuild();
            }));
            x += 64;

            EditBox box = new EditBox(font, x + 4, ry + 5, 236, 12, Component.literal("value"));
            box.setMaxLength(256);
            box.setBordered(false);
            box.setHint(Component.literal("DEFUSE_KIT"));
            box.setValue(f.value);
            int[] field = {x, ry, 244, 20, bad(f) ? 1 : 0};
            box.setResponder(v -> { f.value = v; field[4] = bad(f) ? 1 : 0; TrashHighlighter.invalidate(); });
            addRenderableWidget(box);
            fields.add(field);
            x += 248;

            addRenderableWidget(new TeslaButton(x, ry, 16, 20, "§cx", () -> { list().remove(idx); rebuild(); }));
        }

        int by = panelY + PANEL_H - 30;
        addRenderableWidget(new TeslaButton(panelX + 12, by, 90, 20, "§a+ Add Filter", () -> {
            list().add(new TrashFilter("Name", "Contains", ""));
            scroll = Math.max(0, list().size() - VISIBLE);
            rebuild();
        }));
        addRenderableWidget(new TeslaButton(panelX + 108, by, 100, 20, "Reset Defaults", () -> {
            list().clear();
            list().addAll(TrashHighlighter.defaults());
            scroll = 0;
            rebuild();
        }));
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 12 - 80, by, 80, 20, "§fDone", this::onClose));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (list().size() > VISIBLE) {
            scroll = Math.max(0, Math.min(list().size() - VISIBLE, scroll - (int) Math.signum(sy)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);
        ctx.text(font, "§f§lTrash Filters", panelX + 12, panelY + 10, 0xFFFFFFFF);
        ctx.text(font, "§8match on ID / name / lore · equals / contains / regex · any hit = trash",
                panelX + 12, panelY + 24, 0xFFFFFFFF);

        List<TrashFilter> l = list();
        for (int[] f : fields) {
            HudStyle.screenButton(ctx, f[0], f[1], f[2], f[3], false, true);
            if (f[4] == 1) ctx.text(font, "§c!", listX - 8, f[1] + 6, 0xFFFFFFFF);
        }
        if (l.isEmpty()) ctx.text(font, "§7No filters - click \"+ Add Filter\".", panelX + 12, listY + 6, 0xFFFFFFFF);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        list().removeIf(f -> f.value.isBlank());
        TrashHighlighter.invalidate();
        TeslaMapsConfig.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
