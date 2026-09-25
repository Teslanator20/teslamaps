package com.teslamaps.features;

// Ported from GanKura's ArrowPoisonScanner.java + HotbarOverlayRenderer.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.debug.Perf;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Small dye icon + remaining count of Toxic/Twilight Arrow Poison on every bow in the hotbar. */
public final class ArrowPoison {

    private static Feature feature;
    private static ItemStack icon = ItemStack.EMPTY;
    private static ItemStack toxicIcon, twilightIcon;
    private static String countText = "";
    private static int timer;

    private ArrowPoison() {}

    public static void register() {
        feature = Feature.of("ArrowPoison").section("Combat").when(() -> TeslaMapsConfig.get().arrowPoisonIndicator)
                .tick(ArrowPoison::tick)
                .config("Combat", b -> {
                    b.label("Bow");
                    b.toggle("Arrow Poison On Bow", () -> TeslaMapsConfig.get().arrowPoisonIndicator,
                                    v -> TeslaMapsConfig.get().arrowPoisonIndicator = v)
                            .desc("Hotbar bows show the first Toxic or Twilight Arrow Poison in your inventory and how many are left.");
                });
    }

    private static void tick() {
        if (timer-- > 0) return;
        timer = 10;
        if (toxicIcon == null) {
            toxicIcon = new ItemStack(Items.LIME_DYE);
            twilightIcon = new ItemStack(Items.PURPLE_DYE);
        }
        Inventory inv = Minecraft.getInstance().player.getInventory();
        int first = 0, toxic = 0, twilight = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String name = com.teslamaps.utils.StringUtil.strip(st.getHoverName().getString());
            if (name.contains("Toxic Arrow Poison")) {
                if (first == 0) first = 1;
                toxic += st.getCount();
            } else if (name.contains("Twilight Arrow Poison")) {
                if (first == 0) first = 2;
                twilight += st.getCount();
            }
        }
        int count = first == 1 ? toxic : first == 2 ? twilight : 0;
        icon = first == 1 ? toxicIcon : first == 2 ? twilightIcon : ItemStack.EMPTY;
        countText = count > 0 ? String.valueOf(count) : "";
    }

    public static void drawHotbar(GuiGraphicsExtractor g, int x, int y, ItemStack stack) {
        if (icon.isEmpty() || feature == null || !stack.is(Items.BOW) || !feature.active()) return;
        Perf.begin("ArrowPoison");
        g.pose().pushMatrix();
        g.pose().translate(x + 9f, y - 3f);
        g.pose().scale(0.55f, 0.55f);
        g.item(icon, 0, 0);
        g.pose().popMatrix();
        if (!countText.isEmpty()) g.itemDecorations(Minecraft.getInstance().font, icon, x, y - 2, countText);
        Perf.end();
    }
}
