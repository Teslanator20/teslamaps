package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.PetData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        SkyblockProfile profile = getProfile();
        if (profile == null || profile.getPets().isEmpty()) {
            ctx.text(tr, "No pets", x + 10, y + 10, NeuStyle.TEXT_GRAY);
            return;
        }

        int left = x - 8;
        int top = y - 8;

        List<PetData> pets = new ArrayList<>(profile.getPets());
        pets.sort(Comparator
                .comparing(PetData::isActive).reversed()
                .thenComparing(PetData::getRarityOrdinal, Comparator.reverseOrder())
                .thenComparing(PetData::getLevel, Comparator.reverseOrder()));

        String title = "Pets (" + pets.size() + ")";
        ctx.text(tr, title, left + NeuStyle.PANEL_WIDTH / 2 - tr.width(title) / 2, top + 5, NeuStyle.TEXT_YELLOW);

        // three columns of rows, same rhythm as the skill grid
        int columnWidth = 135;
        int perColumn = 8;

        for (int i = 0; i < pets.size(); i++) {
            PetData pet = pets.get(i);
            int column = i / perColumn;
            int row = i % perColumn;
            int px = left + 14 + column * columnWidth;
            int py = top + 22 + row * 21;

            if (px + columnWidth > left + NeuStyle.PANEL_WIDTH) break;

            String name = pet.getDisplayName();
            ctx.text(tr, name, px, py, pet.getRarityColor() | 0xFF000000);

            String level = "Lv " + pet.getLevel();
            ctx.text(tr, level, px + columnWidth - 26 - tr.width(level), py, NeuStyle.TEXT);

            if (pet.isActive()) {
                ctx.text(tr, "✓", px + columnWidth - 20, py, NeuStyle.TEXT_GREEN);
            }

            NeuStyle.bar(ctx, px, py + 10, columnWidth - 24, pet.getProgress(), pet.getRarityColor() | 0xFF000000);

            if (mouseX > px && mouseX < px + columnWidth - 24 && mouseY > py - 2 && mouseY < py + 15) {
                List<String> lines = new ArrayList<>();
                lines.add(name);
                lines.add("Level: " + pet.getLevel());
                lines.add("XP: " + formatXp(pet.getExp()));
                if (pet.getHeldItem() != null) lines.add("Held: " + prettify(pet.getHeldItem()));
                if (pet.getSkin() != null) lines.add("Skin: " + prettify(pet.getSkin()));
                if (pet.isActive()) lines.add("Active");
                tooltip = lines.toArray(new String[0]);
                tooltipX = mouseX;
                tooltipY = mouseY;
            }
        }

        if (tooltip != null) {
            drawTooltip(ctx, tr);
            tooltip = null;
        }
    }

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    private static String prettify(String id) {
        String out = id.replace("PET_ITEM_", "").replace("PET_SKIN_", "").replace('_', ' ').toLowerCase();
        return out.substring(0, 1).toUpperCase() + out.substring(1);
    }

    private String[] tooltip;
    private int tooltipX;
    private int tooltipY;

    private void drawTooltip(GuiGraphicsExtractor ctx, Font tr) {
        int w = 0;
        for (String line : tooltip) w = Math.max(w, tr.width(line));
        int h = tooltip.length * 10 + 4;
        int tx = Math.min(tooltipX + 8, NeuStyle.PANEL_WIDTH - w - 8);
        int ty = Math.max(2, tooltipY - h);
        NeuStyle.window(ctx, tx - 3, ty - 2, w + 8, h);
        for (int i = 0; i < tooltip.length; i++) {
            ctx.text(tr, tooltip[i], tx, ty + 2 + i * 10, i == 0 ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
        }
    }
}
