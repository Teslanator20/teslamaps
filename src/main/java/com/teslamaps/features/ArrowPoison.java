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
    private static int timer, hudW = 40;
    private static String toxicText = "§ax0", twilightText = "§5x0";
    private static com.teslamaps.registry.HudEditElement hud;

    private ArrowPoison() {}

    public static void register() {
        hud = com.teslamaps.registry.HudEditElement.of("arrowPoisonHud", "Arrow Poison").section("Combat")
                .enabled(() -> TeslaMapsConfig.get().arrowPoisonHud)
                .size(() -> 22 + Minecraft.getInstance().font.width("x64"), () -> 36)
                .preview((g, mc, x, y, s) -> drawHud(g, mc, x, y, s, 22 + mc.font.width("x64"), "§ax64", "§5x64"));
        feature = Feature.of("ArrowPoison").section("Combat")
                .when(() -> TeslaMapsConfig.get().arrowPoisonIndicator || TeslaMapsConfig.get().arrowPoisonHud)
                .tick(ArrowPoison::tick)
                .hudHiddenInInventory("arrowpoisonhud", (g, d) -> {
                    if (!TeslaMapsConfig.get().arrowPoisonHud || Minecraft.getInstance().options.hideGui) return;
                    drawHud(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale(), hudW, toxicText, twilightText);
                })
                .config("Combat", b -> {
                    b.label("Bow");
                    b.toggle("Arrow Poison On Bow", () -> TeslaMapsConfig.get().arrowPoisonIndicator,
                                    v -> TeslaMapsConfig.get().arrowPoisonIndicator = v)
                            .desc("Hotbar bows show the first Toxic or Twilight Arrow Poison in your inventory and how many are left.");
                    b.toggle("Arrow Poison HUD", () -> TeslaMapsConfig.get().arrowPoisonHud,
                                    v -> TeslaMapsConfig.get().arrowPoisonHud = v)
                            .desc("Movable HUD with your Twilight and Toxic Arrow Poison counts. Position in /tmap gui.");
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
        toxicText = "§ax" + toxic;
        twilightText = "§5x" + twilight;
        var font = Minecraft.getInstance().font;
        hudW = 22 + Math.max(font.width(toxicText), font.width(twilightText));
        int count = first == 1 ? toxic : first == 2 ? twilight : 0;
        icon = first == 1 ? toxicIcon : first == 2 ? twilightIcon : ItemStack.EMPTY;
        countText = count > 0 ? String.valueOf(count) : "";
    }

    public static void drawHotbar(GuiGraphicsExtractor g, int x, int y, ItemStack stack) {
        if (icon.isEmpty() || feature == null || !stack.is(Items.BOW) || !TeslaMapsConfig.get().arrowPoisonIndicator || !feature.active()) return;
        Perf.begin("ArrowPoison");
        g.pose().pushMatrix();
        g.pose().translate(x + 9f, y - 3f);
        g.pose().scale(0.55f, 0.55f);
        g.item(icon, 0, 0);
        g.pose().popMatrix();
        if (!countText.isEmpty()) g.itemDecorations(Minecraft.getInstance().font, icon, x, y - 2, countText);
        Perf.end();
    }

    private static void drawHud(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, int w, String toxic, String twilight) {
        if (toxicIcon == null) {
            toxicIcon = new ItemStack(Items.LIME_DYE);
            twilightIcon = new ItemStack(Items.PURPLE_DYE);
        }
        Perf.begin("ArrowPoison.hud");
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        com.teslamaps.render.gui.HudStyle.panel(g, 0, 0, w, 36);
        g.item(twilightIcon, 2, 2);
        g.text(mc.font, twilight, 20, 6, 0xFFFFFFFF, true);
        g.item(toxicIcon, 2, 18);
        g.text(mc.font, toxic, 20, 22, 0xFFFFFFFF, true);
        g.pose().popMatrix();
        Perf.end();
    }
}
