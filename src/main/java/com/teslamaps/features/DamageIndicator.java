package com.teslamaps.features;

// Concept from SkyHanni's DamageIndicatorManager.kt / BossType.kt (lean: nametag HP, DPS and time to kill)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.mobvisuals.MobHealthHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DamageIndicator {

    private static final String[] BOSSES = {
            "Revenant Horror", "Atoned Horror", "Tarantula Broodfather", "Conjoined Brood", "Sven Packmaster",
            "Voidgloom Seraph", "Inferno Demonlord", "Riftstalker Bloodfiend",
            "Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Maxor", "Goldor", "Necron",
            "Minos Inquisitor", "Minos Champion", "Minotaur", "King Minos", "Manticore", "Sphinx",
            "Lord Jawbus", "Thunder", "Ragnarok", "Plhlegblast", "Titanoboa", "Wiki Tiki", "Loch Emperor",
            "Arachne", "Broodmother", "Ashfang", "Bladesoul", "Barbarian Duke X", "Mage Outlaw", "Magma Boss",
            "Kuudra", "Bacte", "Leech Supreme", "Shania", "End Stone Protector", "Endstone Protector",
            "Headless Horseman", "Great White Shark", "Grim Reaper", "Phantom Fisher", "Yeti", "Reindrake"};
    private static final int SLAYERS = 8;

    private static final Pattern SINGLE = Pattern.compile("([\\d,.]+[kKmMbB]?)❤");
    private static final long WINDOW_MS = 3000;
    private static final int MAX_BOSSES = 6;

    private static final class Boss {
        Component src;
        String name;
        boolean slayer;
        double hp, max;
        final long[] at = new long[32];
        final double[] val = new double[32];
        int n, head;
    }

    private record Label(ArmorStand stand, String text) {}

    private static Int2ObjectOpenHashMap<Boss> bosses = new Int2ObjectOpenHashMap<>();
    private static IntOpenHashSet tagIds = new IntOpenHashSet();
    private static List<Label> labels = List.of();
    private static int ticks;

    private DamageIndicator() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DamageIndicator").section("Combat").when(() -> cfg().damageIndicator)
                .tick(DamageIndicator::tick)
                .world(DamageIndicator::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Damage Indicator");
                    b.toggle("Damage Indicator", () -> c.damageIndicator, v -> c.damageIndicator = v)
                            .desc("Big coloured HP above slayer, dungeon, Diana, sea creature and other bosses, read from their nametag. Slayer bosses are skipped while Clean Slayer Nametag or Slayer Boss HUD is on.")
                            .tags("boss", "hp", "health", "dps", "ttk");
                    b.toggle("  DPS and Time to Kill", () -> c.damageIndicatorDps, v -> c.damageIndicatorDps = v)
                            .desc("Damage (or heal) per second over the last 3 s and the time left at that rate");
                    b.toggle("  Hide Vanilla Nametag", () -> c.damageIndicatorHideTag, v -> c.damageIndicatorHideTag = v);
                    b.slider("  Text Size", 0.5f, 4.0f, () -> c.damageIndicatorScale, v -> c.damageIndicatorScale = v, 1);
                });
    }

    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !tagIds.isEmpty() && tagIds.contains(e.getId())
                && cfg().damageIndicator && cfg().damageIndicatorHideTag;
    }

    private static boolean isBoss(String clean) {
        if (clean.startsWith("﴾") && clean.endsWith("﴿")) return true;
        for (String b : BOSSES) if (clean.contains(b)) return true;
        return false;
    }

    private static void tick() {
        if (++ticks % 2 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        TeslaMapsConfig c = cfg();
        boolean skipSlayers = c.slayerNametag || c.slayerBossHud;
        Int2ObjectOpenHashMap<Boss> next = new Int2ObjectOpenHashMap<>();
        IntOpenHashSet ids = new IntOpenHashSet();
        List<ArmorStand> found = new ArrayList<>();
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            if (found.size() >= MAX_BOSSES) break;
            Component name = s.getCustomName();
            if (name == null || s.distanceToSqr(mc.player) > 48 * 48) continue;
            Boss b = bosses.get(s.getId());
            if (b != null && b.src == name) {
                if (skipSlayers && b.slayer) continue;
                next.put(s.getId(), b);
                found.add(s);
                continue;
            }
            String raw = name.getString();
            if (raw.indexOf('❤') < 0) continue;
            String clean = StringUtil.stripTrim(raw);
            if (!isBoss(clean)) continue;
            boolean slayer = isSlayer(clean);
            if (skipSlayers && slayer) continue;
            double cur, max;
            double[] hp = MobHealthHud.parseHealth(clean);
            if (hp != null) {
                cur = hp[0];
                max = hp[1];
            } else {
                Matcher m = SINGLE.matcher(clean);
                if (!m.find()) continue;
                cur = NumberUtil.parseSuffixed(m.group(1));
                max = 0;
            }
            if (b == null) {
                b = new Boss();
                b.name = shortName(clean);
                b.slayer = slayer;
            }
            b.src = name;
            b.hp = cur;
            b.max = Math.max(max, Math.max(b.max, cur));
            next.put(s.getId(), b);
            found.add(s);
        }
        List<Label> out = new ArrayList<>(found.size());
        for (ArmorStand s : found) {
            Boss b = next.get(s.getId());
            sample(b, now);
            ids.add(s.getId());
            out.add(new Label(s, text(b, now, c.damageIndicatorDps)));
        }
        bosses = next;
        tagIds = ids;
        labels = out;
    }

    private static boolean isSlayer(String clean) {
        for (int i = 0; i < SLAYERS; i++) if (clean.contains(BOSSES[i])) return true;
        return false;
    }

    private static String shortName(String clean) {
        String s = clean.replace("﴾", "").replace("﴿", "").replace("☠", "").trim();
        for (String b : BOSSES) if (s.contains(b)) return b;
        String n = MobHealthHud.parseName(s);
        int heart = n.indexOf('❤');
        return heart > 0 ? n.substring(0, heart).trim() : n;
    }

    private static void sample(Boss b, long now) {
        b.at[b.head] = now;
        b.val[b.head] = b.hp;
        b.head = (b.head + 1) % b.at.length;
        if (b.n < b.at.length) b.n++;
    }

    private static String text(Boss b, long now, boolean dps) {
        double pct = b.max > 0 ? b.hp / b.max : 1;
        String col = pct > 0.75 ? "§a" : pct > 0.5 ? "§e" : pct > 0.25 ? "§6" : "§c";
        StringBuilder sb = new StringBuilder();
        sb.append("§f").append(b.name).append(' ').append(col).append(NumberUtil.formatSuffixed(b.hp));
        if (b.max > b.hp) sb.append("§8/§7").append(NumberUtil.formatSuffixed(b.max));
        if (!dps || b.n < 2) return sb.toString();
        int oldest = -1;
        for (int i = 0; i < b.n; i++) {
            int idx = (b.head - 1 - i + b.at.length) % b.at.length;
            if (now - b.at[idx] > WINDOW_MS) break;
            oldest = idx;
        }
        if (oldest < 0) return sb.toString();
        double dt = (now - b.at[oldest]) / 1000.0;
        if (dt < 0.5) return sb.toString();
        double rate = (b.val[oldest] - b.hp) / dt;
        if (rate > 0) {
            sb.append(" §7| §e").append(NumberUtil.formatSuffixed(rate)).append("/s");
            double ttk = b.hp / rate;
            if (ttk < 3600) sb.append(" §7| §b").append(ttk < 10 ? String.format("%.1fs", ttk) : (int) ttk + "s");
        } else if (rate < 0) {
            sb.append(" §7| §a+").append(NumberUtil.formatSuffixed(-rate)).append("/s");
        }
        return sb.toString();
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Label> l = labels;
        if (l.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float scale = cfg().damageIndicatorScale;
        for (Label label : l) {
            if (label.stand.isRemoved()) continue;
            Vec3 p = label.stand.getPosition(pt).add(0, label.stand.getBbHeight() + 0.6, 0);
            ESPRenderer.drawText(pose, label.text, p, scale, cam);
        }
    }
}
