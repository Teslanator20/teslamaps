package com.teslamaps.screen;

import com.teslamaps.features.waypoints.OrderedWaypoints;
import com.teslamaps.features.waypoints.RouteStorage;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The waypoints of one saved route: reorder, delete, add at the player, copy in any format. */
public class RouteEditScreen extends Screen {
    private static final int PANEL_W = 430, PANEL_H = 300;
    private static final int ROW_H = 22, VISIBLE = 8;
    private static final int UP_W = 20, DOWN_W = 20, REMOVE_W = 20, GAP = 6;
    private static final int FOOTER_H = 22, ADD_W = 96, COPY_W = 78, DONE_W = 60;

    private final Screen parent;
    private final String name;
    private final List<OrderedWaypoints.Wp> waypoints = new ArrayList<>();

    private int panelX, panelY, listX, listY, contentW, posW;
    private int upX, downX, removeX;
    private int addX, coleX, skytilsX, skyblockerX, doneX, footerY;
    private int scroll = 0;

    private static final int HISTORY = 64, CROSS_W = 110, HANDLE_W = 36;
    private final boolean extras = com.teslamaps.config.TeslaMapsConfig.get().routeEditorExtras;
    private final java.util.ArrayDeque<List<OrderedWaypoints.Wp>> undo = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<List<OrderedWaypoints.Wp>> redo = new java.util.ArrayDeque<>();
    private int selected = -1, dragFrom = -1, dragTo = -1, crossX, crossY;
    private long lastAutoScroll;

    public RouteEditScreen(Screen parent, String name, int selected) {
        this(parent, name);
        if (selected >= 0 && selected < waypoints.size()) {
            this.selected = selected;
            scroll = Math.max(0, Math.min(selected - VISIBLE / 2, waypoints.size() - VISIBLE));
        }
    }

    public RouteEditScreen(Screen parent, String name) {
        super(Component.literal("Route"));
        this.parent = parent;
        this.name = name;
        List<OrderedWaypoints.Wp> stored = RouteStorage.get(name);
        if (stored != null) {
            for (OrderedWaypoints.Wp wp : stored) {
                waypoints.add(wp.copy());
            }
        }
        renumber();
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 58;
        contentW = PANEL_W - 32 - 10;
        posW = contentW - UP_W - DOWN_W - REMOVE_W - GAP * 3;
        upX = listX + posW + GAP;
        downX = upX + UP_W + GAP;
        removeX = downX + DOWN_W + GAP;

        footerY = panelY + PANEL_H - 32;
        addX = listX;
        coleX = addX + ADD_W + 8;
        skytilsX = coleX + COPY_W + 6;
        skyblockerX = skytilsX + COPY_W + 6;
        doneX = panelX + PANEL_W - 16 - DONE_W;
        crossX = panelX + PANEL_W - 16 - CROSS_W;
        crossY = panelY + 14;

        ScreenFit.clamp(this);
    }

    private void renumber() {
        for (int i = 0; i < waypoints.size(); i++) waypoints.get(i).number = i + 1;
    }

    private static List<OrderedWaypoints.Wp> copyOf(List<OrderedWaypoints.Wp> list) {
        List<OrderedWaypoints.Wp> out = new ArrayList<>(list.size());
        for (OrderedWaypoints.Wp wp : list) out.add(wp.copy());
        return out;
    }

    private void snapshot() {
        undo.push(copyOf(waypoints));
        while (undo.size() > HISTORY) undo.removeLast();
        redo.clear();
    }

