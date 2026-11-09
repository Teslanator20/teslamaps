package com.teslamaps.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.features.ScreenshotManager;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Browses the screenshots folder: thumbnails, a preview, copy, delete, open folder. */
public class ScreenshotScreen extends Screen {

    // The design grid. What actually gets used is whatever fits the viewport, see layout():
    // at gui scale 4 on 1080p the screen is only 480x270 gui units, and a fixed 836x518 panel
    // pushed the button row far below the bottom edge where it could never be clicked.
    private static final int MAX_COLS = 4, MAX_ROWS = 3;
    // 1.5x the old tile so the previews are actually readable
    private static final int BASE_THUMB_W = 192, BASE_THUMB_H = 108, GAP = 12;
    private static final int MARGIN = 10;      // smallest gap we keep to the window edge
    private static final int HEADER_H = 52;    // title + subtitle above the grid
    private static final int FOOTER_H = 52;    // status line + button row below it
    private static final int BTN_H = 22, BTN_MIN_W = 54;
    private static final int TEXTURE_CACHE = 40;

    private record Thumb(Identifier id, int width, int height) {}

    // keeps the GPU side bounded, the folder can hold hundreds of shots
    private static final Map<String, Thumb> TEXTURES = new LinkedHashMap<>();
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private final Screen parent;
    private List<File> files = new ArrayList<>();
    private int scrollRow = 0;
    private int selected = -1;
    private boolean wasMouseDown = false;
    private String status = "";
    private String armedAction = null;   // "delete" / "share" waiting for its confirm click
    private String armedFile = null;
    private long armedAt = 0;

    private int gridX, gridY, gridW, panelX, panelY, panelW, panelH;
    /** Grid actually shown, shrunk from MAX_COLS/MAX_ROWS to whatever the viewport allows. */
    private int cols = MAX_COLS, rows = MAX_ROWS, thumbW = BASE_THUMB_W, thumbH = BASE_THUMB_H;
    private int btnRows = 1;

    private File preselect;

    public ScreenshotScreen(Screen parent) {
        super(Component.literal("Screenshots"));
        this.parent = parent;
    }

    /** Opens with this screenshot selected. */
    public ScreenshotScreen(Screen parent, File select) {
        this(parent);
        this.preselect = select;
    }

    private boolean editor() {
        return com.teslamaps.config.TeslaMapsConfig.get().screenshotEditor;
    }

    @Override
    protected void init() {
        files = ScreenshotManager.list();
        if (preselect != null) {
            int at = files.indexOf(preselect);
            if (at < 0) for (int i = 0; i < files.size(); i++) if (files.get(i).getName().equals(preselect.getName())) at = i;
            if (at >= 0) selected = at;
            preselect = null;
        }
        if (selected >= files.size()) selected = files.isEmpty() ? -1 : 0;
        if (selected < 0 && !files.isEmpty()) selected = 0;

        layout();

        // Buttons are placed from the panel rectangle, which is now guaranteed to sit inside the
        // window, so they stay reachable at every gui scale. Two rows of two when it gets narrow.
        int inner = panelW - 32;
        int count = editor() ? 5 : 4;
        int perRow = btnRows == 1 ? count : (count + 1) / 2;
        int bw = Math.max(BTN_MIN_W, (inner - (perRow - 1) * 12) / perRow);
        int firstRowY = panelY + panelH - 8 - btnRows * (BTN_H + 4) + 4;

        addButton(0, perRow, bw, firstRowY, "§bCopy Picture", () -> {
            File f = current();
            if (f == null) {
                status = "§cCopy failed";
                return;
            }
            status = "§7Copying…";
            ScreenshotManager.copyAsync(f, err -> status = err == null ? "§aCopied " + f.getName() : "§cCopy failed: " + err);
        });
        addButton(1, perRow, bw, firstRowY, "§dCopy Ingame", () -> {
            if (confirm("share", "§eClick §dCopy Ingame§e again to upload ")) share();
        });
        addButton(2, perRow, bw, firstRowY, "§cDelete", () -> {
            File f = current();
            if (f == null || !confirm("delete", "§eClick §cDelete§e again to delete ")) return;
            if (ScreenshotManager.delete(f)) {
                TEXTURES.remove(f.getName());
                status = "§7Deleted " + f.getName();
                rebuildWidgets();
            } else {
                status = "§cDelete failed";
            }
        });
        addButton(3, perRow, bw, firstRowY, "§eFolder", ScreenshotManager::openFolder);
        if (editor()) addButton(4, perRow, bw, firstRowY, "§aEdit", () -> {
            File f = current();
            if (f != null) minecraft.gui.setScreen(new ScreenshotEditorScreen(this, f));
        });

        ScreenFit.clamp(this); // keep every widget reachable at any gui scale

    }

    private void addButton(int index, int perRow, int bw, int firstRowY, String label, Runnable action) {
        int col = index % perRow, row = index / perRow;
        addRenderableWidget(new TeslaButton(panelX + 16 + col * (bw + 12),
                firstRowY + row * (BTN_H + 4), bw, BTN_H, label, action));
    }

