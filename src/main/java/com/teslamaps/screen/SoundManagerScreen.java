package com.teslamaps.screen;

// Ported from NoammAddons' SoundManagerScreen.kt (layout adapted to HudStyle panels)

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoundVolumes;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SoundManagerScreen extends Screen {

    private static final String[] CATEGORIES = {"All", "Recent", "Adjusted", "Blocks", "Hostile Mobs",
            "Neutral Mobs", "Music", "Ambient", "Items", "UI", "Misc"};
    private static final int ROW_H = 20, SIDE_H = 16, MARGIN = 10, SLIDER_W = 120, PLAY_W = 30;

    private record Sound(String id, String name, String category, SoundEvent event, String search) {}

    private static int selected = 1;
    private static String lastQuery = "";

    private final Screen parent;
    private final List<Sound> all = new ArrayList<>();
    private final Map<String, Sound> byId = new HashMap<>();
    private List<Sound> filtered = new ArrayList<>();
    private long recentVersion = -1;
    private EditBox field;
    private int scroll = 0;
    private String dragging = null;

    private int panelX, panelY, panelW, panelH, sideX, sideW, listX, listY, listW, listH, visibleRows;

    public SoundManagerScreen(Screen parent) {
        super(Component.literal("Sound Manager"));
        this.parent = parent;
        for (var e : BuiltInRegistries.SOUND_EVENT.entrySet()) {
            String id = e.getKey().identifier().toString();
            String name = cleanName(id);
            Sound s = new Sound(id, name, category(id), e.getValue(), (id + " " + name).toLowerCase());
            all.add(s);
            byId.put(id, s);
        }
        all.sort((a, b) -> a.id.compareTo(b.id));
    }

    @Override
    protected void init() {
        panelW = Math.min(width - MARGIN * 2, 560);
        panelH = Math.min(height - MARGIN * 2, 320);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        sideW = 90;
        sideX = panelX + 8;
        listX = sideX + sideW + 6;
        listY = panelY + 58;
        listW = panelX + panelW - 8 - listX;
        listH = panelH - 58 - 10;
        visibleRows = Math.max(1, listH / ROW_H);

        field = new EditBox(font, panelX + 12, panelY + 32, panelW - 24, 16, Component.literal("Search"));
        field.setHint(Component.literal("Search sounds..."));
        field.setMaxLength(64);
        field.setResponder(q -> {
            lastQuery = q;
            scroll = 0;
            rebuild();
        });
        field.setValue(lastQuery);
        addRenderableWidget(field);
        rebuild();
        ScreenFit.clamp(this);
    }

    private void rebuild() {
        String q = lastQuery == null ? "" : lastQuery.trim().toLowerCase();
        List<Sound> src;
        String cat = CATEGORIES[selected];
        if (cat.equals("Recent")) {
            src = new ArrayList<>();
            for (String id : SoundVolumes.recentIds()) {
                Sound s = byId.get(id);
                src.add(s != null ? s : new Sound(id, cleanName(id), "Misc", null, id.toLowerCase()));
            }
            recentVersion = SoundVolumes.recentVersion();
        } else if (cat.equals("Adjusted")) {
            src = new ArrayList<>();
            for (String id : TeslaMapsConfig.get().soundVolumes.keySet()) {
                Sound s = byId.get(id);
                src.add(s != null ? s : new Sound(id, cleanName(id), "Misc", null, id.toLowerCase()));
            }
            src.sort((a, b) -> a.id.compareTo(b.id));
        } else {
            src = all;
        }
        filtered = new ArrayList<>();
        for (Sound s : src) {
            if (!cat.equals("All") && !cat.equals("Recent") && !cat.equals("Adjusted") && !s.category.equals(cat)) continue;
            if (!q.isEmpty() && !s.search.contains(q)) continue;
            filtered.add(s);
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, filtered.size() - visibleRows));
    }

    private int sliderX() { return listX + listW - PLAY_W - 8 - SLIDER_W - 6; }
    private int playX() { return listX + listW - PLAY_W - 6; }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, filtered.size() - visibleRows);
        scroll = Math.clamp(scroll - (int) Math.signum(sy) * 2, 0, max);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (mx >= sideX && mx < sideX + sideW && my >= listY && my < listY + CATEGORIES.length * SIDE_H) {
            selected = (int) ((my - listY) / SIDE_H);
            scroll = 0;
            rebuild();
            return true;
        }
        int index = rowAt(mx, my);
        if (index >= 0) {
            Sound s = filtered.get(index);
            int y = listY + (index - scroll) * ROW_H;
            if (mx >= playX() && mx < playX() + PLAY_W && my >= y + 3 && my < y + ROW_H - 3) {
                if (s.event != null) SoundVolumes.preview(s.event);
                return true;
            }
            if (mx >= sliderX() - 4 && mx <= sliderX() + SLIDER_W + 4) {
                if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) SoundVolumes.setPercent(s.id, 100);
                else {
                    dragging = s.id;
                    setFromMouse(s.id, mx);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            setFromMouse(dragging, event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            if (CATEGORIES[selected].equals("Adjusted")) rebuild();
        }
        return super.mouseReleased(event);
    }

    private void setFromMouse(String id, double mx) {
        double p = Math.clamp((mx - sliderX()) / SLIDER_W, 0.0, 1.0);
        SoundVolumes.setPercent(id, (int) Math.round(p * 200));
    }

    private int rowAt(double mx, double my) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + visibleRows * ROW_H) return -1;
        int index = scroll + (int) ((my - listY) / ROW_H);
        return index >= 0 && index < filtered.size() ? index : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (CATEGORIES[selected].equals("Recent") && recentVersion != SoundVolumes.recentVersion()) rebuild();

        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);
        ctx.text(font, "§f§lSound Manager", panelX + 12, panelY + 12, 0xFFFFFFFF);
        if (!TeslaMapsConfig.get().soundManager) {
            String off = "§cSound Manager is off (Sounds category)";
            ctx.text(font, off, panelX + panelW - 12 - font.width(off), panelY + 12, 0xFFFFFFFF);
        }

        for (int i = 0; i < CATEGORIES.length; i++) {
            int y = listY + i * SIDE_H;
            boolean hover = mouseX >= sideX && mouseX < sideX + sideW && mouseY >= y && mouseY < y + SIDE_H;
            if (i == selected) ctx.fill(sideX, y, sideX + sideW, y + SIDE_H, 0x5000A0A0);
            else if (hover) ctx.fill(sideX, y, sideX + sideW, y + SIDE_H, 0x30FFFFFF);
            ctx.text(font, (i == selected ? "§b" : "§7") + CATEGORIES[i], sideX + 4, y + 4, 0xFFFFFFFF);
        }

        int hovered = rowAt(mouseX, mouseY);
        int sx = sliderX(), px = playX();
        for (int i = 0; i < visibleRows; i++) {
            int index = scroll + i;
            if (index >= filtered.size()) break;
            Sound s = filtered.get(index);
            int y = listY + i * ROW_H;
            if (index == hovered) ctx.fill(listX, y, listX + listW, y + ROW_H, 0x30FFFFFF);

            String name = s.name;
            int maxW = sx - listX - 50;
            if (font.width(name) > maxW) name = font.plainSubstrByWidth(name, maxW - font.width("…")) + "…";
            ctx.text(font, "§f" + name, listX + 5, y + 6, 0xFFFFFFFF);

            int pct = SoundVolumes.percent(s.id);
            String val = (pct == 100 ? "§8" : pct == 0 ? "§c" : "§e") + pct + "%";
            ctx.text(font, val, sx - 6 - font.width(val), y + 6, 0xFFFFFFFF);
            float prog = pct / 200f;
            ctx.fill(sx, y + 8, sx + SLIDER_W, y + 12, 0x40FFFFFF);
            ctx.fill(sx, y + 8, sx + Math.round(SLIDER_W * prog), y + 12, 0xFF00AAAA);
            ctx.fill(sx + SLIDER_W / 2, y + 6, sx + SLIDER_W / 2 + 1, y + 14, 0x80FFFFFF);
            int kx = sx + Math.round(SLIDER_W * prog);
            ctx.fill(kx - 2, y + 5, kx + 2, y + 15, 0xFFFFFFFF);

            boolean playHover = index == hovered && mouseX >= px && mouseX < px + PLAY_W;
            ctx.fill(px, y + 3, px + PLAY_W, y + ROW_H - 3, playHover ? 0x8000AAAA : 0x30FFFFFF);
            String play = s.event == null ? "§8-" : "Play";
            ctx.text(font, play, px + (PLAY_W - font.width(play)) / 2, y + 6, 0xFFDDDDDD);
        }

        if (filtered.isEmpty()) {
            String msg = CATEGORIES[selected].equals("Recent") ? "§7No sounds played yet" : "§7No sounds";
            ctx.text(font, msg, listX + (listW - font.width(msg)) / 2, listY + 20, 0xFFAAAAAA);
        }
        if (filtered.size() > visibleRows) {
            String pos = "§8" + (scroll + 1) + "-" + Math.min(filtered.size(), scroll + visibleRows) + "/" + filtered.size();
            ctx.text(font, pos, panelX + panelW - 12 - font.width(pos), panelY + 22, 0xFF808080);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        if (hovered >= 0 && dragging == null) {
            Sound s = filtered.get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal("§b" + s.id));
            lines.add(Component.literal("§7Drag the bar: 0-200 % in 5 % steps"));
            lines.add(Component.literal("§7Right-click the bar: reset to 100 %"));
            ctx.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String cleanName(String id) {
        String path = id.startsWith("minecraft:") ? id.substring(10) : id;
        if (path.startsWith("entity.hostile.")) path = path.substring(15);
        else if (path.startsWith("entity.")) path = path.substring(7);
        else if (path.contains(".")) path = path.substring(path.indexOf('.') + 1);
        return path.replace('.', ' ').replace('_', ' ');
    }

    private static String category(String id) {
        String path = id.startsWith("minecraft:") ? id.substring(10) : id;
        if (path.startsWith("block")) return "Blocks";
        if (path.startsWith("entity.hostile")) return "Hostile Mobs";
        if (path.startsWith("entity")) return "Neutral Mobs";
        if (path.startsWith("music")) return "Music";
        if (path.startsWith("ambient")) return "Ambient";
        if (path.startsWith("item")) return "Items";
        if (path.startsWith("ui")) return "UI";
        return "Misc";
    }
}
