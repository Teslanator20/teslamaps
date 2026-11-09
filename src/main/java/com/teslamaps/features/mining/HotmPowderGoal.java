package com.teslamaps.features.mining;

// Ported from SkyOcean's HotmHelper.kt reminder (NEU's HotmDesires.kt as concept)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Click a perk you cannot afford in the HotM menu: a chat line once you have the powder for it. */
public final class HotmPowderGoal {

    private static final Pattern COST = Pattern.compile("^([\\d,]+) (Mithril|Gemstone|Glacite) Powder$");
    private static final Pattern TAB = Pattern.compile("^(Mithril|Gemstone|Glacite)(?: Powder)?: ([\\d,]+)$");

    private static Feature feature;
    private static final Map<String, Long> lastReminder = new HashMap<>();
    private static long lastClick;
    private static int ticks;

    private HotmPowderGoal() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HotmPowderGoal").section("Mining").when(() -> cfg().hotmPowderGoal)
                .tick(HotmPowderGoal::tick);
    }

    /** Rows in the HotM Menu block, called from HotmPerks' config block. */
    public static void config(com.teslamaps.screen.config.ConfigBuilder b) {
        TeslaMapsConfig c = b.config();
        b.toggle("HotM Powder Reminder", () -> c.hotmPowderGoal, v -> c.hotmPowderGoal = v)
                .desc("Left-click a perk you can't afford in the HotM menu to set a goal for its powder type (click again to clear). A clickable chat line (opens /hotm) appears once the tab list shows enough powder.")
                .tags("hotm", "powder", "reminder", "goal", "perk");
        b.toggle("  Also Show Title", () -> c.hotmPowderGoalTitle, v -> c.hotmPowderGoalTitle = v);
        b.toggle("  Repeat Until Bought", () -> c.hotmPowderGoalRepeat, v -> c.hotmPowderGoalRepeat = v)
                .desc("Off: remind once and forget the goal. On: repeat at the interval below until the goal is cleared.");
        b.slider("  Repeat Interval (min)", 1f, 60f, () -> (float) c.hotmPowderGoalInterval,
                v -> c.hotmPowderGoalInterval = Math.round(v), 0);
        b.button("  Clear Powder Goals", () -> { c.hotmPowderGoals.clear(); TeslaMapsConfig.save(); });
    }

    /** From HandledScreenClickMixin.slotClicked, never cancels. */
    public static void onSlotClick(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput type) {
        if (feature == null || slot == null || button != 0 || type != ContainerInput.PICKUP || !feature.active()) return;
        if (slot.container instanceof Inventory) return;
        if (!StringUtil.stripTrim(screen.getTitle().getString()).startsWith("Heart of the Mountain")) return;
        ItemStack item = slot.getItem();
        if (item.isEmpty()) return;
        List<String> lore = ItemUtil.loreLines(item);
        boolean poor = false;
        String powder = null;
        long cost = 0;
        for (String raw : lore) {
            String l = StringUtil.stripTrim(raw);
            if (l.startsWith("You don't have enough ")) poor = true;
            Matcher m = COST.matcher(l);
            if (m.matches() && powder == null) {
                powder = m.group(2);
                try { cost = Long.parseLong(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return; }
            }
        }
        String perk = StringUtil.stripTrim(item.getHoverName().getString());
        Map<String, String> goals = cfg().hotmPowderGoals;
        String old = powder == null ? null : goals.get(powder);
        if (old != null && old.startsWith(perk + "|")) {
            goals.remove(powder);
            msg("§7Removed the §e" + powder + " Powder §7goal for §e" + perk + "§7.");
        } else if (poor && powder != null) {
            goals.put(powder, perk + "|" + cost);
            lastReminder.remove(powder);
            msg("§7Reminder set: §e" + String.format("%,d", cost) + " " + powder + " Powder §7for §e" + perk + "§7.");
        } else {
            return;
        }
        lastClick = System.currentTimeMillis();
        TeslaMapsConfig.save();
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        Map<String, String> goals = cfg().hotmPowderGoals;
        if (goals.isEmpty() || System.currentTimeMillis() - lastClick < 5000) return;
        Map<String, Long> have = new HashMap<>();
        for (String line : TabListUtils.getTabListLines()) {
            Matcher m = TAB.matcher(StringUtil.stripTrim(line));
            if (!m.matches()) continue;
            try { have.put(m.group(1), Long.parseLong(m.group(2).replace(",", ""))); } catch (NumberFormatException ignored) {}
        }
        if (have.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean any = false, changed = false;
        for (Iterator<Map.Entry<String, String>> it = goals.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            Long cur = have.get(e.getKey());
            int bar = e.getValue().lastIndexOf('|');
            if (cur == null || bar < 0) continue;
            long need;
            try { need = Long.parseLong(e.getValue().substring(bar + 1)); } catch (NumberFormatException ex) { it.remove(); changed = true; continue; }
            if (cur < need) continue;
            if (cfg().hotmPowderGoalRepeat) {
                Long last = lastReminder.get(e.getKey());
                if (last != null && now - last < cfg().hotmPowderGoalInterval * 60_000L) continue;
                lastReminder.put(e.getKey(), now);
            } else {
                it.remove();
                changed = true;
            }
            any = true;
            remind(e.getKey(), e.getValue().substring(0, bar));
        }
        if (changed) TeslaMapsConfig.save();
        if (any && cfg().hotmPowderGoalTitle) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.hud.setTimes(5, 40, 10);
            mc.gui.hud.setTitle(Component.literal("§e§lHotM perk upgrade!"));
        }
    }

    private static void remind(String powder, String perk) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String color = switch (powder) { case "Mithril" -> "§2"; case "Gemstone" -> "§d"; default -> "§b"; };
        mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §eYou have enough " + color + powder + " Powder §eto upgrade §6" + perk + "§e!")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/hotm"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to open the HotM menu")))));
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] " + s));
    }
}
