package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.OrderedWaypoints;
import com.teslamaps.features.waypoints.RouteStorage;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** `/tmap waypoints` - saved routes plus the dungeon waypoint file. */
public class WaypointRoutesScreen extends Screen {
    private static final int PANEL_W = 470, PANEL_H = 300;
    private static final int ROW_H = 26, VISIBLE = 7;
    private static final int COUNT_W = 52, LOAD_W = 58, EDIT_W = 46, REMOVE_W = 20, GAP = 6;
    private static final int FOOTER_H = 22, DONE_W = 60;

    private int panelX, panelY, listX, listY, contentW, nameW;
    private int countX, loadX, editX, removeX;
    private int importX, saveX, dungeonX, doneX, footerY;
    private static final int IMPORT_W = 116, SAVE_W = 96, DUNGEON_W = 106;
    private int scroll = 0;

    public WaypointRoutesScreen() { super(Component.literal("Waypoints")); }

    private List<String> names() { return RouteStorage.names(); }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 58;
        contentW = PANEL_W - 32 - 10;
        nameW = contentW - COUNT_W - LOAD_W - EDIT_W - REMOVE_W - GAP * 4;
        countX = listX + nameW + GAP;
        loadX = countX + COUNT_W + GAP;
        editX = loadX + LOAD_W + GAP;
        removeX = editX + EDIT_W + GAP;

        footerY = panelY + PANEL_H - 32;
        importX = listX;
        saveX = importX + IMPORT_W + 8;
        dungeonX = saveX + SAVE_W + 8;
        doneX = panelX + PANEL_W - 16 - DONE_W;

