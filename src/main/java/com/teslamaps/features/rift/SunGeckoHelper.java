package com.teslamaps.features.rift;

// After SkyHanni's SunGeckoHelper.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SunGeckoHelper {

    private static final String[] MODS = {"Revival", "Combo Manic", "Time Sliced", "Buffantics", "Collective",
            "Brand New Dance", "Culmination"};
    private static final int REVIVAL = 0, TIME_SLICED = 2, COLLECTIVE = 4, CULMINATION = 6;
    private static final Pattern HP = Pattern.compile("([\\d,.]+)/([\\d,.]+)❤");
    private static final Pattern BAR = Pattern.compile("\\[(.*) x(\\d+) (.*)]");
    private static final Pattern DUR = Pattern.compile("(\\d+)([hms])");
    private static final String[] SAMPLE = {"§eSun Gecko §a200§f/§a250§c❤", "§a4/10 §e§lCombo: x2",
            "§aCombo Timer: 3.20s/4.7s", "§6Modifiers:", "§eNone"};

    private static final boolean[] mods = new boolean[MODS.length];
    private static String[] lines = new String[0];
    private static int w;
    private static boolean inChamber, scanning, firstPhase = true, comboMania;
    private static int healthLeft = 250, totalHealth = 250, combo = 1, ticks;
    private static long lastHit, sliceSeconds;
    private static String hits = "";
    private static LivingEntity boss;

    private SunGeckoHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        HudEditElement.of("riftSunGecko", "Sun Gecko Helper").section("Rift")
                .enabled(() -> cfg().riftSunGecko)
                .size(() -> lines.length > 0 ? w : Minecraft.getInstance().font.width(SAMPLE[2]),
                        () -> RiftSpot.height(lines.length > 0 ? lines : SAMPLE, null))
                .preview((g, mc, x, y, s) -> {
                    if (lines.length > 0) RiftSpot.drawLines(g, mc, x, y, s, lines, null, w);
                    else RiftSpot.drawLines(g, mc, x, y, s, SAMPLE, null, mc.font.width(SAMPLE[2]));
                });
        Feature.of("SunGeckoHelper").section("Rift").when(() -> cfg().riftSunGecko)
                .tick(SunGeckoHelper::tick)
                .serverChat(SunGeckoHelper::onChat)
                .hud("riftsungecko", SunGeckoHelper::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Sun Gecko Helper", () -> c.riftSunGecko, v -> c.riftSunGecko = v)
                            .desc("Time Chamber: HUD with Sun Gecko HP, combo and combo timer. Move it in /tmap gui.")
                            .tags("rift", "sun gecko", "time chamber", "mountaintop", "combo");
                    b.toggle("  Show Modifiers", () -> c.riftSunGeckoModifiers, v -> c.riftSunGeckoModifiers = v)
                            .desc("Active modifiers, read from chat, the Modifiers menu and the scoreboard");
                    b.toggle("  Highlight Real Boss", () -> c.riftSunGeckoRealGlow, v -> c.riftSunGeckoRealGlow = v)
                            .desc("Green glow on the real Sun Gecko").legit();
                    b.toggle("  Highlight Clones", () -> c.riftSunGeckoFakeGlow, v -> c.riftSunGeckoFakeGlow = v)
                            .desc("Red glow on the fake ones (name with ?)").legit();
                });
    }

    private static void reset() {
        lines = new String[0];
        healthLeft = 250;
        totalHealth = 250;
        firstPhase = true;
        sliceSeconds = 0;
        lastHit = 0;
        combo = 1;
        hits = "";
        boss = null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean chamber = RiftSpot.inRift() && RiftSpot.sub().equals("Time Chamber");
        if (chamber != inChamber) {
            inChamber = chamber;
            reset();
        }
        if (ticks++ % 10 == 0) readMenu(mc);
        if (!inChamber) return;
        TeslaMapsConfig c = cfg();

        if (ticks % 20 == 0) {
            comboMania = false;
            for (int i = 0; i < 36; i++) {
                if (ItemUtil.skyblockId(mc.player.getInventory().getItem(i)).equals("COMBO_MANIA_TALISMAN")) { comboMania = true; break; }
            }
            for (String line : ScoreboardUtils.getScoreboardLines()) {
                String s = ScoreboardUtils.cleanLine(line);
                if (s.startsWith("Big damage in:")) {
                    mods[TIME_SLICED] = true;
                    sliceSeconds = seconds(s.substring(14));
                }
            }
        }

        boolean glow = !LegitMode.blocksCheats();
        LivingEntity real = null;
        int realHp = -1, realMax = -1;
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            if (as.distanceToSqr(mc.player) > 64 * 64) continue;
            String name = NametagMobs.tagName(as);
            if (name == null || !name.contains("Sun Gecko")) continue;
            LivingEntity mob = NametagMobs.mobForTag(as);
            if (name.contains("?")) {
                if (glow && mob != null && c.riftSunGeckoFakeGlow) EntityGlow.mark(mob, 0xFF5555);
                continue;
            }
            if (glow && mob != null && c.riftSunGeckoRealGlow) EntityGlow.mark(mob, 0x55FF55);
            if (real == null || mob == boss) {
                real = mob;
                Matcher m = HP.matcher(name);
                if (m.find()) {
                    realHp = parse(m.group(1));
                    realMax = parse(m.group(2));
                }
            }
        }
        if (real != null) boss = real;
        if (boss != null && !boss.isAlive()) boss = null;
        if (boss != null) {
            int hp = realHp >= 0 ? realHp : (int) boss.getHealth();
            int max = realMax > 0 ? realMax : (int) boss.getMaxHealth();
            if (hp > healthLeft) {
                firstPhase = false;
                mods[REVIVAL] = true;
            }
            healthLeft = hp;
            totalHealth = max;
        }
        build(mc);
    }

    private static void build(Minecraft mc) {
        int hp = healthLeft, total = totalHealth;
        if (mods[REVIVAL]) {
            if (firstPhase) hp += totalHealth;
            total *= 2;
        }
        List<String> out = new ArrayList<>();
        out.add("§eSun Gecko " + (hp <= total / 2 ? "§e" : "§a") + hp + "§f/§a" + total + "§c❤");
        out.add(hits + " §e§lCombo: x" + combo);
        long expiry = 4700;
        if (comboMania) expiry += 500;
        int active = 0;
        for (boolean b : mods) if (b) active++;
        if (mods[COLLECTIVE]) expiry += active * 200L;
        long left = lastHit + expiry - System.currentTimeMillis();
        String max = String.format(Locale.US, "%.1fs", expiry / 1000.0);
        if (left > expiry - 800 || left <= 0) out.add("§aCombo Timer: " + max + "/" + max);
        else out.add(String.format(Locale.US, "§aCombo Timer: %.2fs/%s", left / 1000.0, max));
        if (cfg().riftSunGeckoModifiers) {
            out.add("§6Modifiers:");
            if (active == 0) out.add("§eNone");
            for (int i = 0; i < MODS.length; i++) if (mods[i]) out.add("§e" + MODS[i]);
        }
        String[] l = out.toArray(new String[0]);
        w = RiftSpot.width(mc, l, null);
        lines = l;
    }

    private static void readMenu(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Modifiers")) return;
        var slots = cs.getMenu().slots;
        if (slots.size() < 26) return;
        for (int i = 0; i < MODS.length; i++) {
            ItemStack st = slots.get(19 + i).getItem();
            mods[i] = st.is(Items.STAINED_GLASS.lime()) || st.is(Items.STAINED_GLASS_PANE.lime());
        }
    }

    /** From InGameHudMixin, main thread. */
    public static void onActionBar(Component message) {
        if (!inChamber || !cfg().riftSunGecko || !cfg().section("Rift")) return;
        String raw = ChatText.formatted(message);
        Matcher m = BAR.matcher(StringUtil.strip(raw));
        if (!m.find()) return;
        try { combo = Integer.parseInt(m.group(2)); } catch (NumberFormatException e) { combo = 1; }
        int count = 0;
        String full = m.group(1) + m.group(3);
        for (int i = 0; i < full.length(); i++) if (full.charAt(i) == '⬛') count++;
        int bar = raw.indexOf('[');
        if (bar >= 0 && !raw.substring(bar).contains("§c")) lastHit = System.currentTimeMillis();
        int total = 10;
        if (mods[CULMINATION]) total--;
        if (mods[TIME_SLICED] && sliceSeconds > 150) total--;
        if (count == 9 && total == 8) count = 1;
        hits = "§a" + count + "/" + total;
    }

    private static void onChat(String raw) {
        if (!RiftSpot.inRift()) return;
        String t = StringUtil.stripTrim(raw);
        if (t.equals("ACTIVE MODIFIERS!")) {
            scanning = true;
            java.util.Arrays.fill(mods, false);
            return;
        }
        if (t.startsWith("▬▬▬▬▬▬▬▬▬▬")) {
            scanning = false;
            return;
        }
        if (!scanning) return;
        for (int i = 0; i < MODS.length; i++) if (t.contains(MODS[i])) mods[i] = true;
    }

    private static long seconds(String s) {
        Matcher m = DUR.matcher(s);
        long out = 0;
        while (m.find()) {
            long v = Long.parseLong(m.group(1));
            out += switch (m.group(2)) { case "h" -> v * 3600; case "m" -> v * 60; default -> v; };
        }
        return out;
    }

    private static int parse(String s) {
        try {
            return (int) Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String[] l = lines;
        if (!inChamber || l.length == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        TeslaMapsConfig c = cfg();
        RiftSpot.drawLines(g, mc, c.riftSunGeckoX, c.riftSunGeckoY, c.riftSunGeckoScale, l, null, w);
    }
}
