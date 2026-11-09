package com.teslamaps.features.garden;

// Ported from SkyHanni's JacobFarmingContestsInventory.kt + FarmingContestApi.kt, Skyblocker's JacobMedalBackground,
// GanKura's FarmingContestHandler (claim colours)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JacobContestMenus {

    public enum Bracket {
        DIAMOND('b', 0x55FFFF), PLATINUM('3', 0x00AAAA), GOLD('6', 0xFFAA00), SILVER('f', 0xFFFFFF), BRONZE('c', 0xFF5555);

        public final char code;
        public final int rgb;
        final Pattern pattern;

        Bracket(char code, int rgb) {
            this.code = code;
            this.rgb = rgb;
            this.pattern = Pattern.compile("^" + name() + " \\(Top \\d{1,2}%\\): ([\\d,]+)");
        }

        public String display() {
            return "§" + code + "§l" + name();
        }
    }

    public record Contest(GardenCrop crop, Map<Bracket, Integer> brackets) {}

    private static final Pattern YOUR_CONTESTS = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Your Contests");
    private static final Pattern DATE = Pattern.compile("^(.+) (\\d+)(?:st|nd|rd|th), Year (\\d+)$");
    private static final Pattern CROP = Pattern.compile("^(.+) Contest$");
    private static final Pattern MEDAL = Pattern.compile("^You placed in the (\\w+) bracket!$");
    private static final Pattern MONTH_MENU = Pattern.compile("^((?:\\w+ )?(?:Summer|Spring|Winter|Autumn)), Year (\\d+)$");
    private static final Pattern DAY = Pattern.compile("^Day (\\d+)$");
    private static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
            "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};
    private static final long EPOCH = 1559829300000L, YEAR_MS = 124 * 60 * 60 * 1000L, MONTH_MS = YEAR_MS / 12, DAY_MS = MONTH_MS / 31;
    private static final String ELITE = "https://eliteskyblock.com/";

    private static final Map<Long, Contest> contests = new HashMap<>();
    private static final int[] medal = new int[54];
    private static final byte[] finnegan = new byte[54];
    private static final byte[] status = new byte[54];
    private static boolean inMenu, anyMarks;
    private static int ticks;
    private static Feature feature;

    private JacobContestMenus() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("JacobContestMenus").section("Garden").when(() -> cfg().jacobContestMenus || cfg().jacobContestTimes || cfg().jacobContestFF)
                .tick(JacobContestMenus::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Contest Menu Helpers", () -> c.jacobContestMenus, v -> c.jacobContestMenus = v)
                            .desc("Extras for Jacob's \"Your Contests\" menu: medal per contest, reward status, real time in the tooltip and a website link.")
                            .tags("jacob", "contest", "medal", "reward", "elite", "eliteskyblock");
                    b.toggle("  Medal Icon (✦, ▲ for Finnegan)", () -> c.jacobMedalIcon, v -> c.jacobMedalIcon = v);
                    b.toggle("  Medal Slot Background", () -> c.jacobMedalBackground, v -> c.jacobMedalBackground = v);
                    b.toggle("  Reward Status Colours", () -> c.jacobRewardStatus, v -> c.jacobRewardStatus = v)
                            .desc("Claimed rewards green, unclaimed yellow");
                    b.toggle("  Real Time In Tooltip", () -> c.jacobRealTime, v -> c.jacobRealTime = v);
                    b.keybind("  Website Link Key", () -> c.jacobEliteKey, v -> c.jacobEliteKey = v)
                            .desc("Hold it and click a contest (or the Jacob / calendar menu items) to open it on eliteskyblock.com instead");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (inMenu && cfg().jacobContestMenus && cfg().jacobRealTime && feature.active()) onTooltip(stack, lines);
        });
    }

    public static Map<Long, Contest> contests() { return contests; }

    public static boolean inContestMenu() { return inMenu; }

    private static boolean isYourContests(AbstractContainerScreen<?> cs) {
        if (!YOUR_CONTESTS.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) return false;
        List<Slot> slots = cs.getMenu().slots;
        if (slots.size() <= 50) return false;
        List<String> lore = ItemUtil.loreLines(slots.get(50).getItem());
        return !lore.isEmpty() && lore.get(0).trim().equals("Claim multiple farming contest");
    }

    private static void clear() {
        inMenu = false;
        if (anyMarks) {
            java.util.Arrays.fill(medal, 0);
            java.util.Arrays.fill(finnegan, (byte) 0);
            java.util.Arrays.fill(status, (byte) 0);
            anyMarks = false;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) { clear(); return; }
        if (++ticks % 10 != 0) return;
        if (!isYourContests(cs)) { clear(); return; }
        inMenu = true;
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < Math.min(54, slots.size()); i++) {
            Slot slot = slots.get(i);
            medal[i] = 0;
            finnegan[i] = 0;
            status[i] = 0;
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            boolean contest = false;
            for (String l : lore) if (l.trim().startsWith("Your score: ")) { contest = true; break; }
            if (!contest) continue;
            long time = sbTime(StringUtil.stripTrim(stack.getHoverName().getString()));
            if (time > 0 && !contests.containsKey(time)) {
                Contest c = parse(lore);
                if (c != null) contests.put(time, c);
            }
            for (String raw : lore) {
                String l = raw.trim();
                if (l.contains("Contest boosted by Finnegan!")) finnegan[i] = 1;
                if (l.equals("Reward claimed!")) status[i] = 1;
                Matcher m = MEDAL.matcher(l);
                if (m.matches()) {
                    try { medal[i] = Bracket.valueOf(m.group(1)).ordinal() + 1; } catch (IllegalArgumentException ignored) {}
                }
            }
            if (!lore.isEmpty() && lore.get(lore.size() - 1).trim().equals("Click to claim reward!")) status[i] = 2;
            anyMarks = true;
        }
    }

    private static Contest parse(List<String> lore) {
        GardenCrop crop = null;
        Map<Bracket, Integer> brackets = new EnumMap<>(Bracket.class);
        for (String raw : lore) {
            String l = raw.trim();
            if (crop == null) {
                Matcher m = CROP.matcher(l);
                if (m.matches()) crop = GardenCrop.byName(m.group(1));
            }
            for (Bracket b : Bracket.values()) {
                Matcher m = b.pattern.matcher(l);
                if (m.find()) brackets.put(b, Integer.parseInt(m.group(1).replace(",", "")));
            }
        }
        return crop == null ? null : new Contest(crop, brackets);
    }

    private static int month(String name) {
        for (int i = 0; i < MONTHS.length; i++) if (MONTHS[i].equals(name)) return i + 1;
        return -1;
    }

    /** Real start time of a "Spring 31st, Year 494" contest, -1 when it is no date. */
    public static long sbTime(String name) {
        Matcher m = DATE.matcher(name);
        if (!m.matches()) return -1;
        int month = month(m.group(1));
        if (month < 0) return -1;
        return sbTime(Integer.parseInt(m.group(3)), month, Integer.parseInt(m.group(2)));
    }

    private static long sbTime(int year, int month, int day) {
        return EPOCH + year * YEAR_MS + (month - 1) * MONTH_MS + (day - 1) * DAY_MS;
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (lines.size() < 2) return;
        long time = sbTime(StringUtil.stripTrim(stack.getHoverName().getString()));
        if (time <= 0) return;
        SimpleDateFormat day = new SimpleDateFormat("dd MMMM yyyy", Locale.US), hm = new SimpleDateFormat("HH:mm", Locale.US);
        lines.add(1, Component.literal("§7" + day.format(new Date(time)) + " " + hm.format(new Date(time)) + "-"
                + hm.format(new Date(time + 20 * 60_000L))));
    }

    /** From SlotBackgrounds, drawn behind the item. */
    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (!inMenu || !anyMarks || slot.container instanceof Inventory || slot.index < 0 || slot.index >= 54) return;
        TeslaMapsConfig c = cfg();
        if (!c.jacobContestMenus || !feature.active()) return;
        int i = slot.index;
        if (c.jacobRewardStatus && status[i] != 0) {
            int col = status[i] == 1 ? 0x55FF55 : 0xFFFF55;
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x55000000 | col);
        } else if (c.jacobMedalBackground && medal[i] > 0) {
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x77000000 | Bracket.values()[medal[i] - 1].rgb);
        }
    }

    /** From SlotLockRenderMixin, on top of the items. */
    public static void render(GuiGraphicsExtractor ctx) {
        if (!inMenu || !anyMarks || !cfg().jacobContestMenus || !cfg().jacobMedalIcon || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < Math.min(54, slots.size()); i++) {
            if (medal[i] == 0) continue;
            Slot slot = slots.get(i);
            Bracket b = Bracket.values()[medal[i] - 1];
            boolean fin = finnegan[i] != 0;
            float x = acc.getX() + slot.x + (fin ? 5 : 9), y = acc.getY() + slot.y + (fin ? -2 : 1);
            float scale = fin ? 1.3f : 0.7f;
            ctx.pose().pushMatrix();
            ctx.pose().translate(x, y);
            ctx.pose().scale(scale, scale);
            ctx.text(mc.font, "§" + b.code + (fin ? "▲" : "✦"), 0, 0, 0xFFFFFFFF, true);
            ctx.pose().popMatrix();
        }
    }

    /** From HandledScreenClickMixin slotClicked. True cancels the click. */
    public static boolean onSlotClick(AbstractContainerScreen<?> cs, Slot slot) {
        if (slot == null || feature == null || !cfg().jacobContestMenus || !feature.active() || !KeyNames.isDown(cfg().jacobEliteKey)) return false;
        if (slot.container instanceof Inventory) return false;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return false;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        if (isYourContests(cs)) {
            Matcher m = DATE.matcher(name);
            if (!m.matches() || month(m.group(1)) < 0) return false;
            open("contests/" + m.group(3) + "/" + month(m.group(1)) + "/" + m.group(2), "contest");
            return true;
        }
        if (title.equals("Jacob's Farming Contests")) {
            switch (name) {
                case "Upcoming Contests" -> open("contests/upcoming", "upcoming contests");
                case "Claim your rewards!" -> {
                    Minecraft mc = Minecraft.getInstance();
                    String profile = SbProfile.name();
                    open("@" + mc.player.getName().getString() + (profile != null ? "/" + profile : "") + "/contests", "your contests");
                }
                case "What is this?" -> open("contests", "contest page");
                default -> { return false; }
            }
            return true;
        }
        Matcher month = MONTH_MENU.matcher(title);
        if (month.matches() && ItemUtil.loreContains(stack, "Jacob's Farming Contest")) {
            Matcher d = DAY.matcher(name);
            int mo = month(month.group(1));
            if (!d.matches() || mo < 0) return false;
            int year = Integer.parseInt(month.group(2)), day = Integer.parseInt(d.group(1));
            long time = sbTime(year, mo, day);
            if (time < System.currentTimeMillis()) open("contests/" + year + "/" + mo + "/" + day, "contest");
            else open("contests/upcoming#" + time / 1000, "upcoming contests");
            return true;
        }
        return false;
    }

    private static void open(String path, String what) {
        Minecraft mc = Minecraft.getInstance();
        try {
            com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(ELITE + path));
        } catch (Exception ignored) {
        }
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fOpening " + what + " on eliteskyblock.com"));
    }
}
