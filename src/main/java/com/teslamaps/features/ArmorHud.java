package com.teslamaps.features;

// Devonian ArmorDisplay.kt, GanKura GearHud, Zen ArmorHUD
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ArmorHud {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int CELL = 18;

    private static HudEditElement hud;
    private static ItemStack barrier;

    private ArmorHud() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("armorHud", "Armor HUD")
                .enabled(() -> cfg().armorHud)
                .size(() -> width(), () -> height())
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, true));
        Feature.of("ArmorHud").when(() -> cfg().armorHud)
                .hudHiddenInInventory("armorhud", ArmorHud::render)
                .config("HUD Widgets", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Armor HUD");
                    b.toggle("Armor HUD", () -> c.armorHud, v -> c.armorHud = v)
                            .desc("Your worn armor as item icons. Position in /tmap gui.");
                    b.toggle("  Vertical", () -> c.armorHudVertical, v -> c.armorHudVertical = v);
                    b.toggle("  Panel Background", () -> c.armorHudBackground, v -> c.armorHudBackground = v);
                    b.toggle("  Slot Backgrounds", () -> c.armorHudSlotBg, v -> c.armorHudSlotBg = v);
                    b.toggle("  Barrier For Empty Slots", () -> c.armorHudEmpty, v -> c.armorHudEmpty = v);
                    b.toggle("  Helmet", () -> c.armorHudHelmet, v -> c.armorHudHelmet = v);
                    b.toggle("  Chestplate", () -> c.armorHudChest, v -> c.armorHudChest = v);
                    b.toggle("  Leggings", () -> c.armorHudLegs, v -> c.armorHudLegs = v);
                    b.toggle("  Boots", () -> c.armorHudBoots, v -> c.armorHudBoots = v);
                    b.info("  Necklace, cloak, belt, gloves: Equipment HUD in Inventory");
                });
    }

    private static boolean shows(int i) {
        TeslaMapsConfig c = cfg();
        return switch (i) {
            case 0 -> c.armorHudHelmet;
            case 1 -> c.armorHudChest;
            case 2 -> c.armorHudLegs;
            default -> c.armorHudBoots;
        };
    }

    private static int count() {
        int n = 0;
        for (int i = 0; i < 4; i++) if (shows(i)) n++;
        return Math.max(n, 1);
    }

    private static int width() {
        return (cfg().armorHudVertical ? 1 : count()) * CELL + 4;
    }

    private static int height() {
        return (cfg().armorHudVertical ? count() : 1) * CELL + 4;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || mc.player == null || mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), false);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, boolean preview) {
        TeslaMapsConfig c = cfg();
        if (barrier == null) barrier = new ItemStack(Items.BARRIER);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        if (c.armorHudBackground) HudStyle.panel(g, 0, 0, width(), height());
        int n = 0;
        for (int i = 0; i < 4; i++) {
            if (!shows(i)) continue;
            int cx = 2 + (c.armorHudVertical ? 0 : n * CELL);
            int cy = 2 + (c.armorHudVertical ? n * CELL : 0);
            n++;
            if (c.armorHudSlotBg) {
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0x608B8B8B);
                g.fill(cx + 1, cy + 1, cx + CELL - 2, cy + CELL - 2, 0x80373737);
            }
            ItemStack stack = mc.player != null ? mc.player.getItemBySlot(SLOTS[i]) : ItemStack.EMPTY;
            if (stack.isEmpty() && preview) stack = sample(i);
            if (stack.isEmpty()) {
                if (c.armorHudEmpty) g.item(barrier, cx + 1, cy + 1);
                continue;
            }
            g.item(stack, cx + 1, cy + 1);
        }
        g.pose().popMatrix();
    }

    private static ItemStack sample(int i) {
        return new ItemStack(switch (i) {
            case 0 -> Items.DIAMOND_HELMET;
            case 1 -> Items.DIAMOND_CHESTPLATE;
            case 2 -> Items.DIAMOND_LEGGINGS;
            default -> Items.DIAMOND_BOOTS;
        });
    }
}
