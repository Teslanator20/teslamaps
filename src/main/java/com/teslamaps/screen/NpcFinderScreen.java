package com.teslamaps.screen;

// Ported from bax's NpcFinderScreen.java (ImGui layout adapted to HudStyle panels)

import com.teslamaps.features.npc.NpcData;
import com.teslamaps.features.npc.NpcDatabase;
import com.teslamaps.features.npc.NpcFinder;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** /tmap npc - search all known NPCs, click to warp + track. */
public class NpcFinderScreen extends Screen {

    private static final int ROW_H = 20, SIDE_H = 14, MARGIN = 10;

    private static String lastQuery = "";
    private static String selectedIsland = null;

    private EditBox field;
    private List<NpcData> filtered = new ArrayList<>();
    private Map<String, Integer> islandCounts;
    private List<String> islands;
    private int scroll = 0, sideScroll = 0;

    private int panelX, panelY, panelW, panelH, sideX, sideW, listX, listY, listH, visibleRows, visibleSide;

    public NpcFinderScreen(String query) {
        super(Component.literal("NPC Finder"));
        if (query != null && !query.isBlank()) lastQuery = query.trim();
    }

    @Override
    protected void init() {
        islandCounts = new LinkedHashMap<>();
        for (NpcData npc : NpcDatabase.getUnique()) {
            islandCounts.merge(npc.island(), 1, Integer::sum);
        }
        islands = new ArrayList<>(islandCounts.keySet());

        panelW = Math.min(width - MARGIN * 2, 540);
        panelH = Math.min(height - MARGIN * 2, 340);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        sideW = font.width("All") + 6;
        for (String island : islands) {
            int w = font.width(island) + font.width(String.valueOf(islandCounts.get(island))) + 14;
            if (w > sideW) sideW = w;
        }
        sideW = Math.max(sideW + 12, 110);

        sideX = panelX + 8;
        listX = sideX + sideW + 6;
        listY = panelY + 62;
        listH = panelH - 62 - 12;
        visibleRows = Math.max(1, listH / ROW_H);
        visibleSide = Math.max(1, listH / SIDE_H);

        field = new EditBox(font, panelX + 12, panelY + 34, panelW - 24, 16, Component.literal("Search"));
        field.setHint(Component.literal("Search NPCs..."));
        field.setMaxLength(64);
        field.setResponder(q -> {
            lastQuery = q;
            scroll = 0;
            rebuild();
        });
        field.setValue(lastQuery);
        addRenderableWidget(field);
        setInitialFocus(field);

        rebuild();
        ScreenFit.clamp(this);
    }

    private void rebuild() {
        String q = lastQuery == null ? "" : lastQuery.trim().toLowerCase();
        filtered = new ArrayList<>();
        for (NpcData npc : NpcDatabase.getUnique()) {
            if (selectedIsland != null && !npc.island().equals(selectedIsland)) continue;
            if (!q.isEmpty()
                    && !npc.name().toLowerCase().contains(q)
                    && !npc.island().toLowerCase().contains(q)
                    && !npc.description().toLowerCase().contains(q)) continue;
            filtered.add(npc);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= sideX && mx < sideX + sideW && my >= listY && my < listY + listH) {
            int max = Math.max(0, islands.size() + 1 - visibleSide);
            sideScroll = Math.clamp(sideScroll - (int) Math.signum(sy), 0, max);
        } else {
            int max = Math.max(0, filtered.size() - visibleRows);
            scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, max);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && field != null && field.isMouseOver(event.x(), event.y())) {
            field.setValue("");
            field.setFocused(true);
            return true;
        }
        int side = sideAt(event.x(), event.y());
        if (side >= 0) {
            String island = side == 0 ? null : islands.get(side - 1);
            selectedIsland = Objects.equals(selectedIsland, island) ? selectedIsland : island;
            scroll = 0;
            rebuild();
            return true;
        }
        int index = rowAt(event.x(), event.y());
        if (index >= 0) {
            handleNpcClick(filtered.get(index));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void handleNpcClick(NpcData npc) {
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        if (!npc.hasPosition()) {
            minecraft.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cNo coordinates available for " + npc.name()));
            return;
        }
        List<NpcData> positions = NpcDatabase.getPositions(npc.name(), npc.island());
        NpcFinder.setTargets(positions);
        String suffix = " (" + positions.size() + " location" + (positions.size() > 1 ? "s" : "") + ")";
        if (NpcFinder.isOnTargetIsland(npc)) {
            minecraft.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Tracking §f" + npc.name() + "§7" + suffix));
        } else {
            com.teslamaps.features.npc.NpcWarps.warp(com.teslamaps.features.npc.NpcWarps.warpFor(npc), true);
            minecraft.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Warping to §f" + npc.name() + "§7 on §f" + npc.island() + "§7" + suffix));
        }
        onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && field != null && field.isFocused()
                && !field.getValue().isEmpty()) {
            field.setValue("");
            return true;
        }
        return super.keyPressed(event);
    }