    private void restore(java.util.ArrayDeque<List<OrderedWaypoints.Wp>> from, java.util.ArrayDeque<List<OrderedWaypoints.Wp>> to) {
        if (from.isEmpty()) return;
        to.push(copyOf(waypoints));
        waypoints.clear();
        waypoints.addAll(from.pop());
        renumber();
        selected = -1;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, waypoints.size() - VISIBLE)));
        GuiSounds.press();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (extras && event.hasControlDown()) {
            if (event.key() == org.lwjgl.glfw.com.mojang.blaze3d.platform.InputConstants.KEY_Z) {
                if (event.hasShiftDown()) restore(redo, undo);
                else restore(undo, redo);
                return true;
            }
            if (event.key() == org.lwjgl.glfw.com.mojang.blaze3d.platform.InputConstants.KEY_Y) {
                restore(redo, undo);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private int rowAt(double my) {
        int row = (int) Math.floor((my - listY) / ROW_H);
        return Math.clamp(scroll + Math.clamp(row, 0, VISIBLE - 1), 0, Math.max(0, waypoints.size() - 1));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragFrom >= 0) {
            dragTo = rowAt(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragFrom >= 0) {
            if (dragTo >= 0 && dragTo != dragFrom && dragFrom < waypoints.size()) {
                snapshot();
                waypoints.add(dragTo, waypoints.remove(dragFrom));
                renumber();
                selected = dragTo;
                GuiSounds.press();
            }
            dragFrom = dragTo = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void addAtCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        net.minecraft.world.phys.HitResult hit = mc.player.pick(128, 1f, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult bh)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            WaypointRoutesScreen.feedback("§cNo block under the crosshair.");
            return;
        }
        snapshot();
        var pos = bh.getBlockPos();
        waypoints.add(new OrderedWaypoints.Wp(pos.getX(), pos.getY(), pos.getZ(), waypoints.size() + 1));
        selected = waypoints.size() - 1;
        scroll = Math.max(0, waypoints.size() - VISIBLE);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (waypoints.size() > VISIBLE) {
            scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, waypoints.size() - VISIBLE);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        if (in(mx, my, addX, footerY, ADD_W, FOOTER_H)) {
            GuiSounds.press();
            if (extras) snapshot();
            addAtPlayer();
            return true;
        }
        if (extras && in(mx, my, crossX, crossY, CROSS_W, FOOTER_H)) {
            GuiSounds.press();
            addAtCrosshair();
            return true;
        }
        if (in(mx, my, coleX, footerY, COPY_W, FOOTER_H)) { copy("coleweight"); return true; }
        if (in(mx, my, skytilsX, footerY, COPY_W, FOOTER_H)) { copy("skytils"); return true; }
        if (in(mx, my, skyblockerX, footerY, COPY_W, FOOTER_H)) { copy("skyblocker"); return true; }
        if (in(mx, my, doneX, footerY, DONE_W, FOOTER_H)) {
            GuiSounds.press();
            onClose();
            return true;
        }

        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= waypoints.size()) break;
            int ry = listY + row * ROW_H;
            if (my < ry || my >= ry + ROW_H) continue;

            if (extras && mx >= listX && mx < listX + HANDLE_W) {
                dragFrom = dragTo = idx;
                selected = idx;
                return true;
            }
            if (in(mx, my, upX, ry, UP_W, 20) && idx > 0) {
                GuiSounds.press();
                if (extras) snapshot();
                waypoints.add(idx - 1, waypoints.remove(idx));
                renumber();
                return true;
            }
            if (in(mx, my, downX, ry, DOWN_W, 20) && idx < waypoints.size() - 1) {
                GuiSounds.press();
                if (extras) snapshot();
                waypoints.add(idx + 1, waypoints.remove(idx));
                renumber();
                return true;
            }
            if (in(mx, my, removeX, ry, REMOVE_W, 20)) {
                GuiSounds.press();
                if (extras) snapshot();
                selected = -1;
                waypoints.remove(idx);
                renumber();
                scroll = Math.max(0, Math.min(scroll, Math.max(0, waypoints.size() - VISIBLE)));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void addAtPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 p = mc.player.position();
        waypoints.add(new OrderedWaypoints.Wp((int) Math.floor(p.x), (int) Math.floor(p.y) - 1,
                (int) Math.floor(p.z), waypoints.size() + 1));
        scroll = Math.max(0, waypoints.size() - VISIBLE);
    }

    private void copy(String format) {
        GuiSounds.press();
        WaypointRoutesScreen.feedback(OrderedWaypoints.exportList(waypoints, name, format));
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§f§l" + name, panelX + 16, panelY + 16, GuiStyle.text());
        ctx.text(this.font, extras ? "§8" + waypoints.size() + " waypoints · drag # · Ctrl+Z / Ctrl+Y"
                        : "§8" + waypoints.size() + " waypoints · changes are saved when you close",
                panelX + 16, panelY + 30, GuiStyle.text());
        if (extras) {
            if (dragFrom >= 0 && System.currentTimeMillis() - lastAutoScroll > 120) {
                if (mouseY < listY + 8 && scroll > 0) {
                    scroll--;
                    lastAutoScroll = System.currentTimeMillis();
                } else if (mouseY > listY + VISIBLE * ROW_H - 8 && scroll < waypoints.size() - VISIBLE) {
                    scroll++;
                    lastAutoScroll = System.currentTimeMillis();
                }
                dragTo = rowAt(mouseY);
            }
            boolean hov = in(mouseX, mouseY, crossX, crossY, CROSS_W, FOOTER_H);
            GuiStyle.panel(ctx, crossX, crossY, CROSS_W, FOOTER_H, hov);
            WaypointRoutesScreen.drawBorder(ctx, crossX, crossY, CROSS_W, FOOTER_H, hov ? GuiStyle.accent() : GuiStyle.border());
            ctx.centeredText(this.font, "Add At Crosshair", crossX + CROSS_W / 2, crossY + 7, GuiStyle.text());
        }

        if (waypoints.isEmpty()) {
            ctx.text(this.font, "§7Empty — stand somewhere and click \"Add At Player\".",
                    listX, listY + 6, GuiStyle.text());
        }
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= waypoints.size()) break;
            drawRow(ctx, idx, listY + row * ROW_H, mouseX, mouseY);
        }

        drawScrollbar(ctx);
        flatButton(ctx, addX, ADD_W, "Add At Player", mouseX, mouseY);
        flatButton(ctx, coleX, COPY_W, "Coleweight", mouseX, mouseY);
        flatButton(ctx, skytilsX, COPY_W, "Skytils", mouseX, mouseY);
        flatButton(ctx, skyblockerX, COPY_W, "Skyblocker", mouseX, mouseY);
        flatButton(ctx, doneX, DONE_W, "Done", mouseX, mouseY);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawRow(GuiGraphicsExtractor ctx, int idx, int ry, int mouseX, int mouseY) {
        OrderedWaypoints.Wp wp = waypoints.get(idx);
        boolean rowHovered = mouseX >= listX && mouseX < listX + contentW && mouseY >= ry && mouseY < ry + ROW_H;
        if (rowHovered) {
            ctx.fill(listX, ry, listX + contentW, ry + ROW_H, GuiStyle.rowHover());
        }
        if (idx == selected) ctx.fill(listX, ry, listX + 2, ry + ROW_H, GuiStyle.accent());
        if (dragFrom >= 0 && idx == dragTo && dragTo != dragFrom) {
            int ly = dragTo > dragFrom ? ry + ROW_H - 1 : ry;
            ctx.fill(listX, ly, listX + contentW, ly + 1, GuiStyle.accent());
        }

        ctx.text(this.font, "§7#" + wp.number, listX + 6, ry + 7, GuiStyle.textDim());
        ctx.text(this.font, "§f" + wp.x + " §7/ §f" + wp.y + " §7/ §f" + wp.z, listX + 42, ry + 7, GuiStyle.text());

        double dist = distanceToPlayer(wp);
        if (dist >= 0) {
            String d = String.format("%.0f m", dist);
            ctx.text(this.font, "§8" + d, listX + posW - 8 - this.font.width(d), ry + 7, GuiStyle.textFaint());
        }

        boolean upHovered = in(mouseX, mouseY, upX, ry, UP_W, 20);
        boolean downHovered = in(mouseX, mouseY, downX, ry, DOWN_W, 20);
        boolean removeHovered = in(mouseX, mouseY, removeX, ry, REMOVE_W, 20);
        ctx.centeredText(this.font, "▲", upX + UP_W / 2, ry + 7,
                idx == 0 ? GuiStyle.toggleOff() : upHovered ? GuiStyle.text() : GuiStyle.textFaint());
        ctx.centeredText(this.font, "▼", downX + DOWN_W / 2, ry + 7,
                idx == waypoints.size() - 1 ? GuiStyle.toggleOff() : downHovered ? GuiStyle.text() : GuiStyle.textFaint());
        ctx.centeredText(this.font, "✕", removeX + REMOVE_W / 2, ry + 7,
                removeHovered ? 0xFFFF5555 : GuiStyle.textFaint());
    }

    private static double distanceToPlayer(OrderedWaypoints.Wp wp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        return mc.player.position().distanceTo(new Vec3(wp.x + 0.5, wp.y + 0.5, wp.z + 0.5));
    }

    private void drawScrollbar(GuiGraphicsExtractor ctx) {
        int trackH = VISIBLE * ROW_H;
        int x = listX + contentW + 4;
        ctx.fill(x, listY, x + 4, listY + trackH, GuiStyle.scrollTrack());
        if (waypoints.size() <= VISIBLE) return;
        int thumbH = Math.max(18, trackH * VISIBLE / waypoints.size());
        int thumbY = listY + (trackH - thumbH) * scroll / (waypoints.size() - VISIBLE);
        ctx.fill(x, thumbY, x + 4, thumbY + thumbH, GuiStyle.scrollThumb());
    }

    private void flatButton(GuiGraphicsExtractor ctx, int x, int w, String label, int mouseX, int mouseY) {
        boolean hovered = in(mouseX, mouseY, x, footerY, w, FOOTER_H);
        GuiStyle.panel(ctx, x, footerY, w, FOOTER_H, hovered);
        WaypointRoutesScreen.drawBorder(ctx, x, footerY, w, FOOTER_H, hovered ? GuiStyle.accent() : GuiStyle.border());
        ctx.centeredText(this.font, label, x + w / 2, footerY + 7, GuiStyle.text());
    }

    @Override
    public void onClose() {
        if (waypoints.isEmpty()) {
            RouteStorage.remove(name);
            if (OrderedWaypoints.isActive(name)) OrderedWaypoints.clearActive();
        } else {
            RouteStorage.put(name, waypoints);
            if (OrderedWaypoints.isActive(name)) OrderedWaypoints.setActiveRoute(name, waypoints);
        }
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
