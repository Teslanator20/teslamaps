package com.teslamaps.features;

// Ported from Skyblocker's CompactDamage.java, hide modes after NoammAddons' DamageSplash.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.OkLabColor;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DamageSplash {

    public static final String[] HIDE_MODES = {"Off", "Always", "Dungeons", "Dungeon Clear", "Dungeon Boss"};

    private static final Pattern DAMAGE = Pattern.compile("([✧✯]?)[\\d,]+[✧✯]?([❤+⚔☄♞✷ﬗ]?)");

    private record Entry(Component src, boolean crit, Component compact) {}

    private static Int2ObjectOpenHashMap<Entry> entries = new Int2ObjectOpenHashMap<>();
    private static IntOpenHashSet hidden = new IntOpenHashSet();
    private static String builtFor = "";

    private DamageSplash() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DamageSplash").section("Combat").when(() -> cfg().damageSplash)
                .tick(DamageSplash::tick)
                .config("Combat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Damage Splash");
                    b.toggle("Damage Splash", () -> c.damageSplash, v -> c.damageSplash = v)
                            .desc("Floating damage numbers: shorten, recolour or hide them")
                            .tags("damage", "splash", "numbers", "crit");
                    b.toggle("  Compact Numbers", () -> c.damageSplashCompact, v -> c.damageSplashCompact = v)
                            .desc("12,345,678 becomes 12.35M; crits get a colour gradient");
                    b.slider("    Significant Digits", 1, 6, () -> (float) c.damageSplashPrecision,
                            v -> c.damageSplashPrecision = Math.round(v), 0);
                    b.color("    Normal Hit Color", () -> c.damageSplashNormalColor, v -> c.damageSplashNormalColor = v);
                    b.color("    Crit Gradient Start", () -> c.damageSplashCritStart, v -> c.damageSplashCritStart = v);
                    b.color("    Crit Gradient End", () -> c.damageSplashCritEnd, v -> c.damageSplashCritEnd = v);
                    b.dropdown("  Hide", HIDE_MODES, () -> c.damageSplashHide, v -> c.damageSplashHide = v)
                            .desc("Hide splashes everywhere, in all of a dungeon, only during the clear or only in the boss");
                    b.toggle("    Only Non-Crits", () -> c.damageSplashHideNonCrit, v -> c.damageSplashHideNonCrit = v)
                            .desc("Crits stay visible, plain hits are hidden");
                });
    }

    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !hidden.isEmpty() && hidden.contains(e.getId()) && cfg().damageSplash;
    }

    /** EntityRendererNameMixin: the compact version of a damage stand's name, or the tag unchanged. */
    public static Component compact(Entity e, Component tag) {
        if (tag == null || entries.isEmpty() || !(e instanceof ArmorStand)) return tag;
        TeslaMapsConfig c = cfg();
        if (!c.damageSplash || !c.damageSplashCompact) return tag;
        Entry en = entries.get(e.getId());
        return en != null && en.compact != null ? en.compact : tag;
    }

    private static boolean hideHere(TeslaMapsConfig c) {
        return switch (c.damageSplashHide) {
            case "Always" -> true;
            case "Dungeons" -> DungeonManager.isInDungeon();
            case "Dungeon Clear" -> DungeonManager.isInDungeon() && !DungeonManager.isInBossArea();
            case "Dungeon Boss" -> DungeonManager.isInDungeon() && DungeonManager.isInBossArea();
            default -> false;
        };
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        String key = c.damageSplashPrecision + c.damageSplashNormalColor + c.damageSplashCritStart + c.damageSplashCritEnd;
        boolean rebuild = !key.equals(builtFor);
        builtFor = key;
        boolean hide = hideHere(c);
        List<ArmorStand> stands = EntityCache.namedArmorStands();
        Int2ObjectOpenHashMap<Entry> next = new Int2ObjectOpenHashMap<>();
        IntOpenHashSet hid = new IntOpenHashSet();
        for (ArmorStand s : stands) {
            if (!s.isInvisible()) continue;
            Component name = s.getCustomName();
            if (name == null) continue;
            Entry en = entries.get(s.getId());
            if (en == null || en.src != name || rebuild) {
                en = classify(name, c);
                if (en == null) continue;
            }
            next.put(s.getId(), en);
            if (hide && (!c.damageSplashHideNonCrit || !en.crit)) hid.add(s.getId());
        }
        entries = next;
        hidden = hid;
    }

    private static Entry classify(Component name, TeslaMapsConfig c) {
        String str = StringUtil.strip(name.getString());
        if (str.isEmpty() || str.length() > 24) return null;
        Matcher m = DAMAGE.matcher(str);
        if (!m.matches()) return null;
        String digits = str.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 18) return null;
        boolean crit = !m.group(1).isEmpty();
        long dmg;
        try {
            dmg = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
        List<Component> sib = name.getSiblings();
        String pretty = prettify(dmg, Math.max(1, c.damageSplashPrecision));
        MutableComponent out;
        if (crit) {
            String sym = m.group(1);
            pretty = sym + pretty + sym;
            int start = hex(c.damageSplashCritStart, 0xFFFF55), end = hex(c.damageSplashCritEnd, 0xFF5555);
            out = Component.empty();
            int len = pretty.length();
            for (int i = 0; i < len; i++) {
                out.append(Component.literal(pretty.substring(i, i + 1))
                        .withColor(OkLabColor.interpolate(start, end, len > 1 ? i / (len - 1.0f) : 0)));
            }
        } else {
            TextColor tc = sib.isEmpty() ? name.getStyle().getColor() : sib.getFirst().getStyle().getColor();
            int color = tc == null || tc.equals(TextColor.fromLegacyFormat(ChatFormatting.GRAY))
                    ? hex(c.damageSplashNormalColor, 0xFFFFFF) : tc.getValue();
            out = Component.literal(pretty).withColor(color);
        }
        if (!m.group(2).isEmpty()) {
            out.append(Component.literal(m.group(2)).setStyle(sib.isEmpty() ? name.getStyle() : sib.getLast().getStyle()));
        }
        return new Entry(name, crit, out);
    }

    private static int hex(String s, int def) {
        if (s == null) return def;
        try {
            return (int) (Long.parseLong(s.length() > 6 ? s.substring(s.length() - 6) : s, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static final long[] POW = {1L, 10L, 100L, 1000L, 10000L, 100000L, 1000000L, 10000000L, 100000000L,
            1000000000L, 10000000000L, 100000000000L, 1000000000000L, 10000000000000L, 100000000000000L,
            1000000000000000L, 10000000000000000L, 100000000000000000L, 1000000000000000000L};

    static int digits(long x) {
        int d = 1;
        while (d < POW.length && x >= POW[d]) d++;
        return d;
    }

    static String prettify(long damage, int precision) {
        int used = digits(damage);
        if (used > precision) {
            double p = POW[used - precision];
            damage = (long) (Math.round(damage / p) * p);
        } else precision = used;
        if (damage < 1_000L) return String.valueOf(damage);
        if (damage < 1_000_000L) return toPrecision(damage / 1_000.0, precision) + "k";
        if (damage < 1_000_000_000L) return toPrecision(damage / 1_000_000.0, precision) + "M";
        if (damage < 1_000_000_000_000L) return toPrecision(damage / 1_000_000_000.0, precision) + "B";
        return toPrecision(damage / 1_000_000_000_000.0, precision) + "T";
    }

    private static String toPrecision(double n, int precision) {
        int used = digits((long) n);
        int rest = precision - used;
        if (rest <= 0) {
            long p = POW[used - precision];
            return String.valueOf(Math.round(n / p) * p);
        }
        return String.format("%." + rest + "f", n);
    }
}
