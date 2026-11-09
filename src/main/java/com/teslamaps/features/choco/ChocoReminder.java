package com.teslamaps.features.choco;

// After SkyHanni's CFCustomReminder
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChocoReminder {

    private static final Pattern COST = Pattern.compile("Cost ([\\d,]+) Chocolate");
    private static final Pattern MILESTONE = Pattern.compile("Requires (\\S+) all-time Chocolate!");
    private static final List<String> SAMPLE = List.of("§fRabbit Bro 15 §ein §b3h 12m");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, ticks;
    private static long lastWarn;
    private static boolean inMenu;

    private ChocoReminder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("cfReminder", "Chocolate Factory Goal").section(ChocoData.CATEGORY)
                .enabled(() -> cfg().cfReminder)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("ChocoReminder").section(ChocoData.CATEGORY).when(() -> cfg().cfReminder)
                .tick(ChocoReminder::tick)
                .hudForeground("cfreminder", ChocoReminder::render)
                .config(ChocoData.CATEGORY, b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Chocolate Factory Goal");
                    b.toggle("Chocolate Goal Reminder", () -> c.cfReminder, v -> c.cfReminder = v)
                            .desc("Click an upgrade or milestone in the factory to make it your goal (click again to clear). HUD shows the time left; chat line and beep once it is affordable. Position in /tmap gui");
                    b.toggle("  Show Outside The Factory", () -> c.cfReminderOutside, v -> c.cfReminderOutside = v);
                    b.toggle("  Hide 'Not Enough Chocolate' Messages", () -> c.cfReminderHideChat, v -> c.cfReminderHideChat = v);
                    b.button("  Clear Goal", ChocoReminder::reset);
                });
    }

    private static void reset() {
        ChocoData.State s = ChocoData.state();
        s.goal = -1;
        s.goalName = null;
        lines = List.of();
        ChocoData.changed();
    }

    static void onSlotClick(Slot slot, int button) {
        if (!cfg().cfReminder || !inMenu) return;
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return;
        String name = StringUtil.stripTrim(st.getHoverName().getString());
        if (name.startsWith("Time Tower") && button == 1) return;
        List<String> lore = ItemUtil.loreLines(st);
        String joined = String.join(" ", lore);
        ChocoData.State s = ChocoData.state();
        double cost;
        String label;
        Matcher m = COST.matcher(joined);
        if (m.find()) {
            cost = ChocoData.num(m.group(1));
            label = name;
        } else {
            Matcher mm = MILESTONE.matcher(joined);
            if (!mm.find() || s.allTime < 0 || s.chocolate < 0) return;
            double amount = NumberUtil.parseSuffixed(mm.group(1));
            cost = amount - s.allTime + s.chocolate;
            label = "§6" + mm.group(1) + " Chocolate Milestone";
        }
        if (s.chocolate >= 0 && cost <= s.chocolate) {
            if (s.goal > 0) reset();
            return;
        }
        if (label.equals(s.goalName)) {
            reset();
            return;
        }
        s.goal = (long) cost;
        s.goalName = label;
        ChocoData.changed();
        ticks = 19;
    }

    /** Hides Hypixel's "not enough chocolate" spam in the factory while a goal is set. From ChatMixin. */
    public static boolean hideChat(String text) {
        TeslaMapsConfig c = cfg();
        if (!c.cfReminder || !c.cfReminderHideChat || !inMenu || !c.section(ChocoData.CATEGORY)) return false;
        String t = StringUtil.stripTrim(text);
        return t.equals("You don't have enough Chocolate!") || t.equals("You don't have the required items!")
                || (t.startsWith("You must collect ") && t.endsWith(" all-time Chocolate!"));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        String title = mc.screen instanceof AbstractContainerScreen<?> cs ? StringUtil.stripTrim(cs.getTitle().getString()) : "";
        inMenu = ChocoData.inFactory() || title.equals("Chocolate Shop") || title.equals("Chocolate Factory Milestones");
        if (++ticks % 20 != 0) return;
        ChocoData.State s = ChocoData.state();
        if (s.goal <= 0 || s.goalName == null) {
            lines = List.of();
            return;
        }
        double sec = ChocoData.secondsUntil(s.goal);
        if (sec == 0) {
            lines = List.of("§aGoal Reached! §eBuy §f" + s.goalName);
            warn(s);
        } else {
            lines = List.of("§f" + s.goalName + " §ein " + ChocoData.timeUntil(s.goal));
        }
        width = TextHud.width(lines);
    }

    private static void warn(ChocoData.State s) {
        if (inMenu || !SkyblockUtils.isInSkyblock()) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < cfg().cfUpgradeWarnMinutes * 60_000L) return;
        lastWarn = now;
        ChocoData.beep();
        ChocoData.chatOpen("§eYou can now buy §f" + s.goalName + " §ein the Chocolate Factory! §7(click to open)");
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (lines.isEmpty() || hud == null || mc.options.hideGui) return;
        boolean show = inMenu || (cfg().cfReminderOutside && mc.screen == null && SkyblockUtils.isInSkyblock());
        if (!show) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
