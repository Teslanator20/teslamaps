package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Lowers mouse sensitivity while a farming tool or the Squeaky Mousemat is held,
 * so a straight lane stays straight. The vanilla option is never written: the
 * value is swapped inside {@code MouseHandler.turnPlayer}.
 */
public class FarmingSensitivity {

    private static final String MOUSEMAT = "squeaky mousemat";

    private static boolean autoActive = false;
    private static boolean manualLocked = false;
    private static boolean pendingChecked = false;
    private static boolean releaseWasDown = false;
    private static HudEditElement el;

    public static void register() {
        el = HudEditElement.of("farmingSensitivityHud", "Mouse Lock").ctx(HudEditElement.HudCtx.GARDEN)
                .section("Garden").enabled(() -> TeslaMapsConfig.get().farmingSensitivityHud)
                .size(() -> Minecraft.getInstance().font.width("Sensitivity Lowered") + 4, () -> 12)
                .preview((g, m, x, y, s) -> draw(g, m, x, y, s, "Mouse Locked"));
        Feature.of("FarmingSensitivityChat").section("Garden").serverChat(FarmingSensitivity::onChat);
        Feature.of("FarmingSensitivityHud").section("Garden").when(() -> TeslaMapsConfig.get().farmingSensitivityHud)
                .hud("farmingsensitivity", FarmingSensitivity::render);
    }

    /** Quitting while the old options override was live left the low value in options.txt — put it back. */
    private static void restorePending() {
        if (pendingChecked) return;
        pendingChecked = true;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (config.sensitivityBackup < 0) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.sensitivity().set(config.sensitivityBackup);
        mc.options.save();
        config.sensitivityBackup = -1;
        TeslaMapsConfig.save();
    }

    /**
     * True while looking around should be suppressed entirely. The vanilla
     * option bottoms out at {@code (0 * 0.6 + 0.2)^3}, so zero is never really
     * zero without stopping the turn itself.
     */
    public static boolean blocksTurning() {
        if (!manualLocked && !autoActive) return false;
        if (!TeslaMapsConfig.get().section("Garden")) return false;
        return manualLocked || TeslaMapsConfig.get().farmingSensitivityValue <= 0.001f;
    }

    public static Object sensitivity(Object original) {
        if (!autoActive || !TeslaMapsConfig.get().section("Garden")) return original;
        return (double) TeslaMapsConfig.get().farmingSensitivityValue;
    }

    public static void tick() {
        restorePending();
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (mc.player == null || !SkyblockUtils.isInGarden()) {
            autoActive = false;
            manualLocked = false;
            return;
        }

        int key = config.mousematReleaseKey;
        boolean down = key != -1 && mc.gui.screen() == null && com.teslamaps.utils.KeyNames.isDown(key);
        if (down && !releaseWasDown && manualLocked) setLocked(false, "Mouse unlocked");
        releaseWasDown = down;

        autoActive = config.farmingSensitivity
                && holdingFarmingItem(mc.player.getMainHandItem())
                && !(config.farmingSensitivityGroundOnly && !mc.player.onGround())
                && !(config.farmingSensitivityPauseBarn && inBarn(mc.player));
    }

    private static boolean inBarn(Player p) {
        return p.getX() <= 35.5 && p.getX() >= -32.5 && p.getZ() <= -4.5 && p.getZ() >= -46.5;
    }

    public static void toggleLock() {
        if (!manualLocked && !SkyblockUtils.isInGarden()) {
            say("§cOnly in the Garden");
            return;
        }
        setLocked(!manualLocked, manualLocked ? "Mouse unlocked" : "Mouse locked · /tmap mouselock to unlock");
    }

    private static void setLocked(boolean locked, String msg) {
        manualLocked = locked;
        say("§f" + msg);
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + msg));
    }

    private static void onChat(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!SkyblockUtils.isInGarden()) return;
        String text = StringUtil.strip(raw).trim();
        if (c.farmingMouseLockOnMousemat && !manualLocked && text.equals("Snapped to squeaky mousemat!")) {
            int key = c.mousematReleaseKey;
            setLocked(true, key == -1 ? "Mouse locked (mousemat) · /tmap mouselock to unlock"
                    : "Mouse locked (mousemat) · press §e" + com.teslamaps.utils.KeyNames.display(key) + "§f to unlock");
        } else if (c.farmingMouseUnlockOnTeleport && manualLocked
                && (text.equals("Warping...") || (text.startsWith("Teleported you to ") && text.endsWith("!")))) {
            setLocked(false, "Mouse unlocked (teleport)");
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (el == null) return;
        String text = manualLocked || blocksTurning() ? "Mouse Locked" : autoActive ? "Sensitivity Lowered" : null;
        if (text == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, el.x(), el.y(), el.scale(), text);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.text(mc.font, Component.literal(text), 2, 2, 0xFFFFFF55, true);
        pose.popMatrix();
    }

    public static boolean isFarmingTool(ItemStack stack) {
        return holdingFarmingItem(stack);
    }

    private static boolean holdingFarmingItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        String id = ItemUtil.skyblockId(stack);
        if (id != null && !id.isEmpty()) {
            if (id.equals("SQUEAKY_MOUSEMAT")) return true;
            // Explicitly not the vacuum: aiming at pests needs the normal sensitivity.
            if (id.contains("VACUUM")) return false;
            return id.startsWith("THEORETICAL_HOE") || id.startsWith("HOE_") || id.contains("_HOE") || id.equals("BINGHOE")
                    || id.contains("DICER") || id.startsWith("COCO_CHOPPER")
                    || id.startsWith("CACTUS_KNIFE") || id.startsWith("FUNGI_CUTTER")
                    || id.endsWith("GARDENING_AXE");
        }

        String name = stack.getHoverName().getString().toLowerCase();
        if (name.contains(MOUSEMAT)) return true;
        if (name.contains("vacuum")) return false;
        return name.contains("hoe") || name.contains("dicer") || name.contains("chopper");
    }

    /** Clears every override. Safe to call at any time. */
    public static void restore() {
        restorePending();
        autoActive = false;
        manualLocked = false;
    }
}