    private int sideAt(double mx, double my) {
        if (mx < sideX || mx >= sideX + sideW) return -1;
        if (my < listY || my >= listY + Math.min(visibleSide, islands.size() + 1 - sideScroll) * SIDE_H) return -1;
        int index = sideScroll + (int) ((my - listY) / SIDE_H);
        return index >= 0 && index <= islands.size() ? index : -1;
    }

    private int rowAt(double mx, double my) {
        if (mx < listX || mx > panelX + panelW - 8) return -1;
        if (my < listY || my >= listY + visibleRows * ROW_H) return -1;
        int index = scroll + (int) ((my - listY) / ROW_H);
        return index >= 0 && index < filtered.size() ? index : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);

        ctx.text(font, "§f§lNPC Finder", panelX + 12, panelY + 12, 0xFFFFFFFF);

        int sideHovered = sideAt(mouseX, mouseY);
        for (int i = 0; i < visibleSide; i++) {
            int index = sideScroll + i;
            if (index > islands.size()) break;
            int y = listY + i * SIDE_H;
            String label = index == 0 ? "All" : islands.get(index - 1);
            int count = index == 0 ? NpcDatabase.getUnique().size() : islandCounts.get(label);
            boolean selected = index == 0 ? selectedIsland == null : label.equals(selectedIsland);
            if (selected) ctx.fill(sideX, y, sideX + sideW, y + SIDE_H, 0x5000A0A0);
            else if (index == sideHovered) ctx.fill(sideX, y, sideX + sideW, y + SIDE_H, 0x30FFFFFF);
            ctx.text(font, (selected ? "§b" : "§7") + label, sideX + 4, y + 3, 0xFFFFFFFF);
            String c = String.valueOf(count);
            ctx.text(font, "§8" + c, sideX + sideW - 4 - font.width(c), y + 3, 0xFF808080);
        }

        int hovered = rowAt(mouseX, mouseY);
        int listW = panelX + panelW - 8 - listX;
        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= filtered.size()) break;
            NpcData npc = filtered.get(index);
            int y = listY + i * ROW_H;
            if (index == hovered) ctx.fill(listX, y, listX + listW, y + ROW_H, 0x30FFFFFF);

            ctx.text(font, "§f" + trim(npc.name(), 28), listX + 6, y + 6, 0xFFFFFFFF);

            String right;
            if (!npc.hasPosition()) right = "§cNo coords";
            else if (selectedIsland != null) {
                String desc = npc.description();
                right = desc != null && !desc.isEmpty() && !desc.equals("NPC") ? "§8" + trim(desc, 30) : "";
            } else right = "§8" + npc.island();
            if (!right.isEmpty()) ctx.text(font, right, listX + listW - 6 - font.width(right), y + 6, 0xFF909090);
        }

        if (filtered.isEmpty()) {
            String msg = "§7No results for \"" + lastQuery + "\"";
            ctx.text(font, msg, listX + (listW - font.width(msg)) / 2, listY + 20, 0xFFAAAAAA);
        }
        if (filtered.size() > visibleRows) {
            String pos = "§8" + (scroll + 1) + "-" + Math.min(filtered.size(), scroll + visibleRows) + "/" + filtered.size();
            ctx.text(font, pos, panelX + panelW - 12 - font.width(pos), panelY + 16, 0xFF808080);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        if (hovered >= 0) {
            NpcData npc = filtered.get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("§b" + npc.name()));
            lines.add(Component.literal("§7" + npc.description()));
            lines.add(Component.literal(npc.hasPosition()
                    ? "§8Click to warp to " + npc.island()
                    : "§cNo coordinates available"));
            ctx.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
