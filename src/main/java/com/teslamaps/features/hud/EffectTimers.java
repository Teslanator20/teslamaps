package com.teslamaps.features.hud;

// Ported from SkyHanni's NonGodPotEffectDisplay + EffectApi (effect list, chat, tab, /effects), footer parsing after Skyblocker's EffectWidget
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.Notifications;
import com.teslamaps.features.ScreenFlash;
import com.teslamaps.mixin.PlayerTabOverlayAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EffectTimers {

    private static final long S = 1000, M = 60 * S, H = 60 * M;

    /** tab/inventory name, display, gained message, removed message, duration, add instead of set, mixin. */
    private record Effect(String tab, String inv, String display, String gained, String removed, long ms, boolean add, boolean mixin) {}

    private static Effect e(String tab, String display, String gained, long ms, boolean add) {
        return new Effect(tab, tab, display, gained, null, ms, add, false);
    }

    private static Effect mixin(String tab, String display) {
        return new Effect(tab, tab, display, null, null, 0, false, true);
    }

    private static Effect plain(String tab, String display) {
        return new Effect(tab, tab, display, null, null, 0, false, false);
    }

    private static final Effect GOD_POT = new Effect("God Potion", "God Potion", "§cGod Potion", null, null, 0, false, false);
    private static final Effect HOT_CHOCOLATE = mixin("Hot Chocolate Mixin I", "§6Hot Chocolate Mixin I");

    private static final List<Effect> EFFECTS = List.of(
            e("Smoldering Polarization I", "§aSmoldering Polarization I", "You ate a Re-heated Gummy Polar Bear!", H, true),
            e("Mushed Glowy Tonic I", "§2Mushed Glowy Tonic I", "BUFF! You have gained Mushed Glowy Tonic I!", H, false),
            e("Wisp's Ice-Flavored Water I", "§bWisp's Ice-Flavored Water I", "BUFF! You splashed yourself with Wisp's Ice-Flavored Water I!", 5 * M, false),
            new Effect("King's Scent I", "King's Scent I", "§2King's Scent I", "[NPC] King Yolkar: This egg will help me stomach my pain.",
                    "The Goblin King's foul stench has dissipated!", 20 * M, false, false),
            mixin("Zombie Brain Mixin", "§cZombie Brain Mixin"),
            mixin("Spider Egg Mixin", "§6Spider Egg Mixin"),
            mixin("Wolf Fur Mixin", "§bWolf Fur Mixin"),
            mixin("End Portal Fumes", "§6End Portal Fumes"),
            mixin("Gabagoey", "§fGabagoey"),
            mixin("Glowing Mush Mixin", "§2Glowing Mush Mixin"),
            HOT_CHOCOLATE,
            mixin("Celestial Mason Jar I", "§dCelestial Mason Jar Mixin"),
            mixin("Hotspot Tonic", "§2Hotspot Tonic Mixin"),
            mixin("Melon Juice Mixin I", "§fMelon Juice Mixin"),
            mixin("Deepterror", "§4Deepterror"),
            new Effect("Great Spook I", "Great Spook Potion", "§fGreat Spook I", "You consumed a Great Spook Potion!", null, 24 * H, false, false),
            e("Douce Pluie de Stinky Cheese I", "§eDouce Pluie de Stinky Cheese I", "BUFF! You have gained Douce Pluie de Stinky Cheese I!", H, false),
            e("Harvest Harbinger V", "§6Harvest Harbinger V", "BUFF! You have gained Harvest Harbinger V!", 25 * M, false),
            plain("Pest Repellent I", "§6Pest Repellent I"),
            plain("Pest Repellent II", "§6Pest Repellent II"),
            plain("Curse of Greed I", "§4Curse of Greed I"),
            plain("Cold Resistance IV", "§bCold Resistance IV"),
            plain("Powder Pumpkin I", "§fPowder Pumpkin I"),
            plain("Filet O' Fortune I", "§fFilet O' Fortune I"),
            plain("Chilled Pristine Potato I", "§fChilled Pristine Potato I"),
            plain("Exalted Lushlilac Bonbon", "§5Exalted Lushlilac Bonbon"),
            plain("Prime Lushlilac Bonbon", "§5Prime Lushlilac Bonbon"),
            plain("Lushlilac Bonbon", "§5Lushlilac Bonbon"),
            plain("Oceandy", "§5Oceandy"),
            plain("Candycomb", "§5Candycomb"),
            plain("Brine Tonic", "§5Brine Tonic"));

    private static final Pattern TIME_PART = Pattern.compile("(\\d+)\\s*([wdhms])");
    private static final Pattern TIME_ONLY = Pattern.compile("^[:\\s]*((?:\\d+\\s*[wdhms]\\s*)+)$");
    private static final Pattern NON_GOD_COUNT = Pattern.compile("You have (\\d+) non-god effects\\.");
    private static final Pattern GOD_TAB = Pattern.compile("God Potion: ([dhms0-9 ]+)");
    private static final Pattern GOD_CONSUME = Pattern.compile(".*God Potion grants you powers for ([dhms0-9 ]*)!.*");
    private static final Pattern HOT_CHOC = Pattern.compile(".*Hot Chocolate Mixin have been extended by ([dhms0-9 ]*)!.*");
    private static final Pattern REPELLENT = Pattern.compile("Repellent: (\\w+) \\(([dhms0-9 ]+)\\)");
    private static final Pattern INV_TITLE = Pattern.compile("(?:\\(\\d+/\\d+\\) )?Active Effects");
    private static final Pattern REMAINING = Pattern.compile("Remaining: ([\\d:]+)");
    private static final List<String> SAMPLE = List.of("§dCookie: §a3d 4h", "§cGod Potion §a1d 2h",
            "§aSmoldering Polarization I §e42m 10s", "§bWisp's Ice-Flavored Water I §c38s");

    private static final Map<Effect, Long> expiry = new HashMap<>();
    private static final Map<Effect, Long> setRecently = new HashMap<>();
    private static final java.util.Set<Effect> warned = new java.util.HashSet<>();
    private static String cookie = null;
    private static int nonGodCount;
    private static List<String> lines = List.of();
    private static int width;
    private static int timer;
    private static HudEditElement hud;
    private static Object lastScreen;
    private static long openedAt;

    private EffectTimers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("effectTimers", "Effect Timers")
                .enabled(() -> cfg().effectTimers)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        TextHud.width(lines.isEmpty() ? SAMPLE : lines)));
        Feature.of("EffectTimers").when(() -> cfg().effectTimers)
                .tick(EffectTimers::tick)
                .serverChat(EffectTimers::onChat)
                .hudHiddenInInventory("effecttimers", EffectTimers::render)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Effect Timers");
                    b.toggle("Effect Timers", () -> c.effectTimers, v -> c.effectTimers = v)
                            .desc("Booster Cookie, God Potion and the buffs a God Potion does not include, with time left. Open /effects once to fill in missing ones. Position in /tmap gui");
                    b.toggle("  Show Cookie", () -> c.effectTimersCookie, v -> c.effectTimersCookie = v);
                    b.toggle("  Show God Potion", () -> c.effectTimersGodPot, v -> c.effectTimersGodPot = v);
                    b.toggle("  Show Mixins", () -> c.effectTimersMixins, v -> c.effectTimersMixins = v);
                    b.toggle("  Expire Warning", () -> c.effectTimersWarn, v -> c.effectTimersWarn = v)
                            .desc("Title and chat line when a buff is about to run out");
                    b.toggle("  Warning Sound", () -> c.effectTimersWarnSound, v -> c.effectTimersWarnSound = v);
                    b.slider("  Warn At (s)", 5, 300, () -> (float) c.effectTimersWarnSeconds, v -> c.effectTimersWarnSeconds = Math.round(v));
                });
        com.teslamaps.utils.SbProfile.onChange(() -> {
            expiry.clear();
            cookie = null;
        });
    }

    private static boolean hidden() {
        if (!SkyblockUtils.isInSkyblock() || DungeonManager.isInDungeon()) return true;
        String area = SkyblockUtils.getArea();
        return area.contains("Kuudra") || area.equals("The Rift");
    }

    static long parseTime(String s) {
        Matcher m = TIME_PART.matcher(s);
        long ms = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += switch (m.group(2)) {
                case "w" -> n * 7 * 24 * H;
                case "d" -> n * 24 * H;
                case "h" -> n * H;
                case "m" -> n * M;
                default -> n * S;
            };
        }
        return ms;
    }

    private static long parseClock(String s) {
        long ms = 0;
        for (String p : s.split(":")) {
            try { ms = ms * 60 + Long.parseLong(p); } catch (NumberFormatException e) { return -1; }
        }
        return ms * S;
    }

    private static void set(Effect e, long ms) {
        if (ms <= 0) return;
        expiry.put(e, System.currentTimeMillis() + ms);
    }

    /** Tab time is truncated (1h 20m = [1h20m, 1h21m)): keep the ticking estimate when it fits. */
    private static void partial(Effect e, long ms) {
        if (ms <= 0) return;
        long now = System.currentTimeMillis();
        Long old = expiry.get(e);
        if (old == null || old <= now) {
            expiry.put(e, now + ms);
            return;
        }
        long left = old - now;
        long upper;
        if (ms % M != 0) upper = ms + S;
        else if (ms % H != 0) upper = ms + M;
        else if (ms % (24 * H) != 0) upper = ms + H;
        else upper = ms + 24 * H;
        if (left < ms) expiry.put(e, now + ms);
        else if (left >= upper) expiry.put(e, now + upper - S);
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.equals("You cleared all of your active effects!")) {
            expiry.clear();
            return;
        }
        Matcher m = GOD_CONSUME.matcher(t);
        if (m.matches()) {
            long now = System.currentTimeMillis();
            Long old = expiry.get(GOD_POT);
            expiry.put(GOD_POT, (old == null || old < now ? now : old) + parseTime(m.group(1)));
            return;
        }
        m = HOT_CHOC.matcher(t);
        if (m.matches()) {
            long now = System.currentTimeMillis();
            Long old = expiry.get(HOT_CHOCOLATE);
            expiry.put(HOT_CHOCOLATE, (old == null || old < now ? now : old) + parseTime(m.group(1)));
            return;
        }
        t = t.replace(" Press TAB or type /effects to view your active effects!", "").trim();
        for (Effect e : EFFECTS) {
            if (e.removed != null && t.equals(e.removed)) {
                expiry.remove(e);
                return;
            }
            if (e.gained == null || !t.equals(e.gained)) continue;
            long now = System.currentTimeMillis();
            if (e.add) {
                Long recent = setRecently.get(e);
                if (recent != null && now - recent < 5000) return;
                Long old = expiry.get(e);
                expiry.put(e, (old == null || old < now ? now : old) + e.ms);
            } else {
                expiry.put(e, now + e.ms);
                setRecently.put(e, now);
            }
            return;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            if (mc.screen != lastScreen) {
                lastScreen = mc.screen;
                openedAt = System.currentTimeMillis();
            }
            if (timer % 10 == 0 && System.currentTimeMillis() - openedAt > 500
                    && INV_TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).matches()) readMenu(cs);
        } else lastScreen = null;

        if (timer++ % 20 != 0) return;
        if (hidden()) {
            lines = List.of();
            return;
        }
        readTab(mc);
        warn(mc);
        build(mc);
    }

    private static void readMenu(AbstractContainerScreen<?> cs) {
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            Effect hit = null;
            for (Effect e : EFFECTS) {
                if (name.contains(e.inv)) { hit = e; break; }
            }
            if (hit == null) continue;
            for (String l : ItemUtil.loreLines(st)) {
                Matcher m = REMAINING.matcher(l.trim());
                if (m.find()) {
                    long ms = parseClock(m.group(1));
                    if (ms > 0) set(hit, ms);
                    break;
                }
            }
        }
    }

    private static void readTab(Minecraft mc) {
        List<String> src = new ArrayList<>();
        Component footer = ((PlayerTabOverlayAccessor) mc.gui.getTabList()).teslamaps$getFooter();
        String[] foot = footer == null ? new String[0] : StringUtil.strip(footer.getString()).split("\n");
        cookie = null;
        for (int i = 0; i < foot.length; i++) {
            String l = foot[i].trim();
            if (l.equals("Cookie Buff") && i + 1 < foot.length) {
                String next = foot[i + 1].trim();
                cookie = next.startsWith("Not active") ? "§cNot active" : "§a" + next;
            }
            Matcher c = NON_GOD_COUNT.matcher(l);
            if (c.find()) nonGodCount = Integer.parseInt(c.group(1));
            src.add(foot[i]);
        }
        for (String l : TabListUtils.getTabListLines()) src.add(StringUtil.strip(l));

        for (String raw : src) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            Matcher g = GOD_TAB.matcher(l);
            if (g.find()) {
                partial(GOD_POT, parseTime(g.group(1)));
                continue;
            }
            Matcher r = REPELLENT.matcher(l);
            if (r.find()) {
                String tier = r.group(1).toUpperCase(java.util.Locale.ROOT);
                String name = tier.equals("MAX") ? "Pest Repellent II" : tier.equals("REGULAR") ? "Pest Repellent I" : null;
                if (name != null) for (Effect e : EFFECTS) if (e.tab.equals(name)) partial(e, parseTime(r.group(2)));
                continue;
            }
            for (Effect e : EFFECTS) {
                if (!l.startsWith(e.tab)) continue;
                Matcher t = TIME_ONLY.matcher(l.substring(e.tab.length()));
                if (!t.matches()) continue;
                partial(e, parseTime(t.group(1)));
                break;
            }
        }
    }

    private static void warn(Minecraft mc) {
        TeslaMapsConfig c = cfg();
        if (!c.effectTimersWarn && !c.effectTimersWarnSound) return;
        long now = System.currentTimeMillis();
        long at = c.effectTimersWarnSeconds;
        for (Map.Entry<Effect, Long> en : expiry.entrySet()) {
            Effect e = en.getKey();
            if (e.mixin && !c.effectTimersMixins) continue;
            long left = (en.getValue() - now) / 1000;
            if (left > at || left < 0) {
                warned.remove(e);
                continue;
            }
            if (left < at - 3 || !warned.add(e)) continue;
            if (c.effectTimersWarn) {
                mc.gui.setTimes(0, 40, 10);
                mc.gui.setTitle(Component.literal(e.display));
                mc.gui.setSubtitle(Component.literal("§erunning out soon!"));
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + e.display + " §eis running out soon!"));
                Notifications.show(e.display + " §eis running out in " + at + "s!", 15);
            }
            if (c.effectTimersWarnSound) LoudSound.playStacked(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f, 5);
            ScreenFlash.alert(c.screenFlashEffects);
        }
    }

    private static void build(Minecraft mc) {
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        if (c.effectTimersCookie && cookie != null) out.add("§dCookie: " + cookie);
        Long god = expiry.get(GOD_POT);
        if (c.effectTimersGodPot && god != null && god > now) out.add("§cGod Potion " + color(god - now) + format(god - now));
        List<Map.Entry<Effect, Long>> list = new ArrayList<>();
        for (Map.Entry<Effect, Long> en : expiry.entrySet()) {
            Effect e = en.getKey();
            if (e == GOD_POT || en.getValue() <= now) continue;
            if (e.mixin && !c.effectTimersMixins) continue;
            list.add(en);
        }
        expiry.values().removeIf(v -> v <= now - 5000);
        list.sort(Map.Entry.comparingByValue());
        for (Map.Entry<Effect, Long> en : list) {
            long left = en.getValue() - now;
            out.add(en.getKey().display + " " + color(left) + format(left));
        }
        int tracked = 0;
        for (Effect e : expiry.keySet()) if (e != GOD_POT && expiry.get(e) > now) tracked++;
        int diff = nonGodCount - tracked;
        if (diff > 0) {
            out.add("§eOpen /effects to show");
            out.add("§ethe missing " + diff + " effect" + (diff == 1 ? "" : "s"));
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static String color(long ms) {
        if (ms < M) return "§c";
        if (ms < 10 * M) return "§6";
        if (ms < H) return "§e";
        return "§a";
    }

    private static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 86400) return s / 86400 + "d " + s / 3600 % 24 + "h";
        if (s >= 3600) return s / 3600 + "h " + s / 60 % 60 + "m";
        if (s >= 60) return s / 60 + "m " + s % 60 + "s";
        return s + "s";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty()) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }
}