    /**
     * Fits the panel into the current viewport instead of assuming the design size. Drops columns
     * and rows first, then shrinks the tiles, so even a tiny gui-scaled screen keeps a usable grid
     * and, more importantly, keeps the button row on screen.
     */
    private void layout() {
        int availW = Math.max(120, width - MARGIN * 2);
        int availH = Math.max(120, height - MARGIN * 2);

        thumbW = BASE_THUMB_W;
        thumbH = BASE_THUMB_H;

        cols = Math.clamp((availW - 32 + GAP) / (BASE_THUMB_W + GAP), 1, MAX_COLS);
        // a single column that still does not fit means the tile itself has to give
        int widest = availW - 32;
        if (cols == 1 && BASE_THUMB_W > widest) {
            thumbW = Math.max(48, widest);
            thumbH = Math.max(27, BASE_THUMB_H * thumbW / BASE_THUMB_W);
        }

        int tileH = thumbH + 30;
        int count = editor() ? 5 : 4;
        btnRows = availW < count * BTN_MIN_W + (count - 1) * 12 + 32 ? 2 : 1;
        int chrome = HEADER_H + FOOTER_H + (btnRows - 1) * (BTN_H + 4);
        rows = Math.clamp((availH - chrome) / tileH, 1, MAX_ROWS);
        if (rows == 1 && tileH > availH - chrome) {
            // extreme case: shrink the tile until one row plus the chrome fits
            thumbH = Math.max(27, availH - chrome - 30);
            thumbW = Math.max(48, BASE_THUMB_W * thumbH / BASE_THUMB_H);
            tileH = thumbH + 30;
        }

        gridW = cols * thumbW + (cols - 1) * GAP;
        panelW = Math.min(gridW + 32, availW);
        panelH = Math.min(rows * tileH + chrome, availH);
        panelX = Math.max(MARGIN, (width - panelW) / 2);
        panelY = Math.max(MARGIN, (height - panelH) / 2);
        gridX = panelX + 16;
        gridY = panelY + HEADER_H;
    }

    /** First click arms the action for the current file, a second one within 4s runs it. */
    private boolean confirm(String action, String prompt) {
        File f = current();
        if (f == null) return false;
        long now = System.currentTimeMillis();
        if (action.equals(armedAction) && f.getName().equals(armedFile) && now - armedAt < 4000) {
            armedAction = null;
            return true;
        }
        armedAction = action;
        armedFile = f.getName();
        armedAt = now;
        status = prompt + f.getName();
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int k = event.key();
        if (!files.isEmpty()) {
            int step = k == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT ? -1 : k == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT ? 1
                    : k == com.mojang.blaze3d.platform.InputConstants.KEY_UP ? -cols : k == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN ? cols : 0;
            if (step != 0) {
                select(Math.clamp(Math.max(0, selected) + step, 0, files.size() - 1));
                return true;
            }
            if ((k == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || k == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) && current() != null) {
                openFullscreen(current(), tileRect(selected), 0);
                return true;
            }
        }
        if (k == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) { onClose(); return true; }
        return super.keyPressed(event);
    }

    private void select(int index) {
        selected = index;
        int row = index / cols;
        if (row < scrollRow) scrollRow = row;
        if (row >= scrollRow + rows) scrollRow = row - rows + 1;
    }

    /** Where a tile sits on screen, or null when scrolled out. */
    private int[] tileRect(int index) {
        int i = index - scrollRow * cols;
        if (i < 0 || i >= cols * rows) return null;
        return new int[]{gridX + (i % cols) * (thumbW + GAP), gridY + (i / cols) * (thumbH + 30), thumbW, thumbH};
    }

    private void browse(int dir) {
        if (files.isEmpty()) return;
        int next = Math.clamp(selected + dir, 0, files.size() - 1);
        if (next == selected) return;
        select(next);
        openFullscreen(files.get(next), null, dir);
    }

    @Override
    public void onClose() {
        if (parent != null) minecraft.gui.setScreen(parent);
        else super.onClose();
    }

    /** Uploads and puts the chat token on the clipboard, no extra dialog in between. */
    private void share() {
        File file = current();
        if (file == null) return;
        status = "§7Uploading " + file.getName() + "...";
        com.teslamaps.features.ImageShare.upload(file,
                url -> {
                    String token = com.teslamaps.features.ImageShare.toToken(url);
                    if (token == null) {
                        status = "§cCannot build a token for " + url;
                        return;
                    }
                    minecraft.keyboardHandler.setClipboard(
                            com.teslamaps.features.ImageShare.TAG + " " + token);
                    status = "§aCopied - paste it into any chat";
                },
                error -> status = "§cUpload failed: " + error);
    }

