package com.teslamaps.features;

// Ported from SkyHanni's GardenInventoryTooltipOverflow, GardenInventoryNumbers, GardenCropMilestoneInventory
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Overflow crop milestones in the Crop Milestones menu, tiers as stack size, average and overflow chat. */
public final class CropMilestoneMenu {

    private static final String BAR_INDENT = "                    ";
    private static final Pattern UPGRADE_TIER = Pattern.compile("Current Tier: (\\d+)/(\\d+)");
    private static final Pattern COMPOSTER = Pattern.compile("^.+ ([IVXLCDM]+|\\d+)$");
    private static final int CREAM = 0xFFFFDDC1, GOLD = 0xFFE5B80B, AQUA = 0xFF55FFFF;

    private record Label(int slot, String text, int color) {}

    private static Feature feature;
    private static List<Label> labels = List.of();
    private static String average = "";
    private static int ticks;

    private CropMilestoneMenu() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.skillOverflow && c.cropMilestoneOverflow;
    }

    private static boolean upgradesOn() {
        TeslaMapsConfig c = cfg();
        return c.skillOverflow && c.gardenUpgradeStackTier;
    }

    public static void register() {
        feature = Feature.of("CropMilestoneMenu").section("Skills")
                .when(() -> on() || upgradesOn())
                .tick(CropMilestoneMenu::tick);
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!lines.isEmpty() && on() && feature.active()) tooltip(stack, lines);
        });
    }

    /** MenuSlotText leaves these menus to us while we draw them. */
    public static boolean handlesMilestones() {
        return on() && feature != null && feature.active();
    }

    public static boolean handlesUpgrades() {
        return upgradesOn() && feature != null && feature.active();
    }

    private static String title(Minecraft mc) {
        return mc.screen instanceof AbstractContainerScreen<?> cs ? StringUtil.stripTrim(cs.getTitle().getString()) : "";
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            if (!labels.isEmpty()) labels = List.of();
            average = "";
            return;
        }
        String t = StringUtil.stripTrim(cs.getTitle().getString());
        boolean milestones = t.equals("Crop Milestones") && on();
        boolean upgrades = (t.startsWith("Crop Upgrades") || t.startsWith("Composter Upgrades")) && upgradesOn();
        if (!milestones && !upgrades) {
            if (!labels.isEmpty()) labels = List.of();
            average = "";
            return;
        }
        List<Label> out = new ArrayList<>();
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (milestones) {
                String crop = GardenMilestoneData.menuCrop(stack);
                if (crop == null) continue;
                long count = GardenMilestoneData.counter(crop);
                if (count >= 0) {
                    int tier = GardenMilestoneData.tierOf(crop, count);
                    int max = GardenMilestoneData.maxTier(crop);
                    out.add(new Label(i, String.valueOf(tier), tier > max ? AQUA : tier == max ? GOLD : CREAM));
                } else {
                    String name = StringUtil.stripTrim(stack.getHoverName().getString());
                    int tier = GardenMilestoneData.parseTier(name.substring(name.lastIndexOf(' ') + 1));
                    if (tier > 0) out.add(new Label(i, String.valueOf(tier), CREAM));
                }
            } else if (t.startsWith("Crop Upgrades")) {
                for (String l : ItemUtil.loreLines(stack)) {
                    Matcher m = UPGRADE_TIER.matcher(StringUtil.stripTrim(l));
                    if (m.find()) {
                        out.add(new Label(i, m.group(1), m.group(1).equals(m.group(2)) ? GOLD : CREAM));
                        break;
                    }
                }
            } else {
                Matcher m = COMPOSTER.matcher(StringUtil.stripTrim(stack.getHoverName().getString()));
                if (!m.matches() || !ItemUtil.loreContains(stack, "Upgrade")) continue;
                int lvl = GardenMilestoneData.parseTier(m.group(1));
                if (lvl > 0) out.add(new Label(i, String.valueOf(lvl), CREAM));
            }
        }
        labels = out;
        average = milestones && cfg().cropMilestoneAverage ? averageText() : "";
    }

    private static String averageText() {
        double sum = 0;
        int n = 0;
        for (String crop : GardenMilestoneData.CROPS.keySet()) {
            long count = GardenMilestoneData.counter(crop);
            if (count < 0) continue;
            sum += GardenMilestoneData.tierOf(crop, count);
            n++;
        }
        if (n == 0) return "";
        return "§6Average Crop Milestone: §e" + String.format(java.util.Locale.ROOT, "%.2f", sum / GardenMilestoneData.CROPS.size())
                + (n < GardenMilestoneData.CROPS.size() ? " §7(" + n + "/" + GardenMilestoneData.CROPS.size() + " known)" : "");
    }

    /** From SlotLockRenderMixin. */
    public static void render(GuiGraphicsExtractor ctx) {
        if (labels.isEmpty() && average.isEmpty() || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        List<Slot> slots = cs.getMenu().slots;
        for (Label l : labels) {
            if (l.slot >= slots.size()) continue;
            Slot slot = slots.get(l.slot);
            ctx.text(mc.font, l.text, left + slot.x + 17 - mc.font.width(l.text), top + slot.y + 9, l.color);
        }
        if (!average.isEmpty()) ctx.text(mc.font, average, left, top - 11, 0xFFFFFFFF);
    }

    private static void tooltip(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (!title(mc).equals("Crop Milestones")) return;
        int maxIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getString().contains("Max tier reached!")) { maxIndex = i; break; }
        }
        if (maxIndex < 0) return;
        String crop = GardenMilestoneData.menuCrop(stack);
        if (crop == null) return;
        long count = GardenMilestoneData.counter(crop);
        if (count < 0) return;
        int tier = GardenMilestoneData.tierOf(crop, count);
        long have = count - GardenMilestoneData.totalForTier(crop, tier);
        long need = GardenMilestoneData.tierAmount(crop, tier + 1);
        double pct = need > 0 ? 100.0 * have / need : 0;
        String name = StringUtil.stripTrim(lines.get(0).getString());
        String lastWord = name.substring(name.lastIndexOf(' ') + 1);
        boolean roman = lastWord.isEmpty() || !Character.isDigit(lastWord.charAt(0));
        String level = roman ? GardenMilestoneData.toRoman(tier) : String.valueOf(tier);
        String nextLevel = roman ? GardenMilestoneData.toRoman(tier + 1) : String.valueOf(tier + 1);
        lines.set(0, Component.literal("§a" + crop + " " + level));
        lines.set(maxIndex, Component.literal("§7Progress to tier " + nextLevel + ": §e"
                + String.format(java.util.Locale.ROOT, "%.1f", pct) + "%"));
        for (int i = maxIndex + 1; i < lines.size(); i++) {
            if (!lines.get(i).getString().contains(BAR_INDENT)) continue;
            lines.set(i, Component.literal(GardenLevelHud.bar(have, need) + " §e" + GardenMilestoneData.sep(have)
                    + "§6/§e" + GardenMilestoneData.sep(need)));
            break;
        }
    }

    /** From GardenMilestoneData when the live counter crosses a tier. */
    public static void onTierUp(String crop, int oldTier, int newTier) {
        if (!on() || feature == null || !feature.active()) return;
        int max = GardenMilestoneData.maxTier(crop);
        if (newTier <= max) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String divider = "§3§l" + "▬".repeat(64);
        mc.player.sendSystemMessage(Component.literal(divider));
        mc.player.sendSystemMessage(Component.literal("  §b§lGARDEN MILESTONE §3" + crop + " §8"
                + Math.max(oldTier, max) + "➜§3" + newTier));
        mc.player.sendSystemMessage(Component.literal(" "));
        mc.player.sendSystemMessage(Component.literal("  §a§lOVERFLOW MILESTONE"));
        mc.player.sendSystemMessage(Component.literal("   §8+§b" + (newTier - max) + " §7past tier " + max));
        mc.player.sendSystemMessage(Component.literal(divider));
    }
}