        GuiSounds.resetHover();
        rebuild();
        ScreenFit.clamp(this);
    }

    private final List<EditBox> nameBoxes = new ArrayList<>();
    private final List<String> nameBoxOriginals = new ArrayList<>();

    private void rebuild() {
        clearWidgets();
        nameBoxes.clear();
        nameBoxOriginals.clear();
        List<String> list = names();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - VISIBLE)));

        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            int ry = listY + row * ROW_H;

            EditBox name = new EditBox(this.font, listX + 6, ry + 7, nameW - 12, 12, Component.literal("name"));
            name.setMaxLength(48);
            name.setBordered(false);
            name.setValue(list.get(idx));
            addRenderableWidget(name);
            nameBoxes.add(name);
            nameBoxOriginals.add(list.get(idx));
        }
    }

    /** Renames are written when you leave the field, not on every keystroke - each one is a file write. */
    private boolean commitRenames() {
        boolean changed = false;
        for (int i = 0; i < nameBoxes.size(); i++) {
            String to = nameBoxes.get(i).getValue().trim();
            String from = nameBoxOriginals.get(i);
            if (to.isBlank() || to.equals(from)) continue;
            List<OrderedWaypoints.Wp> wps = RouteStorage.get(from);
            if (wps == null || RouteStorage.get(to) != null) continue;
            String island = RouteStorage.island(from);
            RouteStorage.remove(from);
            RouteStorage.put(to, wps);
            if (island != null) RouteStorage.setIsland(to, island);
            if (OrderedWaypoints.isActive(from)) OrderedWaypoints.renameActive(to);
            changed = true;
        }
        return changed;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int size = names().size();
        if (size > VISIBLE) {
            scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, size - VISIBLE);
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (commitRenames()) rebuild();
        List<String> list = names();

        if (in(mx, my, importX, footerY, IMPORT_W, FOOTER_H)) {
            GuiSounds.press();
            feedback(OrderedWaypoints.importClipboard());
            rebuild();
            return true;
        }
        if (in(mx, my, saveX, footerY, SAVE_W, FOOTER_H)) {
            GuiSounds.press();
            saveCurrent();
            return true;
        }
        if (in(mx, my, dungeonX, footerY, DUNGEON_W, FOOTER_H)) {
            GuiSounds.press();
            com.teslamaps.dungeon.DungeonWaypoints.load();
            feedback("§aReloaded dungeon_waypoints.json");
            return true;
        }
        if (in(mx, my, doneX, footerY, DONE_W, FOOTER_H)) {
            GuiSounds.press();
            onClose();
            return true;
        }

        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            int ry = listY + row * ROW_H;
            if (my < ry || my >= ry + ROW_H) continue;
            String name = list.get(idx);

            if (in(mx, my, loadX, ry, LOAD_W, 22)) {
                GuiSounds.press();
                if (OrderedWaypoints.isActive(name)) {
                    OrderedWaypoints.clearActive();
                } else {
                    feedback(OrderedWaypoints.load(name));
                }
                return true;
            }
            if (in(mx, my, editX, ry, EDIT_W, 22)) {
                GuiSounds.press();
                minecraft.setScreen(new RouteEditScreen(this, name));
                return true;
            }
            if (in(mx, my, removeX, ry, REMOVE_W, 22)) {
                GuiSounds.press();
                RouteStorage.remove(name);
                if (OrderedWaypoints.isActive(name)) OrderedWaypoints.clearActive();
                rebuild();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void saveCurrent() {
        List<OrderedWaypoints.Wp> live = OrderedWaypoints.route();
        if (live.isEmpty()) { feedback("§cNo route loaded - import one first."); return; }
        String name = OrderedWaypoints.loadedName();
        if (name.isEmpty()) name = "Route " + (names().size() + 1);
        RouteStorage.put(name, live);
        OrderedWaypoints.renameActive(name);
        feedback("§aSaved as §e" + name + "§a.");
        rebuild();
    }

    static void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + message));
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(this.font, "§f§lWaypoint Routes", panelX + 16, panelY + 16, GuiStyle.text());
        String active = OrderedWaypoints.loadedName();
        String sub = OrderedWaypoints.route().isEmpty()
                ? "§8no route loaded · import from Coleweight, Skytils, Skyblocker or a coordinate list"
                : "§8loaded: §7" + (active.isEmpty() ? "unnamed" : active) + " §8· " + OrderedWaypoints.route().size()
                        + " waypoints · at #" + (OrderedWaypoints.currentIndex() + 1);
        ctx.text(this.font, sub, panelX + 16, panelY + 30, GuiStyle.text());

        List<String> list = names();
        if (list.isEmpty()) {
            ctx.text(this.font, "§7No saved routes — copy one and click \"Import Clipboard\".",
                    listX, listY + 6, GuiStyle.text());
        }
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            drawRow(ctx, list.get(idx), listY + row * ROW_H, mouseX, mouseY, idx);
        }

        drawScrollbar(ctx, list.size());
        flatButton(ctx, importX, IMPORT_W, "Import Clipboard", mouseX, mouseY);
        flatButton(ctx, saveX, SAVE_W, "Save Current", mouseX, mouseY);
        flatButton(ctx, dungeonX, DUNGEON_W, "Reload Dungeon", mouseX, mouseY);
        flatButton(ctx, doneX, DONE_W, "Done", mouseX, mouseY);

        GuiSounds.endFrame();
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawRow(GuiGraphicsExtractor ctx, String name, int ry, int mouseX, int mouseY, int idx) {
        boolean rowHovered = mouseX >= listX && mouseX < listX + contentW && mouseY >= ry && mouseY < ry + ROW_H;
        if (rowHovered) {
            GuiSounds.hover("route:" + idx);
            ctx.fill(listX, ry, listX + contentW, ry + ROW_H, GuiStyle.rowHover());
        }

        ctx.fill(listX, ry + 2, listX + nameW, ry + ROW_H - 2, GuiStyle.input());
        drawBorder(ctx, listX, ry + 2, nameW, ROW_H - 4, GuiStyle.border());

        List<OrderedWaypoints.Wp> wps = RouteStorage.get(name);
        int count = wps == null ? 0 : wps.size();
        ctx.text(this.font, "§7" + count + " pts", countX + 4, ry + 9, GuiStyle.textDim());

        boolean active = OrderedWaypoints.isActive(name);
        boolean loadHovered = in(mouseX, mouseY, loadX, ry, LOAD_W, 22);
        if (loadHovered) ctx.fill(loadX, ry + 3, loadX + LOAD_W, ry + ROW_H - 3, GuiStyle.rowHover());
        ctx.fill(loadX + 5, ry + 11, loadX + 9, ry + 15, active ? GuiStyle.accent() : GuiStyle.toggleOff());
        ctx.text(this.font, active ? "Loaded" : "Load", loadX + 14, ry + 9,
                active ? GuiStyle.text() : GuiStyle.textFaint());

        boolean editHovered = in(mouseX, mouseY, editX, ry, EDIT_W, 22);
        ctx.text(this.font, "Edit", editX + 8, ry + 9, editHovered ? GuiStyle.text() : GuiStyle.textFaint());

        boolean removeHovered = in(mouseX, mouseY, removeX, ry, REMOVE_W, 22);
        ctx.centeredText(this.font, "✕", removeX + REMOVE_W / 2, ry + 9,
                removeHovered ? 0xFFFF5555 : GuiStyle.textFaint());
    }

    private void drawScrollbar(GuiGraphicsExtractor ctx, int size) {
        int trackH = VISIBLE * ROW_H;
        int x = listX + contentW + 4;
        ctx.fill(x, listY, x + 4, listY + trackH, GuiStyle.scrollTrack());
        if (size <= VISIBLE) return;
        int thumbH = Math.max(18, trackH * VISIBLE / size);
        int thumbY = listY + (trackH - thumbH) * scroll / (size - VISIBLE);
        ctx.fill(x, thumbY, x + 4, thumbY + thumbH, GuiStyle.scrollThumb());
    }

    private void flatButton(GuiGraphicsExtractor ctx, int x, int w, String label, int mouseX, int mouseY) {
        boolean hovered = in(mouseX, mouseY, x, footerY, w, FOOTER_H);
        if (hovered) GuiSounds.hover("route:" + label);
        GuiStyle.panel(ctx, x, footerY, w, FOOTER_H, hovered);
        drawBorder(ctx, x, footerY, w, FOOTER_H, hovered ? GuiStyle.accent() : GuiStyle.border());
        ctx.centeredText(this.font, label, x + w / 2, footerY + 7, GuiStyle.text());
    }

    static void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        commitRenames();
        TeslaMapsConfig.save();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
