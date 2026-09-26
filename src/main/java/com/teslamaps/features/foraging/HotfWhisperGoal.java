package com.teslamaps.features.foraging;

// Ported from SkyOcean's HotfHelper.kt reminder
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Left-click a HotF perk you can't afford: reminder once the Forest Whispers reach its cost. */
public final class HotfWhisperGoal {

    private static final String KEY = "Forest";
    private static final Pattern COST = Pattern.compile("^([\\d,]+) Forest Whispers$");
    private static final Pattern HAVE = Pattern.compile("^(?:Forest )?Whispers: ([\\d,]+)$");

    private static Feature feature;
    private static long lastReminder, lastClick, menuWhispers = -1;
    private static int ticks, goalSlot = -1;

    private HotfWhisperGoal() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HotfWhisperGoal").section("Foraging").when(() -> cfg().hotfWhisperGoal)
                .tick(HotfWhisperGoal::tick)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Heart of the Forest Reminder");
                    b.toggle("Whisper Reminder", () -> c.hotfWhisperGoal, v -> c.hotfWhisperGoal = v)
                            .desc("Left-click a perk you can't afford in the HotF menu to set a reminder (click again to clear, the perk gets a blue background). Chat line (opens /hotf) once you have enough Forest Whispers. Spent/total, next 10 levels and rest until max: Mining > Powder / Whispers Per Perk.")
                            .tags("hotf", "whispers", "reminder", "goal", "perk");
                    b.toggle("  Also Show Title", () -> c.hotfWhisperGoalTitle, v -> c.hotfWhisperGoalTitle = v);
                    b.toggle("  Repeat Until Bought", () -> c.hotfWhisperGoalRepeat, v -> c.hotfWhisperGoalRepeat = v)
                            .desc("Off: remind once and forget the goal. On: repeat at the interval below until cleared.");
                    b.slider("  Repeat Interval (min)", 1f, 60f, () -> (float) c.hotfWhisperGoalInterval,
                            v -> c.hotfWhisperGoalInterval = Math.round(v), 0);
                    b.button("  Clear Reminder", () -> { c.hotfWhisperGoals.clear(); TeslaMapsConfig.save(); });
                });
    }

    /** From HandledScreenClickMixin.slotClicked; true = swallow the click. */
    public static boolean onSlotClick(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput type) {
        if (feature == null || slot == null || button != 0 || type != ContainerInput.PICKUP || !feature.active()) return false;
        if (slot.container instanceof Inventory || !HotF.inInventory()) return false;
        ItemStack item = slot.getItem();
        if (item.isEmpty()) return false;
        boolean poor = false;
        long cost = -1;
        for (String raw : ItemUtil.loreLines(item)) {
            String l = StringUtil.stripTrim(raw);
            if (l.startsWith("You don't have enough ")) poor = true;
            Matcher m = COST.matcher(l);
            if (m.matches() && cost < 0) {
                try { cost = Long.parseLong(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return false; }
            }
        }
        String perk = StringUtil.stripTrim(item.getHoverName().getString());
        Map<String, String> goals = cfg().hotfWhisperGoals;
        String old = goals.get(KEY);
        if (old != null && old.startsWith(perk + "|")) {
            goals.remove(KEY);
            goalSlot = -1;
            msg("§7Removed the Whisper reminder for §e" + perk + "§7.");
        } else if (poor && cost > 0) {
            goals.put(KEY, perk + "|" + cost);
            goalSlot = slot.index;
            lastReminder = 0;
            msg("§7Reminder set: §e" + String.format("%,d", cost) + " §3Forest Whispers §7for §e" + perk + "§7.");
        } else {
            return false;
        }
        lastClick = System.currentTimeMillis();
        TeslaMapsConfig.save();
        return true;
    }

    /** Blue background behind the reminded perk, from SlotBackgrounds. */
    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (goalSlot < 0 || slot.index != goalSlot || slot.container instanceof Inventory) return;
        if (feature == null || !feature.active() || !HotF.inInventory()) return;
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF3355DD);
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        String goal = cfg().hotfWhisperGoals.get(KEY);
        Minecraft mc = Minecraft.getInstance();
        if (HotF.inInventory() && mc.screen instanceof AbstractContainerScreen<?> cs) scanMenu(cs, goal);
        else goalSlot = -1;
        if (ticks % 20 != 0 || goal == null || System.currentTimeMillis() - lastClick < 5000) return;

        long have = menuWhispers;
        for (String line : TabListUtils.getTabListLines()) {
            Matcher m = HAVE.matcher(StringUtil.stripTrim(line));
            if (m.matches()) { have = parse(m.group(1)); break; }
        }
        int bar = goal.lastIndexOf('|');
        if (have < 0 || bar < 0) return;
        long need = parse(goal.substring(bar + 1));
        if (need < 0) { cfg().hotfWhisperGoals.remove(KEY); return; }
        if (have < need) return;
        long now = System.currentTimeMillis();
        if (cfg().hotfWhisperGoalRepeat) {
            if (now - lastReminder < cfg().hotfWhisperGoalInterval * 60_000L) return;
            lastReminder = now;
        } else {
            cfg().hotfWhisperGoals.remove(KEY);
            goalSlot = -1;
            TeslaMapsConfig.save();
        }
        remind(goal.substring(0, bar));
        if (cfg().hotfWhisperGoalTitle) {
            mc.gui.setTimes(5, 40, 10);
            mc.gui.setTitle(Component.literal("§3§lHotF perk upgrade!"));
        }
    }

    private static void scanMenu(AbstractContainerScreen<?> cs, String goal) {
        String perk = goal == null ? null : goal.substring(0, Math.max(0, goal.lastIndexOf('|')));
        int found = -1;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            if (name.equals("Heart of the Forest")) {
                for (String line : ItemUtil.loreLines(st)) {
                    Matcher m = HAVE.matcher(StringUtil.stripTrim(line));
                    if (m.matches()) menuWhispers = parse(m.group(1));
                }
            } else if (perk != null && name.equals(perk)) {
                found = slot.index;
            }
        }
        goalSlot = found;
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void remind(String perk) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §eYou have enough §3Forest Whispers §eto upgrade §6" + perk + "§e!")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/hotf"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to open the HotF menu")))));
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] " + s));
    }
}
