package com.teslamaps.profileviewer.screen;

import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public abstract class ProfileViewerPage {
    protected ProfileViewerScreen parent;
    protected SkyblockProfiles profiles;

    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        this.parent = parent;
        this.profiles = profiles;
    }

    public abstract void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                                int mouseX, int mouseY, float delta);

    public abstract String getTabName();

    public ItemStack getTabIcon() {
        return new ItemStack(Items.PAPER);
    }

    public void onSelected() {}

    /** A tab that makes no sense for the selected profile hides itself. */
    public boolean isAvailable(SkyblockProfiles profiles) {
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        return false;
    }

    public void mouseReleased() {
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isFixedLayout()) return false;
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - amount * 12));
        return true;
    }

    /** Pages laid out on NEU's grid stay put, the long list pages scroll. */
    public boolean isFixedLayout() {
        return false;
    }

    public int getScroll() {
        return scroll;
    }

    public void setContentHeight(int height) {
        this.maxScroll = Math.max(0, height - com.teslamaps.profileviewer.screen.NeuStyle.PANEL_HEIGHT + 20);
    }

    protected int scroll = 0;
    private int maxScroll = 500;

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr) {
        return false;
    }

    /** Hypixel mixes strings into numeric maps, so never trust getAsInt on foreign data. */
    protected static int asInt(com.google.gson.JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return 0;
        try {
            return element.getAsJsonPrimitive().isNumber() ? element.getAsInt() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected static long asLong(com.google.gson.JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return 0;
        try {
            return element.getAsJsonPrimitive().isNumber() ? element.getAsLong() : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Entity renders ignore the gui pose, so pages need the window offset and scale. */
    protected int screenX(int panelX) {
        return parent == null ? panelX : parent.toScreenX(panelX);
    }

    protected int screenY(int panelY) {
        return parent == null ? panelY : parent.toScreenY(panelY);
    }

    /**
     * Same as below, but the page is built through a callback so a page that has to be shrunk can
     * be laid out on a wider canvas first. Shrinking narrows the content as well, and without this
     * the window ends up with a third of its width empty on both sides.
     */
    protected void drawTree(GuiGraphicsExtractor ctx, java.util.function.IntFunction<com.teslamaps.profileviewer.ui.Ui> builder,
                            int width, int x, int y, int mouseX, int mouseY) {
        int available = com.teslamaps.profileviewer.screen.NeuStyle.PANEL_HEIGHT - y - 6;
        com.teslamaps.profileviewer.ui.Ui page = builder.apply(width);

        if (page.height() > available) {
            float fit = Math.max(MIN_FIT, available / (float) page.height());
            com.teslamaps.profileviewer.ui.Ui wider = builder.apply(Math.round(width / fit));
            // a wider canvas usually means more columns and less height; keep it unless it backfired
            if (wider.height() <= page.height()) page = wider;
        }

        drawTree(ctx, page, x, y, mouseX, mouseY);
    }

    /**
     * Renders a laid out component tree and hands its hover result to the screen. A tree taller
     * than the window is shrunk to fit instead of scrolling - nobody wants to scroll a profile.
     */
    protected void drawTree(GuiGraphicsExtractor ctx, com.teslamaps.profileviewer.ui.Ui root,
                            int x, int y, int mouseX, int mouseY) {
        // symmetric margins by default; pages with a rail pass their real content width
        drawTree(ctx, root, x, y, com.teslamaps.profileviewer.screen.NeuStyle.PANEL_WIDTH - 2 * x, mouseX, mouseY);
    }

    protected void drawTree(GuiGraphicsExtractor ctx, com.teslamaps.profileviewer.ui.Ui root,
                            int x, int y, int free, int mouseX, int mouseY) {
        com.teslamaps.profileviewer.ui.Ui.resetHover();

        int available = com.teslamaps.profileviewer.screen.NeuStyle.PANEL_HEIGHT - y - 6;

        float fitHeight = root.height() > available && root.height() > 0
                ? available / (float) root.height() : 1f;
        // a page laid out on a wider canvas has to come back down to the window as well
        float fitWidth = root.width() > free && root.width() > 0 ? free / (float) root.width() : 1f;
        float fit = Math.max(MIN_FIT, Math.min(fitHeight, fitWidth));

        if (fit < 1f) {
            // shrinking narrows the content too, so keep it centred instead of hugging the left edge
            int offset = Math.max(0, (int) ((free - root.width() * fit) / 2));
            ctx.pose().pushMatrix();
            ctx.pose().translate(x + offset, y);
            ctx.pose().scale(fit, fit);
            root.render(ctx, 0, 0, (int) ((mouseX - x - offset) / fit), (int) ((mouseY - y) / fit));
            ctx.pose().popMatrix();
        } else {
            root.render(ctx, x, y, mouseX, mouseY);
        }

        hoverStack(com.teslamaps.profileviewer.ui.Ui.hoveredStack);
        if (com.teslamaps.profileviewer.ui.Ui.hoveredLines != null && parent != null) {
            parent.setHoveredLines(com.teslamaps.profileviewer.ui.Ui.hoveredLines);
        }
        com.teslamaps.profileviewer.ui.Ui.resetHover();
        // below this the text stops being readable, then the rest scrolls after all
        setContentHeight(fit > MIN_FIT ? 0 : (int) (root.height() * fit) + 24);
    }

    private static final float MIN_FIT = 0.55f;

    /** Vanilla tooltip for an item, drawn by the screen so other mods can hook it. */
    protected void hoverStack(net.minecraft.world.item.ItemStack stack) {
        if (parent != null && stack != null && !stack.isEmpty()) parent.setHoveredStack(stack);
    }

    protected SkyblockProfile getProfile() {
        return profiles != null ? profiles.getSelectedProfile() : null;
    }

    protected void drawProgressBar(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                                   double progress, int bgColor, int fgColor) {
        ctx.fill(x, y, x + width, y + height, bgColor);
        int progressWidth = (int) (width * Math.min(1.0, Math.max(0.0, progress)));
        if (progressWidth > 0) {
            ctx.fill(x, y, x + progressWidth, y + height, fgColor);
        }
        drawBorder(ctx, x, y, width, height, 0xFF000000);
    }

    protected void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int width, int height, int color) {
        ctx.fill(x, y, x + width, y + 1, color);                    // Top
        ctx.fill(x, y + height - 1, x + width, y + height, color);  // Bottom
        ctx.fill(x, y, x + 1, y + height, color);                    // Left
        ctx.fill(x + width - 1, y, x + width, y + height, color);    // Right
    }

    protected String formatNumber(double num) {
        if (num >= 1_000_000_000) {
            return String.format("%.1fB", num / 1_000_000_000);
        } else if (num >= 1_000_000) {
            return String.format("%.1fM", num / 1_000_000);
        } else if (num >= 1_000) {
            return String.format("%.1fK", num / 1_000);
        } else {
            return String.format("%.0f", num);
        }
    }

    protected String formatXp(double value) {
        if (value >= 1_000_000_000) return trim(value / 1_000_000_000) + "B";
        if (value >= 1_000_000) return trim(value / 1_000_000) + "M";
        if (value >= 1_000) return trim(value / 1_000) + "K";
        return String.format("%.0f", value);
    }

    /** Two decimals, without trailing zeroes - 1.51B, not 1.5124B. */
    private static String trim(double value) {
        String text = String.format("%.2f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".") || text.endsWith(",")) text = text.substring(0, text.length() - 1);
        return text;
    }

    protected String formatCoins(double coins) {
        return formatXp(coins);
    }
}