    private void openFullscreen(File file, int[] from, int slideDir) {
        Thumb thumb = thumb(file);
        if (thumb == null) {
            status = "§cCannot preview " + file.getName();
            return;
        }
        minecraft.gui.setScreen(ImageViewerScreen.forTexture(this, file.getName(),
                thumb.id(), thumb.width(), thumb.height()).gallery(this::browse, from, slideDir));
    }

    private File current() {
        return selected >= 0 && selected < files.size() ? files.get(selected) : null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int maxRow = Math.max(0, (files.size() + cols - 1) / cols - rows);
        scrollRow = Math.max(0, Math.min(maxRow, scrollRow - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean down = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) == com.mojang.blaze3d.platform.InputConstants.PRESS;
        boolean clicked = down && !wasMouseDown;

        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);

        ctx.text(this.font, "§f§lScreenshots §7(" + files.size() + ")", panelX + 16, panelY + 16, 0xFFFFFFFF);
        File cur = current();
        String subtitle = cur == null ? "§7Folder is empty" : "§7click again to enlarge §8· §7" + cur.getName() + " §8· " + DATE.format(new Date(cur.lastModified()));
        ctx.text(this.font, subtitle, panelX + 16, panelY + 32, 0xFFB0B0B0);

        int first = scrollRow * cols;
        for (int i = 0; i < cols * rows; i++) {
            int index = first + i;
            if (index >= files.size()) break;
            int col = i % cols, row = i / cols;
            int x = gridX + col * (thumbW + GAP);
            int y = gridY + row * (thumbH + 30);

            boolean hover = mouseX >= x && mouseX < x + thumbW && mouseY >= y && mouseY < y + thumbH;
            if (clicked && hover) {
                // first click picks, a second one on the same tile blows it up
                if (selected == index) openFullscreen(files.get(index), new int[]{x, y, thumbW, thumbH}, 0);
                else selected = index;
            }

            File file = files.get(index);
            ctx.fill(x, y, x + thumbW, y + thumbH, 0xFF1C1C1E);
            Thumb thumb = thumb(file);
            if (thumb != null) {
                // letterbox so a 16:10 or ultrawide shot keeps its shape
                float scale = Math.min((float) thumbW / thumb.width(), (float) thumbH / thumb.height());
                int w = Math.max(1, (int) (thumb.width() * scale));
                int h = Math.max(1, (int) (thumb.height() * scale));
                int ix = x + (thumbW - w) / 2, iy = y + (thumbH - h) / 2;
                // blit takes corners, not a size - x0, y0, x1, y1
                ctx.blit(thumb.id(), ix, iy, ix + w, iy + h, 0f, 1f, 0f, 1f);
            } else {
                ctx.text(this.font, "§8...", x + thumbW / 2 - 6, y + thumbH / 2 - 4, 0xFF808080);
            }

            int border = index == selected ? 0xFF30D158 : (hover ? 0xFF8E8E93 : 0xFF3A3A3C);
            drawBorder(ctx, x, y, thumbW, thumbH, border);

            String name = file.getName().replace(".png", "");
            ctx.text(this.font, this.font.width(name) > thumbW ? name.substring(0, 22) + "…" : name,
                    x, y + thumbH + 5, index == selected ? 0xFFFFFFFF : 0xFFAAAAAA);
            ctx.text(this.font, "§8" + (file.length() / 1024) + " KB", x, y + thumbH + 16, 0xFF808080);
        }

        if (!status.isEmpty()) {
            ctx.text(this.font, status, panelX + 16, panelY + panelH - 46, 0xFFFFFFFF);
        }
        int totalRows = (files.size() + cols - 1) / cols;
        if (totalRows > rows) {
            ctx.text(this.font, "§8row " + (scrollRow + 1) + "/" + (totalRows - rows + 1) + " · scroll",
                    panelX + panelW - 110, panelY + panelH - 46, 0xFF808080);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
        wasMouseDown = down;
    }

    /** Drops a cached thumbnail, e.g. after the file was overwritten. */
    public static void forget(String name) {
        Thumb gone = TEXTURES.remove(name);
        if (gone != null) Minecraft.getInstance().getTextureManager().release(gone.id());
    }

    private Thumb thumb(File file) {
        Thumb known = TEXTURES.get(file.getName());
        if (known != null) return known;

        try (FileInputStream in = new FileInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("teslamaps",
                    "screenshot/" + file.getName().toLowerCase().replaceAll("[^a-z0-9_.-]", "_"));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(file::getName, image));
            Thumb thumb = new Thumb(id, image.getWidth(), image.getHeight());
            TEXTURES.put(file.getName(), thumb);

            while (TEXTURES.size() > TEXTURE_CACHE) {
                String oldest = TEXTURES.keySet().iterator().next();
                Thumb gone = TEXTURES.remove(oldest);
                if (gone != null) Minecraft.getInstance().getTextureManager().release(gone.id());
            }
            return thumb;
        } catch (Throwable t) {
            TEXTURES.put(file.getName(), null);
            return null;
        }
    }

    private void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
