package com.teslamaps.features.choco;

// After Skyblocker's ChocolateFactorySolver and SkyHanni's CFInventory, CFTooltip, CFStats, CFUpgradeWarning
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChocoFactory {

    private static final List<String> SAMPLE = List.of("§6§lChocolate Factory VI", "§eCurrent Chocolate: §61,234,567",
            "§ePer Second: §63,780", "§ePer Hour: §613,610,592", "§eMultiplier: §61.77x", "§eBarn: §6171/190 Rabbits",
            "§eTime Tower: §62/3 Charges", "§eBest Upgrade: §b59m 4s");

    private static final java.util.regex.Pattern COST = java.util.regex.Pattern.compile("Cost ([\\d,]+) Chocolate");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, ticks;
    private static long lastWarn;

    private ChocoFactory() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("cfStats", "Chocolate Factory Stats").section(ChocoData.CATEGORY)
                .enabled(() -> cfg().cfStats)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("ChocoFactory").section(ChocoData.CATEGORY)
                .when(() -> cfg().cfHelper || cfg().cfStats || cfg().cfUpgradeWarning)
                .tick(ChocoFactory::tick)
                .hudForeground("cfstats", ChocoFactory::renderHud)
                .config(ChocoData.CATEGORY, b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Chocolate Factory");
                    b.toggle("Chocolate Factory Helper", () -> c.cfHelper, v -> c.cfHelper = v)
                            .desc("Marks the best upgrade by cost per chocolate/s: green when affordable, yellow plus the best affordable one in green. Prestige slot green/red.");
                    b.toggle("  Levels On Slots", () -> c.cfHelperLevels, v -> c.cfHelperLevels = v);
                    b.toggle("  Tooltip Stats", () -> c.cfHelperTooltips, v -> c.cfHelperTooltips = v)
                            .desc("Time until affordable, extra chocolate/s, cost per chocolate/s, time tower and prestige numbers.");
                    b.toggle("Chocolate Factory Stats", () -> c.cfStats, v -> c.cfStats = v)
                            .desc("Panel with chocolate, production, multiplier, barn, leaderboard, time tower, prestige and hitman. Position in /tmap gui");
                    b.toggle("  Show Outside The Factory", () -> c.cfStatsOutside, v -> c.cfStatsOutside = v);
                    b.toggle("Chocolate Upgrade Warning", () -> c.cfUpgradeWarning, v -> c.cfUpgradeWarning = v)
                            .desc("Chat line when the best upgrade became affordable. Uses the numbers from your last factory visit.");
                    b.slider("  Minutes Between Warnings", 1, 60, () -> (float) c.cfUpgradeWarnMinutes, v -> c.cfUpgradeWarnMinutes = Math.round(v));
                    b.toggle("  Warning Sound", () -> c.cfUpgradeWarnSound, v -> c.cfUpgradeWarnSound = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, tip) -> {
            if (cfg().cfHelper && cfg().cfHelperTooltips && ChocoData.inFactory() && cfg().section(ChocoData.CATEGORY)) onTooltip(stack, tip);
        });
    }

    static void upgradeChanged() {
        lastWarn = System.currentTimeMillis();
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        TeslaMapsConfig c = cfg();
        ChocoData.State s = ChocoData.state();
        if (c.cfStats) {
            lines = s.chocolate < 0 ? List.of("§6§lChocolate Factory", "§7Open /cf once") : build(s);
            width = TextHud.width(lines);
        }
        if (c.cfUpgradeWarning) checkWarning(s);
    }

    private static void checkWarning(ChocoData.State s) {
        if (s.bestCost <= 0 || s.chocolate < s.bestCost || !SkyblockUtils.isInSkyblock()) return;
        if (cfg().cfReminder && s.goal > 0) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < cfg().cfUpgradeWarnMinutes * 60_000L) return;
        lastWarn = now;
        if (cfg().cfUpgradeWarnSound) ChocoData.beep();
        if (ChocoData.inFactory()) return;
        ChocoData.chatOpen("§eYou have a Chocolate Factory upgrade available to purchase! §7(" + (s.bestName == null ? "?" : s.bestName) + ")");
    }

    private static List<String> build(ChocoData.State s) {
        List<String> out = new ArrayList<>();
        out.add("§6§lChocolate Factory" + (s.prestige > 0 ? " " + roman(s.prestige) : ""));
        out.add("§eCurrent Chocolate: §6" + ChocoData.commas(s.chocolate));
        if (s.thisPrestige >= 0 && s.prestige != 1) out.add("§eThis Prestige: §6" + ChocoData.commas(s.thisPrestige));
        if (s.allTime >= 0) out.add("§eAll-time: §6" + ChocoData.commas(s.allTime));
        if (s.cps > 0) {
            out.add("§ePer Second: §6" + String.format(Locale.ROOT, "%,.2f", s.cps));
            out.add("§ePer Hour: §6" + ChocoData.commas(s.cps * 3600));
            out.add("§ePer Day: §6" + ChocoData.commas(s.cps * 86400));
        }
        if (s.rawCps > 0) out.add("§eRaw Per Second: §6" + ChocoData.commas(s.rawCps));
        if (s.multiplier > 0) out.add("§eMultiplier: §6" + s.multiplier + "x");
        if (s.barnMax > 0) out.add("§eBarn: §6" + s.barnCur + "/" + s.barnMax + " Rabbits");
        if (s.leaderboard > 0) out.add("§ePosition: §b#" + ChocoData.commas(s.leaderboard) + (s.percentile > 0 ? " §7Top §a" + s.percentile + "%" : ""));
        long now = System.currentTimeMillis();
        if (s.ttActiveUntil > now) out.add("§eTime Tower: §d§lActive §7(" + ChocoData.format(s.ttActiveUntil - now) + ")");
        else if (s.ttMax > 0) out.add("§eTime Tower: §6" + s.ttCharges + "/" + s.ttMax + " Charges");
        if (s.ttMax > 0 && s.ttCharges < s.ttMax && s.ttNextCharge > 0) {
            long full = s.ttNextCharge + (long) (s.ttMax - s.ttCharges - 1) * ChocoExtras.TT_CHARGE_MS;
            out.add("§eFull Tower Charges: §b" + (full <= now ? "§aNow" : ChocoData.format(full - now)));
        }
        if (!s.maxPrestige && s.prestigeReq > 0 && s.thisPrestige >= 0) {
            long left = s.prestigeReq - s.thisPrestige;
            out.add("§eChocolate To Prestige: " + (left <= 0 ? "§aPrestige Available" : "§6" + ChocoData.commas(left)));
            if (left > 0 && s.cps > 0) out.add("§eTime To Prestige: §b" + ChocoData.format((long) (left / s.cps * 1000)));
        }
        if (s.bestCost > 0) out.add("§eBest Upgrade: " + ChocoData.timeUntil(s.bestCost));
        if (s.hitmanMax > 0) {
            out.add("§c§lRabbit Hitman");
            out.add("§eAvailable Eggs: §6" + (s.hitmanEggs > 0 ? s.hitmanEggs : "§7None") + " §7(" + s.hitmanSlots + " slots)");
            out.add("§eSlot Cooldown: " + (s.hitmanSlotReady > now ? "§b" + ChocoData.format(s.hitmanSlotReady - now) : "§aReady"));
            out.add("§eAll Slots: " + (s.hitmanAllReady > now ? "§b" + ChocoData.format(s.hitmanAllReady - now) : "§aReady"));
        }
        return out;
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty() || hud == null || !cfg().cfStats) return;
        boolean show = ChocoData.inFactory() || (cfg().cfStatsOutside && mc.gui.screen() == null && SkyblockUtils.isInSkyblock());
        if (!show) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    /** From SlotLockRenderMixin. */
    public static void renderSlots(GuiGraphicsExtractor ctx) {
        render(ctx);
        ChocoStrays.render(ctx);
        ChocoExtras.render(ctx);
    }

    private static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = cfg();
        if (!c.cfHelper || !ChocoData.inFactory() || !c.section(ChocoData.CATEGORY)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        ChocoData.State s = ChocoData.state();
        if (ChocoData.bestSlot >= 0) {
            ChocoData.Upgrade best = ChocoData.upgradeAt(ChocoData.bestSlot);
            boolean afford = best != null && s.chocolate >= best.cost();
            mark(ctx, acc, slots, ChocoData.bestSlot, afford ? 0x55FF55 : 0xFFFF55);
            if (!afford && ChocoData.bestAffordableSlot >= 0) mark(ctx, acc, slots, ChocoData.bestAffordableSlot, 0x55FF55);
        }
        if (ChocoData.prestigeSlot >= 0 && !s.maxPrestige) mark(ctx, acc, slots, ChocoData.prestigeSlot, ChocoData.canPrestige ? 0x55FF55 : 0xFF5555);
        if (ChocoData.barnSlot >= 0 && s.barnMax > 0 && s.barnMax - s.barnCur <= c.cfBarnThreshold && c.cfBarnWarning) mark(ctx, acc, slots, ChocoData.barnSlot, 0xFF5555);
        if (ChocoData.ttSlot >= 0 && s.ttActiveUntil > System.currentTimeMillis()) mark(ctx, acc, slots, ChocoData.ttSlot, 0xFF55FF);
        else if (ChocoData.ttSlot >= 0 && s.ttMax > 0 && s.ttCharges >= s.ttMax) mark(ctx, acc, slots, ChocoData.ttSlot, 0xFF5555);

        if (!c.cfHelperLevels) return;
        for (ChocoData.Upgrade u : ChocoData.upgrades) text(ctx, mc, acc, slots, u.slot(), "§6" + u.level());
        level(ctx, mc, acc, slots, ChocoData.handSlot, ChocoData.handLevel, 10);
        level(ctx, mc, acc, slots, ChocoData.shrineSlot, ChocoData.shrineLevel, 20);
        level(ctx, mc, acc, slots, ChocoData.barnSlot, ChocoData.barnLevel, 245);
        level(ctx, mc, acc, slots, ChocoData.ttSlot, ChocoData.ttLevel, 15);
        level(ctx, mc, acc, slots, ChocoData.prestigeSlot, ChocoData.prestigeLevel, 99);
    }

    private static void level(GuiGraphicsExtractor ctx, Minecraft mc, HandledScreenAccessor acc, List<Slot> slots, int slot, int level, int max) {
        if (slot < 0 || level < 0 || level >= max) return;
        text(ctx, mc, acc, slots, slot, "§6" + level);
    }

    static void mark(GuiGraphicsExtractor ctx, HandledScreenAccessor acc, List<Slot> slots, int index, int col) {
        if (index < 0 || index >= slots.size()) return;
        Slot slot = slots.get(index);
        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
        ctx.fill(sx, sy, sx + 16, sy + 16, 0x66000000 | col);
        ctx.fill(sx, sy, sx + 16, sy + 1, 0xFF000000 | col);
        ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF000000 | col);
        ctx.fill(sx, sy, sx + 1, sy + 16, 0xFF000000 | col);
        ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF000000 | col);
    }

    static void text(GuiGraphicsExtractor ctx, Minecraft mc, HandledScreenAccessor acc, List<Slot> slots, int index, String t) {
        if (index < 0 || index >= slots.size()) return;
        Slot slot = slots.get(index);
        ctx.pose().pushMatrix();
        ctx.pose().translate(acc.getX() + slot.x, acc.getY() + slot.y);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.text(mc.font, t, 0, 0, 0xFFFFFFFF);
        ctx.pose().popMatrix();
    }

    static int slotOf(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return -1;
        for (Slot s : cs.getMenu().slots) if (s.getItem() == stack) return s.index;
        return -1;
    }

    private static void onTooltip(ItemStack stack, List<Component> tip) {
        int slot = slotOf(stack);
        if (slot < 0) return;
        ChocoData.State s = ChocoData.state();
        List<String> add = new ArrayList<>();
        ChocoData.Upgrade u = ChocoData.upgradeAt(slot);
        if (u != null) {
            add.add("§7Time until upgrade: " + ChocoData.timeUntil(u.cost()));
            add.add("§7Extra: §6" + String.format(Locale.ROOT, "%,.2f", u.extra()) + " §7choc/s");
            add.add("§7Cost per choc/s: §6" + ChocoData.commas(u.perCps()));
            if (slot == ChocoData.bestSlot) add.add(s.chocolate >= u.cost() ? "§aBest upgrade" : "§eBest upgrade, can't afford");
            else if (slot == ChocoData.bestAffordableSlot) add.add("§aBest upgrade you can afford");
        } else if (slot == ChocoData.ttSlot && s.rawCps > 0 && s.ttMultiplier > 0) {
            add.add("§7One charge gives: §6" + ChocoData.commas(s.rawCps * s.ttMultiplier * 3600));
            add.add("§7CPS while active: §6" + ChocoData.commas(s.ttActiveUntil > System.currentTimeMillis() ? s.cps : s.rawCps * (s.multiplier + s.ttMultiplier)));
        } else if (slot == ChocoData.prestigeSlot && !s.maxPrestige && s.prestigeReq > 0 && s.thisPrestige >= 0) {
            long left = s.prestigeReq - s.thisPrestige;
            if (left > 0) {
                add.add("§7Chocolate until prestige: §6" + ChocoData.commas(left));
                if (s.cps > 0) add.add("§7Time until prestige: §b" + ChocoData.format((long) (left / s.cps * 1000)));
            }
        } else if (slot == ChocoData.shrineSlot || slot == ChocoData.handSlot || slot == ChocoData.barnSlot) {
            String joined = String.join(" ", com.teslamaps.utils.ItemUtil.loreLines(stack));
            java.util.regex.Matcher m = COST.matcher(joined);
            if (m.find()) add.add("§7Time until upgrade: " + ChocoData.timeUntil(ChocoData.num(m.group(1))));
        }
        if (add.isEmpty()) return;
        tip.add(Component.literal("§8§m-----------------"));
        for (String a : add) tip.add(Component.literal(a));
    }
}
