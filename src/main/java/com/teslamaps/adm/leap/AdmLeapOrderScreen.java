package com.teslamaps.adm.leap;

// Ported from ADM's misc/LeapOrder.kt (LeapOrderScreen)
import com.teslamaps.adm.adr.PartyTracker;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.player.PlayerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** The leap menu simulated: drag a player into another quarter to swap them; the order is saved on the drop. */
public class AdmLeapOrderScreen extends Screen {

    private static final long SLIDE_MS = 90L;
    private static final int BOX = 0xD91E1E1E, HOVER = 0xE63C3C3C, DRAGGED = 0xB32D2D2D, EMPTY = 0x40000000, OUTLINE = 0xFF787878, QUARTER = 0x0FFFFFFF;

    private final String[] names = {"", "", "", ""};
    private int dragging = -1;
    private boolean sawHeld;
    private double grabX, grabY, mouseX, mouseY;
    private final int[][] slideFrom = new int[4][];
    private final long[] slideAt = new long[4];
    private int partyVersion = -1;

    public AdmLeapOrderScreen() {
        super(Component.literal("Leap Order"));
        fill();
        PartyTracker.askIfUnknown();
    }

    private void fill() {
        synchronized (PartyTracker.state) { partyVersion = PartyTracker.state.version(); }
        String me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getName().getString();
        List<String> party = new ArrayList<>();
        for (String p : AdmLeapOrder.partyNames()) if (!p.equalsIgnoreCase(me)) party.add(p);
        List<String> order = AdmLeapOrder.order();
        List<String> shown = new ArrayList<>();
        if (party.isEmpty()) shown.addAll(order);
        else {
            for (String n : order) for (String p : party) if (p.equalsIgnoreCase(n)) { shown.add(p); break; }
            for (String p : party) {
                boolean in = false;
                for (String o : order) if (o.equalsIgnoreCase(p)) in = true;
                if (!in) shown.add(p);
            }
        }
        for (int i = 0; i < 4; i++) names[i] = i < shown.size() ? shown.get(i) : "";
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private float scale() { return TeslaMapsConfig.get().admLeapScale; }

    private int[] box(int i) {
        float s = scale();
        int w = (int) (AdmLeapMenu.BOX_WIDTH * s), h = (int) (AdmLeapMenu.BOX_HEIGHT * s);
        int x0 = i % 2 == 0 ? width / 2 - 24 - w : width / 2 + 24;
        int y0 = i / 2 == 0 ? height / 2 - 24 - h : height / 2 + 24;
        return new int[]{x0, y0, x0 + w, y0 + h};
    }

    private int[] drawnAt(int i) {
        int[] b = box(i);
        int[] from = slideFrom[i];
        if (from == null) return b;
        float t = (System.currentTimeMillis() - slideAt[i]) / (float) SLIDE_MS;
        if (t >= 1f) { slideFrom[i] = null; return b; }
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        int x = from[0] + (int) ((b[0] - from[0]) * e), y = from[1] + (int) ((b[1] - from[1]) * e);
        return new int[]{x, y, x + b[2] - b[0], y + b[3] - b[1]};
    }

    private void slide(int i, int x, int y) { slideFrom[i] = new int[]{x, y}; slideAt[i] = System.currentTimeMillis(); }

    private int[] heldAt(double x, double y) {
        int[] b = box(dragging);
        return new int[]{Math.max(0, Math.min(width - (b[2] - b[0]), (int) (x - grabX))), Math.max(0, Math.min(height - (b[3] - b[1]), (int) (y - grabY)))};
    }

    private int spotAt(double x, double y) { return (x < width / 2.0 ? 0 : 1) + (y < height / 2.0 ? 0 : 2); }

    private boolean onBox(int i, double x, double y) { int[] b = box(i); return x >= b[0] && x < b[2] && y >= b[1] && y < b[3]; }

    private String clazz(String name) {
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName().equalsIgnoreCase(name) && !"EMPTY".equals(dp.getDungeonClass())) return dp.getDungeonClass();
        }
        return null;
    }

    private static int classColor(String c) {
        if (c == null) return 0xFFFFFFFF;
        return switch (c) {
            case "Archer" -> 0xFFFFAA00; case "Berserk" -> 0xFFAA0000; case "Healer" -> 0xFFFF55FF;
            case "Mage" -> 0xFF55FFFF; case "Tank" -> 0xFF00AA00; default -> 0xFFFFFFFF;
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        mouseX = mx; mouseY = my;
        int v;
        synchronized (PartyTracker.state) { v = PartyTracker.state.version(); }
        if (dragging < 0 && v != partyVersion) fill();
        if (dragging >= 0) {
            boolean held = GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (held) sawHeld = true;
            else if (sawHeld) drop(mouseX, mouseY);
        }
        centred(g, "§6§lLeap Order", 12);
        centred(g, "§7Drag a player into another quarter of the screen to swap them. Saved as you drop.", 26);
        if (!"Custom sorting".equals(TeslaMapsConfig.get().admLeapSorting)) centred(g, "§cADM Leap Menu sorting isn't Custom, so it won't use this order.", 38);
        centred(g, "§8Top left is first, then top right, bottom left, bottom right.", height - 20);
        if (dragging >= 0) {
            int q = spotAt(mouseX, mouseY);
            int qx = q % 2 == 0 ? 0 : width / 2, qy = q / 2 == 0 ? 0 : height / 2;
            g.fill(qx, qy, qx + (width + 1) / 2, qy + (height + 1) / 2, QUARTER);
        }
        for (int i = 0; i < 4; i++) {
            int[] b = box(i);
            if (i == dragging) {
                AdmLeapMenu.roundedFill(g, b[0], b[1], b[2], b[3], EMPTY, (int) (9 * scale()));
                outline(g, b[0], b[1], b[2], b[3], OUTLINE);
                continue;
            }
            boolean hovered = dragging >= 0 && spotAt(mouseX, mouseY) == i;
            int[] at = drawnAt(i);
            drawBox(g, at[0], at[1], names[i], hovered ? HOVER : BOX);
        }
        if (dragging >= 0) {
            int[] at = heldAt(mouseX, mouseY);
            drawBox(g, at[0], at[1], names[dragging], DRAGGED);
        }
    }

    private void centred(GuiGraphicsExtractor g, String text, int y) {
        g.text(font, text, (width - font.width(text)) / 2, y, 0xFFFFFFFF, true);
    }

    private static void outline(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
    }

    private void drawBox(GuiGraphicsExtractor g, int x, int y, String name, int fill) {
        int w = AdmLeapMenu.BOX_WIDTH, h = AdmLeapMenu.BOX_HEIGHT;
        float s = scale();
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        String c = clazz(name);
        AdmLeapMenu.roundedFill(g, 0, 0, w, h, fill, 9);
        outline(g, 0, 0, w, h, c != null ? classColor(c) : OUTLINE);
        if (name.isEmpty()) {
            g.text(font, "§8Empty", 15, h / 2 - 4, 0xFFFFFFFF, false);
        } else {
            int face = (int) (h * 0.76);
            PlayerFaceExtractor.extractRenderState(g, AdmLeapOrder.skin(name), 9, 9, face);
            g.text(font, name, 15 + face, (int) (h / 2.5), c != null ? classColor(c) : 0xFFFFFFFF, true);
            g.text(font, c != null ? c : "§7In party", 15 + face, (int) (h / 1.7), 0xFFFFFFFF, true);
        }
        g.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int i = spotAt(event.x(), event.y());
        if (event.button() == 0 && !names[i].isEmpty()) {
            dragging = i;
            sawHeld = false;
            int[] b = box(i);
            if (onBox(i, event.x(), event.y())) { grabX = event.x() - b[0]; grabY = event.y() - b[1]; }
            else { grabX = (b[2] - b[0]) / 2.0; grabY = (b[3] - b[1]) / 2.0; }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging < 0) return super.mouseReleased(event);
        drop(event.x(), event.y());
        return true;
    }

    private void drop(double x, double y) {
        int to = spotAt(x, y);
        int[] held = heldAt(x, y);
        int[] other = drawnAt(to);
        slide(to, held[0], held[1]);
        if (to != dragging) slide(dragging, other[0], other[1]);
        if (to != dragging) {
            String t = names[to]; names[to] = names[dragging]; names[dragging] = t;
            List<String> order = new ArrayList<>();
            for (String n : names) if (!n.isEmpty()) order.add(n);
            for (int i = 0; i < 4; i++) names[i] = i < order.size() ? order.get(i) : "";
            AdmLeapOrder.set(order);
            var p = Minecraft.getInstance().player;
            if (p != null) p.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
        }
        dragging = -1;
    }
}
