package com.teslamaps.features;

// Layout after Skyblocker's SkyblockCraftingTableScreen, drawn over the chest instead of swapping the menu
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class FancyCraftingTable {

    private static final Identifier CHEST = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final Identifier TABLE = Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int RESULT = 23;

    private static Screen lastScreen;
    private static boolean lastActive;

    private FancyCraftingTable() {}

    public static void register() {
        Feature.of("FancyCraftingTable").section("Inventory")
                .config("Inventory", b -> {
                    b.label("Crafting");
                    b.toggle("Fancy Crafting Table", () -> TeslaMapsConfig.get().fancyCraftingTable,
                                    v -> TeslaMapsConfig.get().fancyCraftingTable = v)
                            .desc("Craft Item menu: hides the filler panes and draws a crafting grid with a result slot.");
                });
    }

    public static boolean active(Object screen) {
        if (screen != lastScreen) {
            lastScreen = screen instanceof Screen s ? s : null;
            lastActive = screen instanceof ContainerScreen cs && cs.getMenu().getRowCount() == 6
                    && StringUtil.stripTrim(cs.getTitle().getString()).equals("Craft Item");
        }
        if (!lastActive) return false;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.fancyCraftingTable && c.section("Inventory");
    }

    private static boolean grid(int i) {
        int col = i % 9, row = i / 9;
        return col >= 1 && col <= 3 && row >= 1 && row <= 3;
    }

    private static boolean filler(Slot slot) {
        ItemStack st = slot.getItem();
        if (slot.index == RESULT && st.is(Items.BARRIER)) return true;
        return st.is(Items.GRAY_STAINED_GLASS_PANE) && ItemUtil.skyblockId(st).isEmpty();
    }

    /** True = skip vanilla drawing of this slot. */
    public static boolean hideSlot(Object screen, Slot slot) {
        if (!active(screen) || slot.container instanceof Inventory) return false;
        return filler(slot);
    }

    public static void drawBackground(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> cs) {
        if (!active(cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        ctx.fill(left + 7, top + 17, left + 169, top + 17 + 6 * 18, 0xFFC6C6C6);
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            int i = slot.index;
            if (grid(i) || i == RESULT || filler(slot)) continue;
            ctx.blit(RenderPipelines.GUI_TEXTURED, CHEST, left + slot.x - 1, top + slot.y - 1, 7, 17, 18, 18, 256, 256);
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, TABLE, left + 25, top + 35, 29, 16, 54, 54, 256, 256);
        ctx.blit(RenderPipelines.GUI_TEXTURED, TABLE, left + 93, top + 49, 119, 30, 26, 26, 256, 256);
        ctx.text(Minecraft.getInstance().font, "→", left + 82, top + 58, 0xFF404040, false);
    }
}
