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

    protected String formatXp(double xp) {
        if (xp >= 1_000_000_000) return String.format("%.2fB", xp / 1_000_000_000);
        if (xp >= 1_000_000) return String.format("%.1fM", xp / 1_000_000);
        if (xp >= 1_000) return String.format("%.1fK", xp / 1_000);
        return String.format("%.0f", xp);
    }

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
