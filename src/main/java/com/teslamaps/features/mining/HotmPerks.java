package com.teslamaps.features.mining;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Heart of the Mountain: perk tint, perk level, tokens and the Sky Mall readout. */
public class HotmPerks {

    private static final Pattern NOT_UNLOCKED = Pattern.compile("Requires.*|.*Mountain!|Click to unlock!");
    private static final Pattern TOKENS = Pattern.compile("Tokens? of the Mountain: (\\d+)");
    private static final Pattern BUFF = Pattern.compile(" ?(?:New buff: |■ )?(.*)");
    private static final int NONE = 0, ENABLED = 1, DISABLED = 2;

    // what one slot shows, built once per (immutable) lore component
    private record Info(int state, String tip) {}
    private static final Info EMPTY = new Info(NONE, null);

    private static final String[] SAMPLE = {"§bSky Mall§8: §6+100 Mining Speed"};

    private static Feature feature, skyMall;
    private static Screen cachedScreen;
    private static boolean cachedIn;
    private static final Map<ItemLore, Info> cache = new java.util.IdentityHashMap<>();

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HotmPerks").section("Mining")
                .when(() -> cfg().hotmHighlight || cfg().hotmLevelStack || cfg().hotmTokenStack)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("HotM Menu");
                    b.toggle("HotM Perk Highlight", () -> c.hotmHighlight, v -> c.hotmHighlight = v)
                            .desc("Tints enabled and disabled perks in the Heart of the Mountain menu.");
                    b.color("  Enabled Color", () -> c.colorHotmEnabled, v -> c.colorHotmEnabled = v);
                    b.color("  Disabled Color", () -> c.colorHotmDisabled, v -> c.colorHotmDisabled = v);
                    b.toggle("HotM Tokens On Heart Item", () -> c.hotmTokenStack, v -> c.hotmTokenStack = v)
                            .desc("Unused Tokens of the Mountain on the heart item.");
                    b.toggle("Sky Mall Display", () -> c.hotmSkyMallDisplay, v -> c.hotmSkyMallDisplay = v)
                            .desc("HUD line with today's Sky Mall buff, read from chat and the HotM menu.")
                            .tags("sky mall", "skymall", "hotm");
                    b.toggle("  Only On Mining Islands", () -> c.hotmSkyMallMiningOnly, v -> c.hotmSkyMallMiningOnly = v);
                    HotmPowderGoal.config(b);
                    b.toggle("HotM Perk Level", () -> c.hotmLevelStack, v -> c.hotmLevelStack = v)
                            .desc("Perk level in the bottom right of each slot in the Heart of the Mountain menu, gold at max level.")
                            .tags("hotm", "perk", "level", "slot text");
                });
        skyMall = Feature.of("HotmSkyMall").section("Mining").when(() -> cfg().hotmSkyMallDisplay)
                .hud("hotmskymalldisplay", HotmPerks::renderSkyMall);
        Feature.of("SkyMallTracker").section("Mining").when(HotmPerks::trackSkyMall)
                .serverChat(HotmPerks::onChat);
        HudEditElement.of("hotmSkyMallDisplay", "Sky Mall").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().hotmSkyMallDisplay)
                .size(() -> previewSize()[0], () -> previewSize()[1])
                .preview(HotmPerks::draw);
    }

    private static final Pattern LEVEL = Pattern.compile("Level (\\d+)(/\\d+)?");
    private static final java.util.Map<ItemLore, String> levelCache = new java.util.IdentityHashMap<>();

    /** From LoreSlotTextMixin. */
    public static void drawLevel(GuiGraphicsExtractor ctx, Slot slot) {
        if (feature == null || !TeslaMapsConfig.get().hotmLevelStack || !inMenu() || !feature.active()) return;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory || slot.index > 44) return;
        ItemStack item = slot.getItem();
        if (item.isEmpty() || item.is(net.minecraft.world.item.Items.COAL)) return;
        ItemLore raw = item.get(DataComponents.LORE);
        if (raw == null) return;
        String text = levelCache.get(raw);
        if (text == null) {
            text = "";
            if (!raw.lines().isEmpty()) {
                java.util.regex.Matcher m = LEVEL.matcher(StringUtil.stripTrim(raw.lines().get(0).getString()));
                if (m.matches()) text = (m.group(2) == null ? "§6" : "§e") + m.group(1);
            }
            if (levelCache.size() > 256) levelCache.clear();
            levelCache.put(raw, text);
        }
        if (text.isEmpty() || !com.teslamaps.features.SlotText.shown(com.teslamaps.features.SlotText.HOTM_LEVEL)) return;
        com.teslamaps.features.SlotText.draw(ctx, Minecraft.getInstance().font, text, slot.x, slot.y, 3, 0xFFFFFFFF);
    }

    private static boolean inMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != cachedScreen) {
            cachedScreen = mc.gui.screen();
            cache.clear();
            cachedIn = mc.gui.screen() instanceof AbstractContainerScreen<?> cs
                    && StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Heart of the Mountain");
        }
        return cachedIn;
    }

    public static boolean shouldRender() {
        TeslaMapsConfig c = cfg();
        return feature != null && (c.hotmTokenStack || trackSkyMall())
                && inMenu() && c.section("Mining");
    }

    private static Info info(ItemStack item) {
        ItemLore raw = item.get(DataComponents.LORE);
        if (raw == null) return EMPTY;
        Info info = cache.get(raw);
        if (info == null) {
            info = build(item);
            if (cache.size() > 512) cache.clear();
            cache.put(raw, info);
        }
        return info;
    }

    public static void drawHighlight(GuiGraphicsExtractor ctx, Slot slot) {
        if (feature == null || !cfg().hotmHighlight || !inMenu() || !feature.active()) return;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) return;
        ItemStack item = slot.getItem();
        if (item.isEmpty()) return;
        int state = info(item).state();
        if (state == NONE) return;
        TeslaMapsConfig c = cfg();
        String hex = state == ENABLED ? c.colorHotmEnabled : c.colorHotmDisabled;
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF000000 | (TeslaMapsConfig.parseColor(hex) & 0xFFFFFF));
    }

    /** Level / token stack text, drawn over the container. */
    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            String tip = info(item).tip();
            if (tip == null) continue;
            int w = mc.font.width(tip);
            ctx.text(mc.font, tip, acc.getX() + slot.x + 17 - w, acc.getY() + slot.y + 9, 0xFFFFFFFF);
        }
    }

    private static Info build(ItemStack item) {
        TeslaMapsConfig c = cfg();
        String name = StringUtil.stripTrim(item.getHoverName().getString());
        List<String> lore = ItemUtil.loreLines(item);
        if (lore.isEmpty()) return EMPTY;
        if (name.startsWith("Heart of the Mountain")) {
            if (!c.hotmTokenStack) return EMPTY;
            for (String line : lore) {
                Matcher m = TOKENS.matcher(line.trim());
                if (m.matches() && !m.group(1).equals("0")) return new Info(NONE, "§b" + m.group(1));
            }
            return EMPTY;
        }
        if (name.startsWith("Reset Heart of the Mountain")) return EMPTY;
        if (name.equals("Sky Mall")) readSkyMall(lore);
        if (NOT_UNLOCKED.matcher(lore.get(lore.size() - 1).trim()).matches()) return EMPTY;

        boolean enabled = false, disabled = false;
        for (String line : lore) {
            String l = line.trim();
            if (l.equals("ENABLED") || l.equals("SELECTED")) enabled = true;
            else if (l.equals("DISABLED")) disabled = true;
        }
        int state = enabled ? ENABLED : disabled ? DISABLED : NONE;

        return state == NONE ? EMPTY : new Info(state, null);
    }

    // the Sky Mall item lists today's buff under "Your Current Effect"
    private static void readSkyMall(List<String> lore) {
        int index = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).trim().equals("Your Current Effect")) { index = i; break; }
        }
        if (index < 0) return;
        StringBuilder wrapped = new StringBuilder();
        for (int i = index + 1; i < lore.size(); i++) {
            String line = lore.get(i).trim();
            if (line.isEmpty()) break;
            if (!wrapped.isEmpty()) wrapped.append(' ');
            wrapped.append(line);
        }
        String display = skyMallDisplay(wrapped.toString());
        if (display != null) setSkyMall(display);
    }

    private static final long YEAR_0 = 1560275700000L, DAY_MS = 1_200_000L;
    private static long newDayAt;

    private static boolean trackSkyMall() {
        TeslaMapsConfig c = cfg();
        return c.hotmSkyMallDisplay;
    }

    private static long sbDay(long now) {
        return Math.floorDiv(now - YEAR_0, DAY_MS);
    }

    /** Today's Sky Mall buff, null when unknown or from an earlier SkyBlock day. */
    public static String skyMallToday() {
        TeslaMapsConfig c = cfg();
        return c.hotmSkyMallPerk.isEmpty() || c.skyMallDay != sbDay(System.currentTimeMillis()) ? null : c.hotmSkyMallPerk;
    }

    private static void setSkyMall(String display) {
        TeslaMapsConfig c = cfg();
        long day = sbDay(System.currentTimeMillis());
        if (display.equals(c.hotmSkyMallPerk) && c.skyMallDay == day) return;
        c.hotmSkyMallPerk = display;
        c.skyMallDay = day;
        TeslaMapsConfig.save();
    }

    private static void onChat(String message) {
        String t = StringUtil.strip(message).trim();
        if (t.equals("New day! Your Sky Mall buff changed!")) {
            newDayAt = System.currentTimeMillis();
            return;
        }
        if (!t.startsWith("New buff: ")) return;
        String display = skyMallDisplay(t);
        if (display == null && System.currentTimeMillis() - newDayAt < 3000) {
            String perk = t.substring(10).trim();
            if (perk.startsWith("Gain ")) perk = perk.substring(5);
            if (perk.endsWith(".")) perk = perk.substring(0, perk.length() - 1);
            display = "§a" + perk;
        }
        if (display != null) setSkyMall(display);
    }

    // Ported from SkyHanni's HotmApi.SkymallPerk
    private static String skyMallDisplay(String text) {
        Matcher m = BUFF.matcher(text);
        if (!m.matches()) return null;
        String e = m.group(1);
        if (e.contains("Mining Speed") && e.startsWith("Gain")) return "§6+100 Mining Speed";
        if (e.contains("Mining Fortune") && e.startsWith("Gain") && !e.contains("Mangrove") && !e.contains("Fig")
                && !e.contains("Helix")) return "§6+50 Mining Fortune";
        if (e.contains("more Powder while mining")) return "§a+15% §7more Powder";
        if (e.contains("Pickaxe Ability cooldown")) return "§a-20% §7Pickaxe Ability cooldowns";
        if (e.contains("Golden and Diamond Goblins") || e.startsWith("10x chance to find Golden"))
            return "§a10x §6Gold §7& §bDiamond §7Goblin chance";
        if (e.contains("5x Titanium drops")) return "§a5x §9Titanium §7drops";
        return null;
    }

    private static void renderSkyMall(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        TeslaMapsConfig c = cfg();
        if (com.teslamaps.utils.HudVisibility.suppressed()) return;
        if (c.hotmSkyMallMiningOnly && !Mining.onMiningIsland()) return;
        String line = line();
        if (line == null) return;
        draw(ctx, Minecraft.getInstance(), c.hotmSkyMallDisplayX, c.hotmSkyMallDisplayY, c.hotmSkyMallDisplayScale, line);
    }

    private static String line() {
        String perk = skyMallToday();
        return perk == null ? null : "§bSky Mall§8: " + perk;
    }

    public static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        String line = line();
        draw(ctx, mc, x, y, scale, line == null ? SAMPLE[0] : line);
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, String line) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        ctx.text(mc.font, line, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }

    private static int[] previewSize() {
        Minecraft mc = Minecraft.getInstance();
        String line = line();
        return new int[] {mc.font.width(line == null ? SAMPLE[0] : line), mc.font.lineHeight};
    }
}
