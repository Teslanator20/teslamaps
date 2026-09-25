package com.teslamaps.features;

// Based on Skysoft's InventoryHud.kt / FloydAddons' FloydInventoryHud.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class InventoryHud {

    private static final int SLOT = 18;
    private static final int PAD = 3;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static HudEditElement hud;

    private InventoryHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("inventoryHud", "Inventory HUD").section("Inventory")
                .enabled(() -> cfg().inventoryHud)
                .size(InventoryHud::width, InventoryHud::height)
                .scaleRange(0.5f, 3.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s));
        Feature.of("InventoryHud").section("Inventory").when(() -> cfg().inventoryHud)
                .hudHiddenInInventory("inventoryhud", InventoryHud::render)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Inventory HUD");
                    b.toggle("Inventory HUD", () -> c.inventoryHud, v -> c.inventoryHud = v)
                            .desc("Your 27 main inventory slots as a 9x3 panel on the HUD. Position in /tmap gui.")
                            .tags("inventory", "hud");
                    b.toggle("  Show Armor", () -> c.inventoryHudArmor, v -> c.inventoryHudArmor = v)
                            .desc("Worn armor as a column left of the grid");
                });
    }

    private static int armorWidth() {
        return cfg().inventoryHudArmor ? SLOT + PAD : 0;
    }

    private static int width() {
        return armorWidth() + 9 * SLOT + 2 * PAD;
    }

    private static int height() {
        return (cfg().inventoryHudArmor ? 4 : 3) * SLOT + 2 * PAD;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale());
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(), height());
        if (mc.player != null) {
            int ax = armorWidth();
            if (ax > 0) {
                for (int i = 0; i < ARMOR.length; i++) slot(g, mc, mc.player.getItemBySlot(ARMOR[i]), PAD, PAD + i * SLOT);
            }
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < 27; i++) {
                slot(g, mc, inv.getItem(i + 9), ax + PAD + (i % 9) * SLOT, PAD + (i / 9) * SLOT);
            }
        }
        g.pose().popMatrix();
    }

    private static void slot(GuiGraphicsExtractor g, Minecraft mc, ItemStack st, int x, int y) {
        g.fill(x, y, x + SLOT - 1, y + SLOT - 1, 0x30000000);
        if (st.isEmpty()) return;
        g.item(st, x + 1, y + 1);
        g.itemDecorations(mc.font, st, x + 1, y + 1);
    }
}
