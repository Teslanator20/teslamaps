package com.teslamaps.profileviewer.screen;

import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Base class for profile viewer pages.
 */
public abstract class ProfileViewerPage {
    protected ProfileViewerScreen parent;
    protected SkyblockProfiles profiles;

    public void init(ProfileViewerScreen parent, SkyblockProfiles profiles) {
        this.parent = parent;
        this.profiles = profiles;
    }

    /**
     * Render the page content.
     * @param ctx Draw context
     * @param x Left edge of content area
     * @param y Top edge of content area
     * @param width Content area width
     * @param height Content area height
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param delta Partial tick
     */
    public abstract void render(DrawContext ctx, int x, int y, int width, int height,
                                int mouseX, int mouseY, float delta);

    /**
     * Get the tab name for this page.
     */
    public abstract String getTabName();

    /**
     * Get the tab icon for this page.
     */
    public ItemStack getTabIcon() {
        return new ItemStack(Items.PAPER);
    }

    /**
     * Called when the page is selected.
     */
    public void onSelected() {}

    /**
     * Called when mouse is clicked.
     * @return true if the click was handled
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Called when mouse is scrolled.
     * @return true if the scroll was handled
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    /**
     * Called when a key is pressed.
     * @return true if the key was handled
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /**
     * Get the current profile.
     */
    protected SkyblockProfile getProfile() {
        return profiles != null ? profiles.getSelectedProfile() : null;
    }

    // Utility methods for rendering

    /**
     * Draw a progress bar.
     */
    protected void drawProgressBar(DrawContext ctx, int x, int y, int width, int height,
                                   double progress, int bgColor, int fgColor) {
        // Background
        ctx.fill(x, y, x + width, y + height, bgColor);
        // Foreground (progress)
        int progressWidth = (int) (width * Math.min(1.0, Math.max(0.0, progress)));
        if (progressWidth > 0) {
            ctx.fill(x, y, x + progressWidth, y + height, fgColor);
        }
        // Border
        drawBorder(ctx, x, y, width, height, 0xFF000000);
    }

    /**
     * Draw a border around a rectangle.
     */
    protected void drawBorder(DrawContext ctx, int x, int y, int width, int height, int color) {
        ctx.fill(x, y, x + width, y + 1, color);                    // Top
        ctx.fill(x, y + height - 1, x + width, y + height, color);  // Bottom
        ctx.fill(x, y, x + 1, y + height, color);                    // Left
        ctx.fill(x + width - 1, y, x + width, y + height, color);    // Right
    }

    /**
     * Format a large number (e.g., 1.2M, 5.3K).
     */
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

    /**
     * Format coins with comma separators.
     */
    protected String formatCoins(double coins) {
        if (coins >= 1_000_000_000) {
            return String.format("%.2fB", coins / 1_000_000_000);
        } else if (coins >= 1_000_000) {
            return String.format("%.2fM", coins / 1_000_000);
        } else if (coins >= 1_000) {
            return String.format("%.1fK", coins / 1_000);
        } else {
            return String.format("%.0f", coins);
        }
    }
}
