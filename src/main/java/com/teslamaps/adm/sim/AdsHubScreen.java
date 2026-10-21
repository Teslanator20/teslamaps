package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/AdsHubScreen.kt

import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class AdsHubScreen extends Screen {
    private final Screen parent;

    private static class Entry {
        final String label;
        final int color;
        final Identifier tex;
        final int frames;
        final Runnable open;

        Entry(String label, int color, Identifier tex, int frames, Runnable open) {
            this.label = label;
            this.color = color;
            this.tex = tex;
            this.frames = frames;
            this.open = open;
        }

        Entry(String label, int color, Identifier tex, int frames) { this(label, color, tex, frames, () -> {}); }

        Entry(String label, int color, Identifier tex) { this(label, color, tex, 1); }
    }

    private final List<Entry> entries;

    public AdsHubScreen(Screen parent) {
        super(Component.literal(P3Sim.FULL_NAME));
        this.parent = parent;
        Minecraft mc = Minecraft.getInstance();
        entries = List.of(
                new Entry("Terms", 0xFFF0A21C, Identifier.withDefaultNamespace("textures/block/command_block_front.png"), 4, () -> SimWorld.open()),
                new Entry("Solo Clear", 0xFF5BBCE8, Identifier.withDefaultNamespace("textures/item/ender_pearl.png"), 1, () -> mc.setScreen(new com.teslamaps.adm.sim.clear.ui.RunStartScreen())),
                new Entry("Scenarios", 0xFFB4B4B4, Identifier.withDefaultNamespace("textures/item/spyglass.png"), 1, () -> mc.setScreen(new com.teslamaps.adm.sim.clear.ui.LayoutBrowserScreen(new com.teslamaps.adm.sim.clear.ui.RunStartScreen()))),
                new Entry("Practice", 0xFF213F9A, Identifier.withDefaultNamespace("textures/item/paper.png"), 1, () -> mc.setScreen(new com.teslamaps.adm.sim.clear.ui.PracticeScreen(this))),
                new Entry("Settings", 0xFF4A4A4A, null, 1, () -> mc.setScreen(com.teslamaps.screen.MapConfigScreen.openAdm()))
        );
    }

    @Override
    protected void init() {
        super.init();
        int w = ICON + GAP + BUTTON_W;
        int total = entries.size() * ROW_H - (ROW_H - ICON);
        int x = width / 2 - w / 2;
        int y0 = height / 2 - total / 2 + 10;
        for (int i = 0; i < entries.size(); i++) addRenderableWidget(new BigButton(entries.get(i), x, y0 + i * ROW_H, w));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int total = entries.size() * ROW_H - (ROW_H - ICON);
        g.centeredText(font, "§6§l" + P3Sim.FULL_NAME, width / 2, height / 2 - total / 2 - 12, -1);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    private static class BigButton extends AbstractButton {
        final Entry e;

        BigButton(Entry e, int x, int y, int w) {
            super(x, y, w, ICON, Component.literal(e.label));
            this.e = e;
        }

        @Override
        public void onPress(InputWithModifiers input) { e.open.run(); }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            boolean hot = isHoveredOrFocused();
            int base = hot ? shade(e.color, 1.18) : e.color;
            int x = getX();
            int y = getY();
            bevel(g, x, y, ICON, ICON, shade(base, 0.55), hot);
            int ix = x + (ICON - 16) / 2;
            int iy = y + (ICON - 16) / 2;
            if (e.tex != null) g.blit(RenderPipelines.GUI_TEXTURED, e.tex, ix, iy, 0f, 0f, 16, 16, 16, 16 * e.frames);
            else gear(g, ix, iy);
            int bx = x + ICON + GAP;
            bevel(g, bx, y, BUTTON_W, ICON, base, hot);
            Font font = Minecraft.getInstance().font;
            boolean light = luma(base) > 0.6;
            int tx = bx + BUTTON_W / 2 - font.width(e.label) / 2;
            g.text(font, e.label, tx, y + (ICON - 8) / 2, light ? 0xFF202020 : -1, !light);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final int ICON = 24;
    private static final int GAP = 4;
    private static final int BUTTON_W = 160;
    private static final int ROW_H = ICON + 6;

    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, boolean hot) {
        g.fill(x, y, x + w, y + h, hot ? -1 : 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, shade(color, 1.35));
        g.fill(x + 1, y + 1, x + 2, y + h - 1, shade(color, 1.2));
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, shade(color, 0.6));
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, shade(color, 0.75));
    }

    private static int shade(int c, double k) {
        return (c & 0xFF000000) | ch(c, k, 16) | ch(c, k, 8) | ch(c, k, 0);
    }

    private static int ch(int c, double k, int s) { return Math.clamp((int) (((c >> s) & 0xFF) * k), 0, 255) << s; }

    private static double luma(int c) { return (0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF)) / 255.0; }

    private static final List<String> GEAR = List.of(
            "......OOOO......",
            "...OO.OLLO.OO...",
            "..OLLOOLLMOOMO..",
            "..OLLLLLMMMMMO..",
            "...OLLLMMMMMO...",
            ".OOOLLOOOOMMOOO.",
            ".OLLLO....OMMDO.",
            ".OLLO......OMDO.",
            ".OLMO......ODDO.",
            ".OLMMO....ODDDO.",
            ".OOOMMOOOODDOOO.",
            "...OMMMDDDDDO...",
            "..OMMMMDDDDDDO..",
            "..OMDOODDOODDO..",
            "...OO.ODDO.OO...",
            "......OOOO......"
    );
    private static final Map<Character, Integer> GEAR_COLORS = Map.of(
            'O', 0xFF262626, 'L', 0xFFE2E2E2, 'M', 0xFFA9A9A9, 'D', 0xFF6B6B6B
    );

    private static void gear(GuiGraphicsExtractor g, int x, int y) {
        for (int row = 0; row < GEAR.size(); row++) {
            String line = GEAR.get(row);
            for (int col = 0; col < line.length(); col++) {
                Integer c = GEAR_COLORS.get(line.charAt(col));
                if (c != null) g.fill(x + col, y + row, x + col + 1, y + row + 1, c);
            }
        }
    }
}
