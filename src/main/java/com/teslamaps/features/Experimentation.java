package com.teslamaps.features;

// Ported from SkyHanni's SuperpairsClicksAlert.kt, UltraRareBookAlert.kt, ExperimentationXPOverlay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Experimentation {

    private static final Pattern ROUNDS_NEEDED = Pattern.compile("(?:Chain|Series) of (\\d+):");
    private static final Pattern TYPE = Pattern.compile("(Superpairs|Chronomatron|Ultrasequencer) \\((.+)\\)");
    private static final Pattern ROUND = Pattern.compile("Round: (\\d+)");
    private static final Pattern XP = Pattern.compile("^([\\d.]+)k Enchanting Exp$");

    private static Feature feature;
    private static Screen lastScreen;
    private static String type;           // Superpairs / Chronomatron / Ultrasequencer, null outside a game
    private static boolean stakes;
    private static int roundsNeeded = -1;
    private static int ultraRound;
    private static boolean rareFired;
    private static long rareShownAt;
    private static String rareName = "";
    private static Map<Integer, String> xpText = Map.of();

    private Experimentation() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("Experimentation").section("Inventory").when(() -> cfg().experimentationAlerts)
                .tick(Experimentation::tick)
                .config("Inventory", b -> {
                    b.label("Experimentation Table");
                    b.toggle("Experimentation Alerts", () -> cfg().experimentationAlerts, v -> cfg().experimentationAlerts = v)
                            .desc("Max-rounds beep, ultra-rare alert and Superpairs XP on the dyes.");
                    b.toggle("  Max Superpairs Clicks Alert", () -> cfg().expMaxRoundsAlert, v -> cfg().expMaxRoundsAlert = v);
                    b.toggle("  Ultra-Rare Alert", () -> cfg().expUltraRareAlert, v -> cfg().expUltraRareAlert = v);
                    b.toggle("  Superpairs XP On Dyes", () -> cfg().expSuperpairsXp, v -> cfg().expSuperpairsXp = v);
                    b.toggle("Hide Tooltips In Chronomatron/Ultrasequencer", () -> cfg().expHideTooltips, v -> cfg().expHideTooltips = v)
                            .desc("No item tooltips in those two games, so they never cover the flashing slots.");
                });
    }

    private static Screen tipScreen;
    private static boolean tipHide;

    // SkyblockAddons' SHOW_EXPERIMENTATION_TABLE_TOOLTIPS
    public static boolean hideTooltip(Screen s) {
        TeslaMapsConfig c = cfg();
        if (!c.expHideTooltips || !c.section("Inventory")) return false;
        if (s != tipScreen) {
            tipScreen = s;
            String t = StringUtil.stripTrim(s.getTitle().getString());
            tipHide = t.startsWith("Chronomatron") || t.startsWith("Ultrasequencer");
        }
        return tipHide;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != lastScreen) {
            lastScreen = mc.gui.screen();
            onOpen(mc.gui.screen());
        }
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        TeslaMapsConfig c = cfg();

        if (stakes && c.expMaxRoundsAlert) readRoundsNeeded(cs);
        if (type == null) return;

        switch (type) {
            case "Chronomatron" -> {
                if (!c.expMaxRoundsAlert || roundsNeeded < 0) break;
                ItemStack st = containerItem(cs, 4);
                if (st == null) break;
                Matcher m = ROUND.matcher(StringUtil.stripTrim(st.getHoverName().getString()));
                if (m.find() && Integer.parseInt(m.group(1)) > roundsNeeded) maxReached(mc);
            }
            case "Ultrasequencer" -> {
                if (!c.expMaxRoundsAlert || roundsNeeded < 0) break;
                for (Slot slot : cs.getMenu().slots) {
                    if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
                    String n = StringUtil.stripTrim(slot.getItem().getHoverName().getString());
                    if (n.isEmpty() || n.length() > 3) continue;
                    try {
                        ultraRound = Math.max(ultraRound, Integer.parseInt(n));
                    } catch (NumberFormatException ignored) {}
                }
                // Hypixel needs one round less than the lore says
                if (ultraRound > roundsNeeded - 1) maxReached(mc);
            }
            case "Superpairs" -> superpairs(mc, cs, c);
            default -> {}
        }
    }

    private static void onOpen(Screen s) {
        xpText = Map.of();
        stakes = false;
        rareFired = false;
        if (!(s instanceof AbstractContainerScreen<?>)) {
            type = null;
            return;
        }
        String title = StringUtil.stripTrim(s.getTitle().getString());
        if (title.equals("Experimentation Table")) {
            type = null;
            return;
        }
        if (title.endsWith("Stakes")) {
            stakes = true;
            type = null;
            return;
        }
        Matcher m = TYPE.matcher(title);
        if (m.matches()) {
            type = m.group(1);
            ultraRound = 0;
        } else {
            type = null;
        }
    }

    private static void readRoundsNeeded(AbstractContainerScreen<?> cs) {
        for (int i = 24; i >= 20; i--) {
            ItemStack st = containerItem(cs, i);
            if (st == null) continue;
            List<String> lore = ItemUtil.loreLines(st);
            boolean practice = false, skip = false;
            for (String l : lore) {
                if (l.contains("Practice mode has no rewards")) practice = true;
                if (l.contains("Enchanting level too low!") || l.contains("Not enough experience!")) skip = true;
            }
            if (practice) {
                roundsNeeded = -1;
                return;
            }
            if (skip) continue;
            for (int j = lore.size() - 1; j >= 0; j--) {
                Matcher m = ROUNDS_NEEDED.matcher(lore.get(j));
                if (m.find()) {
                    roundsNeeded = Integer.parseInt(m.group(1));
                    return;
                }
            }
        }
    }

    private static void maxReached(Minecraft mc) {
        roundsNeeded = -1;
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §eYou have reached the maximum extra Superpairs clicks from this add-on!"));
    }

    private static void superpairs(Minecraft mc, AbstractContainerScreen<?> cs, TeslaMapsConfig c) {
        Map<Integer, String> xp = c.expSuperpairsXp ? new HashMap<>() : Map.of();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            if (c.expUltraRareAlert && !rareFired) {
                List<String> lore = ItemUtil.loreLines(st);
                if (!lore.isEmpty() && lore.get(0).trim().equals("XX ULTRA-RARE BOOK! XX") && lore.size() > 2) {
                    rareFired = true;
                    rareName = lore.get(2).trim();
                    rareShownAt = System.currentTimeMillis();
                    if (mc.player != null) {
                        mc.player.playSound(SoundEvents.ENDER_DRAGON_GROWL, 1f, 1f);
                        mc.player.sendSystemMessage(Component.literal(
                                "§a[TeslaMaps] §7You have uncovered an §d§kXX§5 ULTRA-RARE BOOK! §d§kXX§7! You found: §9" + rareName));
                    }
                }
            }
            if (c.expSuperpairsXp && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("_dye")) {
                Matcher m = XP.matcher(StringUtil.stripTrim(st.getHoverName().getString()));
                if (m.find()) xp.put(slot.index, m.group(1) + "k");
            }
        }
        xpText = xp;
    }

    private static ItemStack containerItem(AbstractContainerScreen<?> cs, int index) {
        List<Slot> slots = cs.getMenu().slots;
        if (index >= slots.size()) return null;
        Slot s = slots.get(index);
        if (s.container instanceof Inventory || s.getItem().isEmpty()) return null;
        return s.getItem();
    }

    /** On top of the items, from ContainerOverlays. */
    public static void render(GuiGraphicsExtractor ctx) {
        if (feature == null || type == null || !"Superpairs".equals(type) || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        if (!xpText.isEmpty()) {
            for (Slot slot : cs.getMenu().slots) {
                String t = xpText.get(slot.index);
                if (t == null) continue;
                int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
                ctx.pose().pushMatrix();
                ctx.pose().translate(sx + 1, sy + 11);
                ctx.pose().scale(0.6f, 0.6f);
                ctx.text(mc.font, t, 0, 0, 0xFF55FFFF, true);
                ctx.pose().popMatrix();
            }
        }
        if (rareShownAt != 0 && System.currentTimeMillis() - rareShownAt < 2500) {
            String t = "§d§kXX§5 ULTRA-RARE BOOK! §d§kXX";
            ctx.pose().pushMatrix();
            ctx.pose().translate(cs.width / 2f, acc.getY() - 30);
            ctx.pose().scale(2f, 2f);
            ctx.text(mc.font, t, -mc.font.width(t) / 2, 0, 0xFFFFFFFF, true);
            ctx.pose().popMatrix();
        }
    }
}
