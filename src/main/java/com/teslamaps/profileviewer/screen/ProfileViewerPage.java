/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
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

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
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
