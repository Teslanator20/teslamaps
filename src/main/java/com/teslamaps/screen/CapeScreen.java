package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.capes.CapeGallery;
import com.teslamaps.features.capes.CapeImport;
import com.teslamaps.features.capes.CapeManager;
import com.teslamaps.render.gui.HudStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Cape picker: what you own, a gallery of drawn capes, and a player lookup. */
public class CapeScreen extends Screen {


    // the cape front sits at 1,1 .. 11,17 of the 64x32 grid, at any resolution
    private static final float U0 = 1f / 64f, U1 = 11f / 64f, V0 = 1f / 32f, V1 = 17f / 32f;

    private static final int MARGIN = 8;
    private static final int PREVIEW_W = 112;
    private static final int CARD_W = 68, IMG_W = 40, IMG_H = 64, LABEL_H = 12, GAP = 8;
    private static final int CARD_H = IMG_H + LABEL_H;
    private static final int HEADER_H = 36, GROUP_H = 15;
    private static final int FOOTER_H = 86;

    /** One tile in the grid. {@code frontOnly} means the texture is already the cape front. */
    private record Card(String name, String group, Supplier<Identifier> texture, boolean frontOnly,
                        Runnable click, BooleanSupplier active) {}

    private record Slot(Card card, int x, int y) {}

    private final Screen parent;
    private final List<Slot> slots = new ArrayList<>();

    private static String filter = "";     // survives a reopen

    private EditBox field;
    private boolean draggingBar;
    private boolean draggingWaviness;
    private int scroll, contentHeight, shownGeneration = -1;
    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridW, gridH, cols;
    private String status = "";

    public CapeScreen(Screen parent) {
        super(Component.literal("Capes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CapeImport.clear();
        scroll = 0;
        shownGeneration = -1;
        panelW = Math.min(width - MARGIN * 2, 560);
        panelH = Math.min(height - MARGIN * 2, 330);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        gridX = panelX + PREVIEW_W + 20;
        gridY = panelY + HEADER_H;
        gridW = panelX + panelW - 12 - gridX;
        gridH = panelH - HEADER_H - FOOTER_H;
        cols = Math.max(1, (gridW + GAP) / (CARD_W + GAP));

        layout();

        int rowY = panelY + panelH - 46;
        int actionW = 52;
        field = new EditBox(font, panelX + 12, rowY, panelW - 24 - actionW - 6, 16,
                Component.literal(hint()));
        field.setHint(Component.literal(hint()));
        field.setMaxLength(512);
        field.setValue(filter);
        // filters on every keystroke - locals directly, the gallery catalogue once loaded
        field.setResponder(value -> {
            String next = looksLikeUrl(value) ? "" : value.trim();
            if (!next.equals(filter)) {
                filter = next;
                scroll = 0;
                CapeGallery.search(next);
                layout();
            }
        });
        addRenderableWidget(field);
        addRenderableWidget(new TeslaButton(panelX + panelW - 12 - actionW, rowY, actionW, 16,
                "§fLoad", this::submit));

        int by = panelY + panelH - 24;
        int bw = Math.max(44, (panelW - 24 - 4 * 6) / 5);
        addRenderableWidget(new TeslaButton(panelX + 12, by, bw, 18, "§bFrom File", CapeImport::pickFile));
        addRenderableWidget(new TeslaButton(panelX + 12 + bw + 6, by, bw, 18, "§eFolder",
                () -> com.mojang.blaze3d.Blaze3D.openPath(CapeManager.DIR)));
        addRenderableWidget(new TeslaButton(panelX + 12 + (bw + 6) * 2, by, bw, 18, "§dPublish", this::publish));
        addRenderableWidget(new TeslaButton(panelX + 12 + (bw + 6) * 3, by, bw, 18, "§cDelete", this::deleteSelected));
        addRenderableWidget(new TeslaButton(panelX + 12 + (bw + 6) * 4, by, bw, 18, "§fClose", this::onClose));

        if (CapeGallery.results().isEmpty()) CapeGallery.search(filter);

        ScreenFit.clamp(this);
    }

    private String hint() {
        return "search capes, or paste a png / gif / tenor link";
    }

    private static boolean looksLikeUrl(String value) {
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        return v.startsWith("http://") || v.startsWith("https://");
    }

    private void submit() {
        String value = field.getValue().trim();
        status = "";
        CapeImport.clear();     // a finished import must not keep the status line hostage
        if (looksLikeUrl(value)) CapeImport.fromUrl(value);
        else status = "§7Typing filters the grid - Load wants a link";
    }

    /**
     * One grid for everything: Off, the builtins, your local files and every gallery
     * cape that is not already on disk - all grouped by category. Clicking a gallery
     * card downloads it into the matching folder and wears it.
     */
    private void layout() {
        slots.clear();
        Map<String, List<Card>> groups = new LinkedHashMap<>();

        String query = filter.toLowerCase(java.util.Locale.ROOT);
        if (query.isEmpty()) {
            groups.put("None", new ArrayList<>(List.of(new Card("Off", "None", () -> null, false,
                    () -> {
                        TeslaMapsConfig.get().capeEnabled = false;
                        CapeManager.select("");
                    },
                    () -> !TeslaMapsConfig.get().capeEnabled || CapeManager.selected() == null))));
        }

        java.util.Set<String> local = new java.util.HashSet<>();
        for (CapeManager.Cape cape : CapeManager.all()) {
            if (cape.custom()) {
                local.add((cape.group + "/" + cape.name).toLowerCase(java.util.Locale.ROOT));
            }
            if (!query.isEmpty()
                    && !cape.name.toLowerCase(java.util.Locale.ROOT).contains(query)
                    && !cape.group.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            groups.computeIfAbsent(cape.group, g -> new ArrayList<>()).add(new Card(
                    cape.name, cape.group, cape::texture, false,
                    () -> CapeManager.select(cape.id),
                    () -> TeslaMapsConfig.get().capeEnabled && CapeManager.selected() == cape));
        }

        // gallery results are already filtered by CapeGallery.search(filter)
        for (CapeGallery.Entry entry : CapeGallery.results()) {
            if (local.contains((entry.category() + "/" + entry.name()).toLowerCase(java.util.Locale.ROOT))) continue;
            groups.computeIfAbsent(entry.category(), g -> new ArrayList<>()).add(new Card(
                    entry.name(), entry.category(), () -> CapeGallery.thumb(entry), false,
                    () -> CapeImport.fromUrl(CapeGallery.imageUrl(entry), entry.name(),
                            entry.category(), entry.crop()),
                    () -> false));
        }

        int y = 0;
        for (Map.Entry<String, List<Card>> group : groups.entrySet()) {
            y += GROUP_H;
            List<Card> cards = group.getValue();
            for (int i = 0; i < cards.size(); i++) {
                slots.add(new Slot(cards.get(i), (i % cols) * (CARD_W + GAP), y + (i / cols) * (CARD_H + GAP)));
            }
            y += ((cards.size() + cols - 1) / cols) * (CARD_H + GAP);
        }
        contentHeight = y + 4;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - gridH)));
    }

