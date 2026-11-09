package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonWaypoints;
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

/** Dungeon waypoint packs: show/hide, pick the edit pack, create, rename, delete, import, export. */
public class WaypointPackScreen extends Screen {
    private static final int PANEL_W = 470, PANEL_H = 300;
    private static final int ROW_H = 26, VISIBLE = 6;
    private static final int COUNT_W = 52, SHOW_W = 62, EDIT_W = 58, REMOVE_W = 20, GAP = 6;
    private static final int FOOTER_H = 22;
    private static final String[] FOOTER = {"New Pack", "Import Clipboard", "Export Edit Pack", "Reset Room", "Done"};
    private static final int[] FOOTER_W = {70, 100, 96, 84, 50};

    private final Screen parent;
    private int panelX, panelY, listX, listY, contentW, nameW;
    private int countX, showX, editX, removeX, footerY;
    private final int[] footerX = new int[FOOTER.length];
    private int scroll = 0;
    private EditBox newName;
    private final List<EditBox> nameBoxes = new ArrayList<>();
    private final List<String> nameBoxOriginals = new ArrayList<>();

    public WaypointPackScreen(Screen parent) {
        super(Component.literal("Waypoint Packs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 70;
        contentW = PANEL_W - 32 - 10;
        nameW = contentW - COUNT_W - SHOW_W - EDIT_W - REMOVE_W - GAP * 4;
        countX = listX + nameW + GAP;
        showX = countX + COUNT_W + GAP;
        editX = showX + SHOW_W + GAP;
        removeX = editX + EDIT_W + GAP;
        footerY = panelY + PANEL_H - 32;
        int fx = listX;
        for (int i = 0; i < FOOTER.length; i++) {
            footerX[i] = fx;
            fx += FOOTER_W[i] + 8;
        }
        rebuild();
    }

    private void rebuild() {
        String typed = newName == null ? "" : newName.getValue();
        clearWidgets();
        nameBoxes.clear();
        nameBoxOriginals.clear();
        newName = new EditBox(font, listX + 96, panelY + 44, 180, 14, Component.literal("new pack name"));
        newName.setMaxLength(32);
        newName.setValue(typed);
        addRenderableWidget(newName);

        List<String> list = DungeonWaypoints.packNames();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - VISIBLE)));
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            String name = list.get(idx);
            if (DungeonWaypoints.DEFAULT_PACK.equals(name)) continue;
            EditBox box = new EditBox(font, listX + 6, listY + row * ROW_H + 7, nameW - 12, 12, Component.literal("name"));
            box.setMaxLength(32);
            box.setBordered(false);
            box.setValue(name);
            addRenderableWidget(box);
            nameBoxes.add(box);
            nameBoxOriginals.add(name);
        }
        ScreenFit.clamp(this);
    }

    private boolean commitRenames() {
        boolean changed = false;
        for (int i = 0; i < nameBoxes.size(); i++) {
            String to = nameBoxes.get(i).getValue().trim();
            String from = nameBoxOriginals.get(i);
            if (to.isBlank() || to.equals(from)) continue;
            String r = DungeonWaypoints.renamePack(from, to);
            if (r != null) feedback(r);
            changed = true;
        }
        return changed;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int size = DungeonWaypoints.packNames().size();
        if (size > VISIBLE) {
            scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, size - VISIBLE);
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        if (commitRenames()) rebuild();

        for (int i = 0; i < FOOTER.length; i++) {
            if (!in(mx, my, footerX[i], footerY, FOOTER_W[i], FOOTER_H)) continue;
            GuiSounds.press();
            switch (i) {
                case 0 -> { feedback(DungeonWaypoints.createPack(newName.getValue())); newName.setValue(""); }
                case 1 -> { feedback(DungeonWaypoints.importPack(newName.getValue(), minecraft.keyboardHandler.getClipboard())); newName.setValue(""); }
                case 2 -> feedback(DungeonWaypoints.exportEditPack());
                case 3 -> feedback(DungeonWaypoints.clearRoom());
                default -> { onClose(); return true; }
            }
            rebuild();
            return true;
        }

        List<String> list = DungeonWaypoints.packNames();
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            int ry = listY + row * ROW_H;
            if (my < ry || my >= ry + ROW_H) continue;
            String name = list.get(idx);
            if (in(mx, my, showX, ry, SHOW_W, 22)) {
                GuiSounds.press();
                feedback(DungeonWaypoints.togglePack(name));
                return true;
            }
            if (in(mx, my, editX, ry, EDIT_W, 22)) {
                GuiSounds.press();
                feedback(DungeonWaypoints.setEditPack(name));
                return true;
            }
            if (in(mx, my, removeX, ry, REMOVE_W, 22)) {
                GuiSounds.press();
                feedback(DungeonWaypoints.deletePack(name));
                rebuild();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && message != null) mc.player.sendSystemMessage(Component.literal("§b[WP] §r" + message));
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);
        ctx.text(font, "§f§lWaypoint Packs", panelX + 16, panelY + 16, GuiStyle.text());
        ctx.text(font, "§8edit pack: §7" + TeslaMapsConfig.get().waypointEditPack
                + " §8· new waypoints and removals go there", panelX + 16, panelY + 30, GuiStyle.text());
        ctx.text(font, "§7New pack name:", listX, panelY + 47, GuiStyle.text());
        ctx.fill(listX + 94, panelY + 42, listX + 278, panelY + 60, GuiStyle.input());

        List<String> list = DungeonWaypoints.packNames();
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            drawRow(ctx, list.get(idx), listY + row * ROW_H, mouseX, mouseY, idx);
        }
        for (int i = 0; i < FOOTER.length; i++) flatButton(ctx, footerX[i], FOOTER_W[i], FOOTER[i], mouseX, mouseY);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawRow(GuiGraphicsExtractor ctx, String name, int ry, int mouseX, int mouseY, int idx) {
        if (mouseX >= listX && mouseX < listX + contentW && mouseY >= ry && mouseY < ry + ROW_H) {
            ctx.fill(listX, ry, listX + contentW, ry + ROW_H, GuiStyle.rowHover());
        }
        ctx.fill(listX, ry + 2, listX + nameW, ry + ROW_H - 2, GuiStyle.input());
        WaypointRoutesScreen.drawBorder(ctx, listX, ry + 2, nameW, ROW_H - 4, GuiStyle.border());
        if (DungeonWaypoints.DEFAULT_PACK.equals(name))
            ctx.text(font, "§7Default §8(dungeon_waypoints.json)", listX + 6, ry + 9, GuiStyle.text());
        ctx.text(font, "§7" + DungeonWaypoints.packSize(name) + " pts", countX + 4, ry + 9, GuiStyle.textDim());

        boolean shown = DungeonWaypoints.isPackActive(name);
        ctx.fill(showX + 5, ry + 11, showX + 9, ry + 15, shown ? GuiStyle.accent() : GuiStyle.toggleOff());
        ctx.text(font, shown ? "Shown" : "Hidden", showX + 14, ry + 9,
                in(mouseX, mouseY, showX, ry, SHOW_W, 22) || shown ? GuiStyle.text() : GuiStyle.textFaint());

        boolean edit = DungeonWaypoints.isEditPack(name);
        ctx.fill(editX + 5, ry + 11, editX + 9, ry + 15, edit ? 0xFFFFAA00 : GuiStyle.toggleOff());
        ctx.text(font, edit ? "Editing" : "Edit", editX + 14, ry + 9,
                in(mouseX, mouseY, editX, ry, EDIT_W, 22) || edit ? GuiStyle.text() : GuiStyle.textFaint());

        if (!DungeonWaypoints.DEFAULT_PACK.equals(name))
            ctx.centeredText(font, "✕", removeX + REMOVE_W / 2, ry + 9,
                    in(mouseX, mouseY, removeX, ry, REMOVE_W, 22) ? 0xFFFF5555 : GuiStyle.textFaint());
    }

    private void flatButton(GuiGraphicsExtractor ctx, int x, int w, String label, int mouseX, int mouseY) {
        boolean hovered = in(mouseX, mouseY, x, footerY, w, FOOTER_H);
        GuiStyle.panel(ctx, x, footerY, w, FOOTER_H, hovered);
        WaypointRoutesScreen.drawBorder(ctx, x, footerY, w, FOOTER_H, hovered ? GuiStyle.accent() : GuiStyle.border());
        ctx.centeredText(font, label, x + w / 2, footerY + 7, GuiStyle.text());
    }

    @Override
    public void onClose() {
        commitRenames();
        TeslaMapsConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
