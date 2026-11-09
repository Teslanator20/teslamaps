package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.CustomEspRules;
import com.teslamaps.features.CustomEspRules.EspRule;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Rule list for {@link CustomEspRules}; click Edit for the filter form. */
public class EspRuleScreen extends Screen {
    private static final int PANEL_W = 480, PANEL_H = 300;
    private static final int ROW_H = 24, VISIBLE = 8;
    private static final int FOOTER_H = 20;

    private final Screen parent;
    private int panelX, panelY, listX, listY, contentW, footerY;
    private int scroll;
    private EspRule editing;
    private List<String> suggestions = List.of();
    private EditBox typeBox;

    public EspRuleScreen(Screen parent) {
        super(Component.literal("Custom ESP Rules"));
        this.parent = parent;
    }

    private static List<EspRule> rules() { return TeslaMapsConfig.get().customEspRuleList; }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 46;
        contentW = PANEL_W - 32;
        footerY = panelY + PANEL_H - 30;
        rebuild();
        ScreenFit.clamp(this);
    }

    private void rebuild() {
        clearWidgets();
        typeBox = null;
        if (editing == null) return;
        EspRule r = editing;
        int colW = (contentW - 12) / 2;
        int x1 = listX, x2 = listX + colW + 12;
        int y = listY + 10;
        typeBox = field(x1, y, colW, "Entity Type", "zombie", () -> r.entityType, v -> { r.entityType = v; updateSuggestions(v); });
        field(x2, y, colW, "Name", "Pitfighter", () -> r.name, v -> r.name = v);
        y += 44;
        field(x1, y, colW, "Helmet", "Necron", () -> r.helmet, v -> r.helmet = v);
        field(x2, y, colW, "Chestplate", "", () -> r.chestplate, v -> r.chestplate = v);
        y += 34;
        field(x1, y, colW, "Leggings", "", () -> r.leggings, v -> r.leggings = v);
        field(x2, y, colW, "Boots", "", () -> r.boots, v -> r.boots = v);
        y += 34;
        field(x1, y, colW, "Held Item", "Bow", () -> r.heldItem, v -> r.heldItem = v);
        field(x2, y, colW, "Island", "Crimson Isle", () -> r.island, v -> r.island = v);
        y += 34;
        field(x1, y, colW, "Min Health (0 = off)", "0",
                () -> r.minHealth > 0 ? String.valueOf((int) r.minHealth) : "",
                v -> { try { r.minHealth = v.isBlank() ? 0 : Float.parseFloat(v.trim()); } catch (NumberFormatException ignored) {} });
    }

    private EditBox field(int x, int y, int w, String label, String hint, Supplier<String> get, Consumer<String> set) {
        EditBox box = new EditBox(font, x, y + 10, w, 14, Component.literal(label));
        box.setMaxLength(64);
        box.setHint(Component.literal(hint));
        box.setValue(get.get() == null ? "" : get.get());
        box.setResponder(set);
        addRenderableWidget(box);
        return box;
    }

    private void updateSuggestions(String v) {
        suggestions = CustomEspRules.typeSuggestions(v, 6);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (editing == null && rules().size() > VISIBLE) {
            scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, rules().size() - VISIBLE);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (editing != null) {
            if (typeBox != null) {
                int sy = typeBox.getY() + 16;
                for (int i = 0; i < suggestions.size(); i++) {
                    int sx = typeBox.getX() + i % 3 * (typeBox.getWidth() / 3);
                    int yy = sy + i / 3 * 10;
                    if (in(mx, my, sx, yy, typeBox.getWidth() / 3, 10)) {
                        GuiSounds.press();
                        editing.entityType = suggestions.get(i);
                        suggestions = List.of();
                        rebuild();
                        return true;
                    }
                }
            }
            int ty = footerY - 26;
            String[] names = {"Enabled", "Invisible", "NPC Only", "Tracer", "Alert"};
            for (int i = 0; i < names.length; i++) {
                if (in(mx, my, listX + i * 76, ty, 72, 18)) {
                    boolean v = switch (i) {
                        case 0 -> editing.enabled = !editing.enabled;
                        case 1 -> editing.invisible = !editing.invisible;
                        case 2 -> editing.npcOnly = !editing.npcOnly;
                        case 3 -> editing.tracer = !editing.tracer;
                        default -> editing.alert = !editing.alert;
                    };
                    GuiSounds.toggle(v);
                    return true;
                }
            }
            if (in(mx, my, listX + 5 * 76, ty, 50, 18)) {
                GuiSounds.press();
                EspRule r = editing;
                minecraft.gui.setScreen(new ColorPickerWidget(this, r.color, hex -> r.color = hex));
                return true;
            }
            if (in(mx, my, panelX + PANEL_W - 16 - 60, footerY, 60, FOOTER_H)) {
                GuiSounds.press();
                editing = null;
                suggestions = List.of();
                TeslaMapsConfig.save();
                rebuild();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        List<EspRule> list = rules();
        if (in(mx, my, listX, footerY, 90, FOOTER_H)) {
            GuiSounds.press();
            EspRule r = new EspRule();
            list.add(r);
            editing = r;
            rebuild();
            return true;
        }
        if (in(mx, my, panelX + PANEL_W - 16 - 60, footerY, 60, FOOTER_H)) {
            GuiSounds.press();
            onClose();
            return true;
        }
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            int ry = listY + row * ROW_H;
            if (my < ry || my >= ry + ROW_H) continue;
            EspRule r = list.get(idx);
            int right = listX + contentW;
            if (in(mx, my, listX, ry, 18, ROW_H)) {
                r.enabled = !r.enabled;
                GuiSounds.toggle(r.enabled);
            } else if (in(mx, my, right - 20, ry, 20, ROW_H)) {
                GuiSounds.press();
                list.remove(idx);
                scroll = Math.max(0, Math.min(scroll, list.size() - VISIBLE));
            } else if (in(mx, my, right - 60, ry, 36, ROW_H)) {
                GuiSounds.press();
                editing = r;
                rebuild();
            } else if (in(mx, my, right - 104, ry, 40, ROW_H)) {
                r.alert = !r.alert;
                GuiSounds.toggle(r.alert);
            } else if (in(mx, my, right - 156, ry, 48, ROW_H)) {
                r.tracer = !r.tracer;
                GuiSounds.toggle(r.tracer);
            } else if (in(mx, my, right - 186, ry, 26, ROW_H)) {
                GuiSounds.press();
                minecraft.gui.setScreen(new ColorPickerWidget(this, r.color, hex -> r.color = hex));
                return true;
            } else {
                continue;
            }
            TeslaMapsConfig.save();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);
        ctx.text(font, "§f§lCustom ESP Rules", panelX + 16, panelY + 14, GuiStyle.text());
        if (editing == null) {
            ctx.text(font, "§8every filled filter must match · text filters are substrings", panelX + 16, panelY + 28, GuiStyle.text());
            drawList(ctx, mouseX, mouseY);
            flatButton(ctx, listX, footerY, 90, "+ Add Rule", mouseX, mouseY);
        } else {
            ctx.text(font, "§8empty = ignored · gear/held match item name or SkyBlock id", panelX + 16, panelY + 28, GuiStyle.text());
            drawEditor(ctx, mouseX, mouseY);
        }
        flatButton(ctx, panelX + PANEL_W - 16 - 60, footerY, 60, editing == null ? "Done" : "Back", mouseX, mouseY);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (editing != null && typeBox != null && !suggestions.isEmpty()) {
            int sy = typeBox.getY() + 16;
            for (int i = 0; i < suggestions.size(); i++) {
                int sx = typeBox.getX() + i % 3 * (typeBox.getWidth() / 3);
                int yy = sy + i / 3 * 10;
                boolean h = in(mouseX, mouseY, sx, yy, typeBox.getWidth() / 3, 10);
                ctx.text(font, suggestions.get(i), sx, yy, h ? GuiStyle.accent() : GuiStyle.textDim());
            }
        }
    }

    private void drawList(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<EspRule> list = rules();
        if (list.isEmpty()) {
            ctx.text(font, "§7No rules yet — click \"+ Add Rule\".", listX, listY + 6, GuiStyle.text());
            return;
        }
        int right = listX + contentW;
        for (int row = 0; row < VISIBLE; row++) {
            int idx = scroll + row;
            if (idx >= list.size()) break;
            EspRule r = list.get(idx);
            int ry = listY + row * ROW_H;
            if (in(mouseX, mouseY, listX, ry, contentW, ROW_H)) ctx.fill(listX, ry, right, ry + ROW_H, GuiStyle.rowHover());
            ctx.fill(listX + 5, ry + 9, listX + 11, ry + 15, r.enabled ? GuiStyle.accent() : GuiStyle.toggleOff());
            String s = r.summary();
            int maxW = contentW - 18 - 190;
            if (font.width(s) > maxW) s = font.plainSubstrByWidth(s, maxW - 6) + "…";
            ctx.text(font, s, listX + 18, ry + 8, r.enabled ? GuiStyle.text() : GuiStyle.textFaint());
            int sx = right - 184;
            ctx.fill(sx, ry + 5, sx + 22, ry + 19, TeslaMapsConfig.parseColor(r.color) | 0xFF000000);
            flag(ctx, right - 156, ry, "Tracer", r.tracer);
            flag(ctx, right - 104, ry, "Alert", r.alert);
            ctx.text(font, "Edit", right - 56, ry + 8, in(mouseX, mouseY, right - 60, ry, 36, ROW_H) ? GuiStyle.accent() : GuiStyle.textDim());
            ctx.centeredText(font, "✕", right - 10, ry + 8, in(mouseX, mouseY, right - 20, ry, 20, ROW_H) ? 0xFFFF5555 : GuiStyle.textFaint());
        }
    }

    private void flag(GuiGraphicsExtractor ctx, int x, int ry, String label, boolean on) {
        ctx.fill(x + 2, ry + 10, x + 6, ry + 14, on ? GuiStyle.accent() : GuiStyle.toggleOff());
        ctx.text(font, label, x + 10, ry + 8, on ? GuiStyle.text() : GuiStyle.textFaint());
    }

    private void drawEditor(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        for (var w : children()) {
            if (w instanceof EditBox b) ctx.text(font, b.getMessage().getString(), b.getX(), b.getY() - 10, GuiStyle.textDim());
        }
        int ty = footerY - 26;
        String[] names = {"Enabled", "Invisible", "NPC Only", "Tracer", "Alert"};
        boolean[] vals = {editing.enabled, editing.invisible, editing.npcOnly, editing.tracer, editing.alert};
        for (int i = 0; i < names.length; i++) {
            int x = listX + i * 76;
            if (in(mouseX, mouseY, x, ty, 72, 18)) ctx.fill(x, ty, x + 72, ty + 18, GuiStyle.rowHover());
            flag(ctx, x, ty - 3, names[i], vals[i]);
        }
        int cx = listX + 5 * 76;
        ctx.text(font, "Color", cx, ty + 5, GuiStyle.textDim());
        ctx.fill(cx + 30, ty + 2, cx + 50, ty + 16, TeslaMapsConfig.parseColor(editing.color) | 0xFF000000);
    }

    private void flatButton(GuiGraphicsExtractor ctx, int x, int y, int w, String label, int mouseX, int mouseY) {
        boolean hovered = in(mouseX, mouseY, x, y, w, FOOTER_H);
        GuiStyle.panel(ctx, x, y, w, FOOTER_H, hovered);
        ctx.centeredText(font, label, x + w / 2, y + 6, GuiStyle.text());
    }

    @Override
    public void onClose() {
        rules().removeIf(r -> r == null || !r.hasFilters());
        TeslaMapsConfig.save();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
