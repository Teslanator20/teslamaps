package com.teslamaps.features;

// Skytils CooldownTracker: action bar mana line + cooldown from the item lore
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AbilityCooldowns {

    private static final Pattern MANA_USED = Pattern.compile("-[\\d,]+ Mana \\(([^)]+)\\)");
    private static final Pattern COOLDOWN = Pattern.compile("^Cooldown: (?:(\\d+)m ?)?(\\d+(?:\\.\\d+)?)s");
    private static final List<String> SAMPLE = List.of("§6Instant Transmission§7: §e2.4s", "§6Wither Impact§7: §e4.0s");

    private record Entry(String name, String itemId, long start, long duration) {
        long left(long now) { return start + duration - now; }
    }

    private static final Map<String, Entry> ACTIVE = new LinkedHashMap<>();
    private static final Map<String, Entry> BY_ID = new HashMap<>();
    private static List<String> lines = List.of();
    private static int width;
    private static String lastSeen = null;
    private static long lastSeenAt = 0;
    private static HudEditElement hud;

    private AbilityCooldowns() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return (c.abilityCooldownHud || c.abilityCooldownShade) && c.section("Inventory");
    }

    public static void register() {
        hud = HudEditElement.of("abilityCooldownHud", "Ability Cooldowns").section("Inventory")
                .enabled(() -> cfg().abilityCooldownHud)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("AbilityCooldowns").section("Inventory").when(() -> cfg().abilityCooldownHud || cfg().abilityCooldownShade)
                .tick(AbilityCooldowns::tick)
                .hudHiddenInInventory("abilitycooldowns", AbilityCooldowns::render)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Ability Cooldowns");
                    b.toggle("Ability Cooldown HUD", () -> c.abilityCooldownHud, v -> c.abilityCooldownHud = v)
                            .desc("Countdown for every item ability. Name and cooldown come from the item lore, started by the mana line in the action bar (or the click for abilities without mana cost). Position in /tmap gui.")
                            .tags("cooldown", "ability", "lore");
                    b.toggle("  Notify When Ready", () -> c.abilityCooldownNotify, v -> c.abilityCooldownNotify = v)
                            .desc("Chat line once an ability is off cooldown");
                    b.toggle("Shade Item Icon on Cooldown", () -> c.abilityCooldownShade, v -> c.abilityCooldownShade = v)
                            .desc("Vanilla cooldown shading on the item that used the ability (Spirit Leap, Shadow Fury, Giant's Sword, ...), cooldown read from the lore")
                            .tags("cooldown", "spirit leap", "shadow fury", "giants sword", "item cooldowns");
                    SbItemCooldowns.config(b);
                });
    }

    private static void tick() {
        if (ACTIVE.isEmpty()) {
            if (!lines.isEmpty()) lines = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>(ACTIVE.size());
        Iterator<Entry> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            long left = e.left(now);
            if (left <= 0) {
                it.remove();
                BY_ID.remove(e.itemId, e);
                if (cfg().abilityCooldownNotify && cfg().abilityCooldownHud) notifyReady(e.name);
                continue;
            }
            String col = left <= 1000 ? "§a" : left <= 3000 ? "§e" : "§c";
            out.add("§6" + e.name + "§7: " + col + String.format(Locale.ROOT, "%.1fs", left / 1000.0));
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static void notifyReady(String name) {
        Player p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§a[TeslaMaps] §6" + name + " §aready"));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().abilityCooldownHud || mc.gui.hud.isHidden() || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    /** Action bar, main thread. */
    public static void onActionBar(Component message) {
        if (!on()) return;
        String s = StringUtil.strip(message.getString());
        if (!s.contains(" Mana (")) {
            lastSeen = null;
            return;
        }
        Matcher m = MANA_USED.matcher(s);
        if (!m.find()) return;
        String name = m.group(1).trim();
        long now = System.currentTimeMillis();
        boolean repeat = name.equals(lastSeen) && now - lastSeenAt < 1500;
        lastSeen = name;
        lastSeenAt = now;
        if (repeat) return;
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        if (tryStart(p.getMainHandItem(), name, false)) return;
        for (int i = 0; i < 9; i++) if (tryStart(p.getInventory().getItem(i), name, false)) return;
    }

    /** Right click with the main hand: abilities without a mana cost never show in the action bar. */
    public static void onUseItem(ItemStack stack) {
        if (!on() || stack.isEmpty() || !SkyblockUtils.isInSkyblock()) return;
        tryStart(stack, null, true);
    }

    private static boolean tryStart(ItemStack stack, String ability, boolean freeRightClick) {
        if (stack.isEmpty()) return false;
        List<String> lore = ItemUtil.loreLines(stack);
        if (lore.isEmpty()) return false;
        String block = null;
        boolean match = false, mana = false;
        for (String raw : lore) {
            String line = raw.trim();
            int a = line.indexOf("Ability: ");
            if (a >= 0) {
                String rest = line.substring(a + 9);
                block = abilityName(rest);
                match = ability != null ? block.equalsIgnoreCase(ability)
                        : rest.contains("RIGHT CLICK") && !rest.contains("SNEAK");
                mana = false;
                continue;
            }
            if (block == null || !match) continue;
            if (line.isEmpty()) { block = null; continue; }
            if (line.startsWith("Mana Cost:")) { mana = true; continue; }
            Matcher m = COOLDOWN.matcher(line);
            if (!m.find()) continue;
            if (freeRightClick && mana) return false;
            double sec = Double.parseDouble(m.group(2)) + (m.group(1) == null ? 0 : Integer.parseInt(m.group(1)) * 60);
            start(block, ItemUtil.skyblockId(stack), (long) (sec * 1000));
            return true;
        }
        return false;
    }

    private static String abilityName(String rest) {
        String[] words = rest.split(" +");
        int end = words.length;
        while (end > 1 && isKeyWord(words[end - 1])) end--;
        return String.join(" ", java.util.Arrays.copyOf(words, end)).trim();
    }

    private static boolean isKeyWord(String w) {
        return w.equals("RIGHT") || w.equals("LEFT") || w.equals("CLICK") || w.equals("SNEAK") || w.equals("HOLD");
    }

    private static void start(String name, String itemId, long ms) {
        if (ms <= 0) return;
        long now = System.currentTimeMillis();
        Entry old = ACTIVE.get(name);
        if (old != null && old.left(now) > 0) return;
        Entry e = new Entry(name, itemId, now, ms);
        ACTIVE.put(name, e);
        if (!itemId.isEmpty()) BY_ID.put(itemId, e);
    }

    /** Hotbar/inventory icon shading, per drawn item. */
    public static float percent(ItemStack stack, float vanilla) {
        if (BY_ID.isEmpty() || stack.isEmpty() || !cfg().abilityCooldownShade || !cfg().section("Inventory")) return vanilla;
        Entry e = BY_ID.get(ItemUtil.skyblockId(stack));
        if (e == null) return vanilla;
        long left = e.left(System.currentTimeMillis());
        return left <= 0 ? vanilla : Math.max(vanilla, (float) left / e.duration);
    }
}
