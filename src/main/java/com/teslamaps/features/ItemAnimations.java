package com.teslamaps.features;

// Ported from Devonian's features/misc/ItemAnimations.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class ItemAnimations {

    private ItemAnimations() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ItemAnimations").section("Render")
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Held Item");
                    b.toggle("Item Animations", () -> c.itemAnimations, v -> c.itemAnimations = v)
                            .desc("Moves, rotates and scales the item in your hand and changes the swing.");
                    b.slider("  X Offset", -1f, 1f, () -> c.itemAnimX, v -> c.itemAnimX = v, 2);
                    b.slider("  Y Offset", -1f, 1f, () -> c.itemAnimY, v -> c.itemAnimY = v, 2);
                    b.slider("  Z Offset", -1f, 1f, () -> c.itemAnimZ, v -> c.itemAnimZ = v, 2);
                    b.slider("  Yaw", -180f, 180f, () -> c.itemAnimYaw, v -> c.itemAnimYaw = (float) Math.round(v), 0);
                    b.slider("  Pitch", -180f, 180f, () -> c.itemAnimPitch, v -> c.itemAnimPitch = (float) Math.round(v), 0);
                    b.slider("  Roll", -180f, 180f, () -> c.itemAnimRoll, v -> c.itemAnimRoll = (float) Math.round(v), 0);
                    b.slider("  Size", -2f, 2f, () -> c.itemAnimScale, v -> c.itemAnimScale = v, 2)
                            .desc("0 = normal, 1 = double, -1 = half.");
                    b.slider("  Swing Speed", -2f, 2f, () -> c.itemAnimSwingSpeed, v -> c.itemAnimSwingSpeed = v, 2)
                            .desc("0 = normal, 1 = twice as fast, -1 = half as fast.");
                    b.toggle("  Change Empty Hand", () -> c.itemAnimEmptyHand, v -> c.itemAnimEmptyHand = v);
                    b.toggle("  Ignore Haste / Fatigue", () -> c.itemAnimIgnoreEffects, v -> c.itemAnimIgnoreEffects = v)
                            .desc("Swing speed ignores Haste and Mining Fatigue.");
                    b.toggle("  No Re-Equip Animation", () -> c.itemAnimNoReequip, v -> c.itemAnimNoReequip = v)
                            .desc("The item no longer dips when Hypixel updates it or you swap items.");
                    b.toggle("  In-Place Swing", () -> c.itemAnimInPlaceSwing, v -> c.itemAnimInPlaceSwing = v)
                            .desc("The swing only rotates the item instead of moving it.");
                    b.toggle("  No Swing", () -> c.itemAnimNoSwing, v -> c.itemAnimNoSwing = v);
                    b.toggle("  No Terminator Swing", () -> c.itemAnimNoSwingTerm, v -> c.itemAnimNoSwingTerm = v);
                    b.toggle("  No Hand Sway", () -> c.itemAnimNoSway, v -> c.itemAnimNoSway = v)
                            .desc("The item no longer lags behind when you turn.");
                    b.button("  Reset Values", () -> {
                        c.itemAnimX = c.itemAnimY = c.itemAnimZ = 0f;
                        c.itemAnimYaw = c.itemAnimPitch = c.itemAnimRoll = 0f;
                        c.itemAnimScale = c.itemAnimSwingSpeed = 0f;
                        TeslaMapsConfig.save();
                    });
                });
    }

    public static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.itemAnimations && c.section("Render");
    }

    public static boolean noReequip() { return on() && cfg().itemAnimNoReequip; }

    public static boolean inPlaceSwing() { return on() && cfg().itemAnimInPlaceSwing; }

    public static boolean noSway() { return on() && cfg().itemAnimNoSway; }

    public static boolean noSwing() {
        if (!on()) return false;
        TeslaMapsConfig c = cfg();
        if (c.itemAnimNoSwing) return true;
        if (!c.itemAnimNoSwingTerm) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack held = mc.player.getMainHandItem();
        return !held.isEmpty() && "TERMINATOR".equals(ItemUtil.skyblockId(held));
    }

    public static void applyTransform(PoseStack pose, ItemStack mainHand) {
        if (!on()) return;
        TeslaMapsConfig c = cfg();
        if (mainHand.isEmpty() && !c.itemAnimEmptyHand) return;
        if (c.itemAnimPitch != 0) pose.rotateDegrees(Axis.XP, c.itemAnimPitch);
        if (c.itemAnimYaw != 0) pose.rotateDegrees(Axis.YP, c.itemAnimYaw);
        if (c.itemAnimRoll != 0) pose.rotateDegrees(Axis.ZP, c.itemAnimRoll);
        if (c.itemAnimX != 0 || c.itemAnimY != 0 || c.itemAnimZ != 0) pose.translate(c.itemAnimX, c.itemAnimY, c.itemAnimZ);
    }

    public static void applyScale(PoseStack pose, boolean emptyHand) {
        if (!on()) return;
        TeslaMapsConfig c = cfg();
        if (c.itemAnimScale == 0 || emptyHand && !c.itemAnimEmptyHand) return;
        float s = (float) Math.pow(2, c.itemAnimScale);
        pose.scale(s, s, s);
    }

    /** Own player's swing length in ticks; base = item swing duration without effects. */
    public static int swingDuration(int vanilla, int base) {
        TeslaMapsConfig c = cfg();
        int d = c.itemAnimIgnoreEffects ? base : vanilla;
        if (c.itemAnimSwingSpeed != 0) d = Math.round((float) (d / Math.pow(2, c.itemAnimSwingSpeed)));
        return Math.max(1, d);
    }
}
