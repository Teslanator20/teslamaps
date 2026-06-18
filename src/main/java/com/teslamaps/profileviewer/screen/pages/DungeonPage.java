package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DungeonPage extends ProfileViewerPage {
    @Override
    public String getTabName() {
        return "Dungeons";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.WITHER_SKELETON_SKULL);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        var textRenderer = Minecraft.getInstance().font;

        if (profiles == null || getProfile() == null) {
            ctx.text(textRenderer, "No profile data", x + 10, y + 10, 0xFFFFFF);
            return;
        }

        ctx.text(textRenderer, "Dungeon stats coming soon...", x + 10, y + 10, 0xAAAAAA);
    }
}
