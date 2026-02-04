package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class PetsPage extends ProfileViewerPage {
    @Override
    public String getTabName() {
        return "Pets";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.BONE);
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        var textRenderer = MinecraftClient.getInstance().textRenderer;

        if (profiles == null || getProfile() == null) {
            ctx.drawTextWithShadow(textRenderer, "No profile data", x + 10, y + 10, 0xFFFFFF);
            return;
        }

        ctx.drawTextWithShadow(textRenderer, "Pet stats coming soon...", x + 10, y + 10, 0xAAAAAA);
    }
}
