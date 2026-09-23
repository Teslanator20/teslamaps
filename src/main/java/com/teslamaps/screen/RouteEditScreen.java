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

        GuiSounds.resetHover();
        ScreenFit.clamp(this);
    }

    private void renumber() {
        for (int i = 0; i < waypoints.size(); i++) waypoints.get(i).number = i + 1;
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
            addAtPlayer();
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

            if (in(mx, my, upX, ry, UP_W, 20) && idx > 0) {
                GuiSounds.press();
                waypoints.add(idx - 1, waypoints.remove(idx));
                renumber();
                return true;
            }
            if (in(mx, my, downX, ry, DOWN_W, 20) && idx < waypoints.size() - 1) {
                GuiSounds.press();
                waypoints.add(idx + 1, waypoints.remove(idx));
                renumber();
                return true;
            }
            if (in(mx, my, removeX, ry, REMOVE_W, 20)) {
                GuiSounds.press();
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
        ctx.text(this.font, "§8" + waypoints.size() + " waypoints · changes are saved when you close",
                panelX + 16, panelY + 30, GuiStyle.text());

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

        GuiSounds.endFrame();
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawRow(GuiGraphicsExtractor ctx, int idx, int ry, int mouseX, int mouseY) {
        OrderedWaypoints.Wp wp = waypoints.get(idx);
        boolean rowHovered = mouseX >= listX && mouseX < listX + contentW && mouseY >= ry && mouseY < ry + ROW_H;
        if (rowHovered) {
            GuiSounds.hover("wp:" + idx);
            ctx.fill(listX, ry, listX + contentW, ry + ROW_H, GuiStyle.rowHover());
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
        if (hovered) GuiSounds.hover("wp:" + label);
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
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