    /** Adds the current custom cape to the shared gallery so others can wear it too. */
    private void publish() {
        status = "§7Publishing to the gallery...";
        com.teslamaps.features.capes.CapeSync.publishToGallery(line -> status = line);
    }

    private void deleteSelected() {
        CapeManager.Cape cape = CapeManager.selected();
        if (cape == null || !cape.custom()) {
            status = "§7Only your own capes can be deleted";
            return;
        }
        status = CapeManager.delete(cape) ? "§7Deleted " + cape.name : "§cCould not delete " + cape.name;
        int keep = scroll;
        rebuildWidgets();
        scroll = Math.max(0, Math.min(keep, maxScroll()));
    }

    private int barX() { return gridX + gridW + 2; }

    private int maxScroll() { return Math.max(0, contentHeight - gridH); }

    private int barHeight() { return Math.max(20, gridH * gridH / Math.max(1, contentHeight)); }

    /** Puts the handle under the cursor, so grabbing the middle of it does not jump. */
    private void scrollToBar(double my) {
        int barH = barHeight();
        int travel = gridH - barH;
        if (travel <= 0) return;
        double t = (my - gridY - barH / 2.0) / travel;
        scroll = (int) Math.round(Math.max(0, Math.min(1, t)) * maxScroll());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();

        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && field != null && field.isMouseOver(mx, my)) {
            field.setValue("");
            field.setFocused(true);
            return true;
        }

        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && mx >= wavinessX() && mx < wavinessX() + wavinessW()
                && my >= wavinessY() - 4 && my < wavinessY() + 10) {
            draggingWaviness = true;
            setDragging(true);
            setWaviness(mx);
            return true;
        }

        // the bar is 4 px wide but gets a 12 px grab zone, otherwise it is unhittable
        if (maxScroll() > 0 && mx >= barX() - 4 && mx <= barX() + 12
                && my >= gridY && my < gridY + gridH) {
            draggingBar = true;
            setDragging(true);
            scrollToBar(my);
            return true;
        }

        if (mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH) {
            for (Slot slot : slots) {
                int x = gridX + slot.x(), y = gridY + slot.y() - scroll;
                if (mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H) {
                    if (minecraft.hasShiftDown()) {
                        galleryDelete(slot.card());
                        return true;
                    }
                    slot.card().click().run();
                    status = "";
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Shift-click: remove the card's gallery entry (server rejects non-admins). */
    private void galleryDelete(Card card) {
        for (CapeGallery.Entry entry : CapeGallery.results()) {
            if (entry.category().equals(card.group()) && entry.name().equalsIgnoreCase(card.name())) {
                status = "§7Removing " + entry.name() + "...";
                com.teslamaps.features.capes.CapeSync.galleryDelete(entry.path(), line -> status = line);
                return;
            }
        }
        status = "§7" + card.name() + " is not in the gallery";
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBar) {
            scrollToBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingBar) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        // the grid is the only scrollable thing here, so the cursor does not have to be over it
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (sy * 24)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN && field != null && field.isFocused()) {
            submit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // an import or a fresh catalogue changes what the grid holds
        if (CapeImport.importedId != null) {
            CapeImport.importedId = null;
            layout();
        }
        if (CapeGallery.generation != shownGeneration) {
            shownGeneration = CapeGallery.generation;
            layout();
        }

        // mouseDragged does not reach us reliably, so the held bar is followed per frame instead
        if (draggingBar) {
            scrollToBar(mouseY);
            if (!minecraft.mouseHandler.isLeftPressed()) {
                draggingBar = false;
                setDragging(false);
            }
        }
        if (draggingWaviness) {
            setWaviness(mouseX);
            if (!minecraft.mouseHandler.isLeftPressed()) {
                draggingWaviness = false;
                setDragging(false);
            }
        }

        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);

        CapeManager.Cape selected = CapeManager.selected();
        boolean on = TeslaMapsConfig.get().capeEnabled && selected != null;

        ctx.text(font, "§f§lCapes", panelX + 12, panelY + 12, 0xFFFFFFFF);
        ctx.text(font, "§8worn capes are visible to every TeslaMaps user", panelX + 62, panelY + 13, 0xFF808080);

        drawPreview(ctx, selected, on);
        drawWaviness(ctx, mouseX, mouseY);
        drawGrid(ctx, mouseX, mouseY);
        drawFooter(ctx);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private int wavinessX() { return panelX + 12; }

    private int wavinessY() { return panelY + HEADER_H + (gridH - 62) + 41; }

    private int wavinessW() { return PREVIEW_W - 4; }

    private void setWaviness(double mx) {
        float t = (float) ((mx - wavinessX()) / wavinessW());
        TeslaMapsConfig.get().capeWaviness = Math.round(Math.max(0f, Math.min(1f, t)) * 20f) / 20f;
    }

    private void drawWaviness(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int x = wavinessX(), y = wavinessY(), w = wavinessW();
        float value = TeslaMapsConfig.get().capeWaviness;

        ctx.text(font, "§7Waviness", x, y - 10, 0xFFB0B0B0);
        String label = value <= 0f ? "off" : (int) (value * 100) + "%";
        ctx.text(font, "§f" + label, x + w - font.width(label), y - 10, 0xFFFFFFFF);

        boolean hot = draggingWaviness
                || (mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y + 8);
        ctx.fill(x, y, x + w, y + 6, 0xFF1C1C1E);
        if (value > 0f) ctx.fill(x, y, x + Math.max(1, (int) (w * value)), y + 6, 0xFF30D158);
        drawBorder(ctx, x, y, w, 6, hot ? 0xFF8E8E93 : 0xFF3A3A3C);
        int hx = x + (int) ((w - 3) * value);
        ctx.fill(hx, y - 2, hx + 3, y + 8, hot ? 0xFFFFFFFF : 0xFFB0B0B4);
    }

    private void drawPreview(GuiGraphicsExtractor ctx, CapeManager.Cape selected, boolean on) {
        int x = panelX + 12, y = panelY + HEADER_H;
        int w = PREVIEW_W - 4, h = gridH - 62;
        ctx.fill(x, y, x + w, y + h, 0xFF1C1C1E);
        drawBorder(ctx, x, y, w, h, 0xFF3A3A3C);

        Identifier texture = on ? selected.texture() : null;
        if (texture != null) {
            int ih = h - 16;
            int iw = Math.max(1, ih * 10 / 16);
            if (iw > w - 16) {
                iw = w - 16;
                ih = iw * 16 / 10;
            }
            int ix = x + (w - iw) / 2, iy = y + (h - ih) / 2;
            ctx.blit(texture, ix, iy, ix + iw, iy + ih, U0, U1, V0, V1);
        } else {
            ctx.centeredText(font, "§8no cape", x + w / 2, y + h / 2 - 4, 0xFF808080);
        }

        String name = on ? trim(selected.name, w - 4) : "Off";
        ctx.centeredText(font, "§f" + name, x + w / 2, y + h + 5, 0xFFFFFFFF);
        String detail = on
                ? selected.width + "x" + selected.height + (selected.width > 64 ? " §aHD" : "")
                        + (selected.animated ? " §d" + Math.max(1, selected.frames()) + " frames" : "")
                : "pick one below";
        ctx.centeredText(font, "§8" + detail, x + w / 2, y + h + 17, 0xFF808080);
    }

    private void drawGrid(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ctx.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);

        if (slots.isEmpty()) {
            ctx.text(font, CapeGallery.loading ? "§7Loading catalogue..." : "§8Nothing matches",
                    gridX, gridY + 6, 0xFF808080);
        }

        String group = null;
        for (Slot slot : slots) {
            int x = gridX + slot.x(), y = gridY + slot.y() - scroll;
            Card card = slot.card();

            if (!card.group().equals(group)) {
                group = card.group();
                ctx.text(font, "§7" + group, gridX, y - GROUP_H + 3, 0xFFB0B0B0);
            }
            if (y + CARD_H < gridY || y > gridY + gridH) continue;

            boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
            boolean active = card.active().getAsBoolean();

            int ix = x + (CARD_W - IMG_W) / 2;
            ctx.fill(ix, y, ix + IMG_W, y + IMG_H, 0xFF1C1C1E);
            Identifier texture = card.texture().get();
            if (texture != null) {
                if (card.frontOnly()) ctx.blit(texture, ix, y, ix + IMG_W, y + IMG_H, 0f, 1f, 0f, 1f);
                else ctx.blit(texture, ix, y, ix + IMG_W, y + IMG_H, U0, U1, V0, V1);
            } else {
                ctx.centeredText(font, card.name().equals("Off") ? "§8off" : "§8...",
                        ix + IMG_W / 2, y + IMG_H / 2 - 4, 0xFF808080);
            }
            drawBorder(ctx, ix, y, IMG_W, IMG_H,
                    active ? 0xFF30D158 : (hover ? 0xFF8E8E93 : 0xFF3A3A3C));

            ctx.centeredText(font, (active ? "§f" : "§7") + trim(card.name(), CARD_W - 2),
                    x + CARD_W / 2, y + IMG_H + 3, active ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        ctx.disableScissor();

        if (maxScroll() > 0) {
            int barH = barHeight();
            int barY = gridY + (gridH - barH) * scroll / maxScroll();
            boolean hot = draggingBar
                    || (mouseX >= barX() - 4 && mouseX <= barX() + 12 && mouseY >= gridY && mouseY < gridY + gridH);
            ctx.fill(barX(), gridY, barX() + 4, gridY + gridH, 0xFF232326);
            ctx.fill(barX(), barY, barX() + 4, barY + barH, hot ? 0xFFB0B0B4 : 0xFF6A6A6E);
        }
    }

    private void drawFooter(GuiGraphicsExtractor ctx) {
        int x = panelX + 12, y = panelY + panelH - 62, w = panelW - 24, h = 8;

        boolean importing = CapeImport.running || CapeImport.failed || !CapeImport.message.isEmpty();
        boolean busy = CapeGallery.loading;
        if (!importing && !busy) {
            String line = !status.isEmpty() ? status : CapeGallery.status;
            if (!line.isEmpty()) ctx.text(font, trim(line, w), x, y, 0xFFFFFFFF);
            return;
        }

        String message = importing ? CapeImport.message : CapeGallery.status;
        ctx.text(font, (CapeImport.failed ? "§c" : "§7") + trim(message, w - 4), x, y - 11, 0xFFFFFFFF);
        ctx.fill(x, y, x + w, y + h, 0xFF1C1C1E);
        float fraction = importing ? CapeImport.fraction : -1f;
        int color = CapeImport.failed ? 0xFFFF453A : 0xFF30D158;
        if (fraction < 0f) {
            // unknown size: a block sliding along instead of a lying progress bar
            int slice = w / 5;
            int offset = (int) ((System.currentTimeMillis() / 6) % (w + slice)) - slice;
            ctx.fill(x + Math.max(0, offset), y, x + Math.min(w, offset + slice), y + h, color);
        } else {
            ctx.fill(x, y, x + Math.max(1, (int) (w * Math.min(1f, fraction))), y + h, color);
        }
        drawBorder(ctx, x, y, w, h, 0xFF3A3A3C);
    }

    private String trim(String text, int max) {
        if (font.width(text) <= max) return text;
        StringBuilder cut = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(cut.toString() + c + "…") > max) break;
            cut.append(c);
        }
        return cut + "…";
    }

    private void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
