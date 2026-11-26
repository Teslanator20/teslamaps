package com.teslamaps.features.fishing;

// After GanKura's BaitScanner / BaitHud and SkyHanni's FishingBaitDisplay / FishingBaitWarnings
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class FishingBait {

    private static final List<String> SAMPLE = List.of("§b48x §9Whale Bait");
    private static final String REMAINING = "Bait Remaining: ";

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static String bait;
    private static int count;
    private static boolean holding;
    private static boolean lowWarned;
    private static FishingHook lastHook;

    private FishingBait() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean any() {
        TeslaMapsConfig c = cfg();
        return c.fishingBaitDisplay || c.fishingBaitChanged || c.fishingNoBaitWarning || c.fishingBaitLow;
    }

    public static void register() {
        hud = HudEditElement.of("fishingBaitDisplay", "Fishing Bait").section("Fishing")
                .enabled(() -> cfg().fishingBaitDisplay)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("FishingBait").section("Fishing").when(FishingBait::any)
                .tick(FishingBait::tick)
                .hudHiddenInInventory("fishingbait", FishingBait::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Bait");
                    b.toggle("Fishing Bait Display", () -> c.fishingBaitDisplay, v -> c.fishingBaitDisplay = v)
                            .desc("With a rod in hand: current bait and how many are left, or No Bait. Position in /tmap gui")
                            .tags("bait", "fishing");
                    b.toggle("  Bait Changed Title", () -> c.fishingBaitChanged, v -> c.fishingBaitChanged = v)
                            .desc("Title and chat line \"old -> new\" when the bait in use switches");
                    b.toggle("  No Bait Warning", () -> c.fishingNoBaitWarning, v -> c.fishingNoBaitWarning = v)
                            .desc("Chat line when you cast without bait");
                    b.toggle("  Low Bait Warning", () -> c.fishingBaitLow, v -> c.fishingBaitLow = v)
                            .desc("Title and sound once when the bait count drops to the threshold");
                    b.slider("    Threshold", 1f, 64f, () -> (float) c.fishingBaitLowThreshold,
                            v -> c.fishingBaitLowThreshold = Math.round(v), 0);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInSkyblock()) {
            holding = false;
            lines = List.of();
            return;
        }
        holding = com.teslamaps.features.FishingHelpers.holdingRod();
        if (!holding) {
            lines = List.of();
            lastHook = null;
            return;
        }
        String newBait = null;
        int newCount = 0;
        ItemStack st = mc.player.getInventory().getItem(8);
        if (!st.isEmpty()) {
            for (String l : ItemUtil.loreLines(st)) {
                int i = l.indexOf(REMAINING);
                if (i < 0) continue;
                try {
                    newCount = Integer.parseInt(l.substring(i + REMAINING.length()).trim().replace(",", ""));
                    newBait = st.getHoverName().getString();
                } catch (NumberFormatException ignored) {}
                break;
            }
        }
        update(mc, newBait, newCount);

        FishingHook hook = mc.player.fishing;
        if (hook != null && hook != lastHook && bait == null && cfg().fishingNoBaitWarning) {
            mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §cNo bait is used!"));
        }
        lastHook = hook;

        if (cfg().fishingBaitDisplay) {
            lines = List.of(bait == null ? "§cNo Bait" : "§b" + String.format(java.util.Locale.ROOT, "%,d", count) + "x §f" + bait);
            width = TextHud.width(lines);
        } else {
            lines = List.of();
        }
    }

    private static void update(Minecraft mc, String newBait, int newCount) {
        TeslaMapsConfig c = cfg();
        String old = bait;
        boolean changed = newBait == null ? old != null : !newBait.equals(old);
        if (changed && old != null && newBait != null && c.fishingBaitChanged) {
            mc.gui.hud.setTimes(0, 30, 5);
            mc.gui.hud.setTitle(Component.literal("§eBait changed!"));
            mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §eBait changed: §f" + old + " §7-> §f" + newBait));
        }
        if (changed) lowWarned = false;
        bait = newBait;
        count = newCount;
        if (newBait == null || newCount > c.fishingBaitLowThreshold) {
            lowWarned = false;
            return;
        }
        if (!c.fishingBaitLow || lowWarned) return;
        lowWarned = true;
        mc.gui.hud.setTimes(0, 40, 5);
        mc.gui.hud.setTitle(Component.literal("§c§lBait Low §e§l" + newCount));
        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.7f);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (hud == null || lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
