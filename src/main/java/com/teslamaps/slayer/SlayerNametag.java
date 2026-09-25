package com.teslamaps.slayer;

// Ported from Zen's slayerdisplay/SlayerDisplay.kt + BossTypes.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerNametag {

    public static final String[] MODES = {"All", "Carries", "Own", "Own + Carries"};

    private static final Pattern BOSS = Pattern.compile("(Revenant Horror|Atoned Horror|Tarantula Broodfather|Conjoined Brood"
            + "|Sven Packmaster|Voidgloom Seraph|Inferno Demonlord|Riftstalker Bloodfiend|Bloodfiend)");
    private static final Pattern HP = Pattern.compile("([\\d.,]+[kKmMbB]?)❤");
    private static final Pattern HITS = Pattern.compile("(\\d+) Hits?\\b");
    private static final Pattern TIMER = Pattern.compile("^\\d{1,2}:\\d{2}$");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private static final class Boss {
        final ArmorStand tag;
        final List<ArmorStand> extra = new ArrayList<>();
        final long firstSeen;
        boolean own;
        String l1 = "", l2 = "";
        Vec3 lastPos;

        Boss(ArmorStand tag, long firstSeen) {
            this.tag = tag;
            this.firstSeen = firstSeen;
        }
    }

    private record Dead(Vec3 pos, String text, long until) {}

    private static final Map<ArmorStand, Boss> bosses = new IdentityHashMap<>();
    private static final List<Dead> dead = new ArrayList<>();
    private static IntOpenHashSet hidden = new IntOpenHashSet();
    private static Feature feature;
    private static Object lastLevel;
    private static int ticks;

    private SlayerNametag() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("SlayerNametag").section("Combat").when(() -> cfg().slayerNametag)
                .tick(SlayerNametag::tick)
                .world(SlayerNametag::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Clean Slayer Nametag", () -> c.slayerNametag, v -> c.slayerNametag = v)
                            .desc("Own nametag over slayer bosses: name, HP, Voidgloom hits, laser and quest timer")
                            .tags("slayer", "nametag", "boss", "display", "hp");
                    b.dropdown("  Show For", MODES, () -> c.slayerNametagMode, v -> c.slayerNametagMode = v)
                            .desc("Carries = bosses spawned by a customer in the Carry Tracker");
                    b.toggle("  Short Names (Rev 5, Void 4)", () -> c.slayerNametagShortName, v -> c.slayerNametagShortName = v);
                    b.toggle("  Compact (one line)", () -> c.slayerNametagCompact, v -> c.slayerNametagCompact = v);
                    b.toggle("  Hide Hypixel Nametags", () -> c.slayerNametagHideOriginal, v -> c.slayerNametagHideOriginal = v);
                    b.toggle("  Kill Time After Death", () -> c.slayerNametagKillTimer, v -> c.slayerNametagKillTimer = v);
                });
    }

    /** From EntityRenderDispatcherMixin: Hypixel's own tags of a displayed boss. */
    public static boolean shouldHide(Entity e) {
        if (hidden.isEmpty() || !(e instanceof ArmorStand)) return false;
        return hidden.contains(e.getId()) && feature != null && feature.active();
    }

    private static boolean isTag(String clean) {
        return (clean.contains("❤") || clean.contains("Hit")) && BOSS.matcher(clean).find();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            bosses.clear();
            dead.clear();
            hidden = new IntOpenHashSet();
        }
        if (++ticks % 5 == 0) discover(mc, now);

        TeslaMapsConfig c = cfg();
        IntOpenHashSet hide = c.slayerNametagHideOriginal ? new IntOpenHashSet() : null;
        Entity ownBoss = SlayerHUD.getBossEntity();
        Iterator<Boss> it = bosses.values().iterator();
        while (it.hasNext()) {
            Boss b = it.next();
            String clean = StringUtil.strip(b.tag.getName().getString());
            if (!b.tag.isAlive() || !isTag(clean) || clean.endsWith(" 0❤")) {
                if (c.slayerNametagKillTimer && b.lastPos != null) {
                    dead.add(new Dead(b.lastPos, "§aKilled in §f" + String.format(java.util.Locale.ROOT, "%.1fs",
                            (now - b.firstSeen) / 1000.0), now + 3000));
                }
                it.remove();
                continue;
            }
            b.lastPos = b.tag.position();
            build(b, clean, ownBoss);
            if (hide != null) {
                hide.add(b.tag.getId());
                for (ArmorStand s : b.extra) hide.add(s.getId());
            }
        }
        hidden = hide != null ? hide : hidden.isEmpty() ? hidden : new IntOpenHashSet();
        dead.removeIf(d -> d.until < now);
    }

    private static void discover(Minecraft mc, long now) {
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        AABB box = new AABB(px - 30, py - 10, pz - 30, px + 30, py + 10, pz + 30);
        String me = mc.getUser().getName();
        String mode = cfg().slayerNametagMode;
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, box, a -> a.hasCustomName() && a.isAlive())) {
            if (bosses.containsKey(as)) continue;
            String clean = StringUtil.strip(as.getName().getString());
            if (!isTag(clean) || clean.endsWith(" 0❤")) continue;
            String owner = SlayerHUD.ownerOf(as);
            boolean own = owner != null && owner.equalsIgnoreCase(me);
            boolean carry = owner != null && isCustomer(owner);
            boolean show = switch (mode) {
                case "Carries" -> carry;
                case "Own" -> own;
                case "Own + Carries" -> own || carry;
                default -> true;
            };
            if (!show) continue;
            long spawn = own ? SlayerQuest.spawnMs() : 0;
            Boss b = new Boss(as, spawn > 0 ? spawn : now);
            b.own = own;
            for (ArmorStand s : NametagMobs.namedStands(as, 1.5)) {
                if (s == as) continue;
                String n = StringUtil.stripTrim(s.getName().getString());
                if (TIMER.matcher(n).matches() || n.startsWith("Spawned by")) b.extra.add(s);
            }
            bosses.put(as, b);
        }
    }

    private static boolean isCustomer(String name) {
        for (TeslaMapsConfig.SlayerCarry c : cfg().slayerCarries) if (name.equalsIgnoreCase(c.customer)) return true;
        return false;
    }

    private static void build(Boss b, String clean, Entity ownBoss) {
        Matcher m = BOSS.matcher(clean);
        String full = m.find() ? m.group(1) : "Boss";
        int tier = SlayerHUD.tagTier(clean);
        String name = cfg().slayerNametagShortName ? shortName(full) + (tier > 0 ? " " + tier : "")
                : full + (tier > 0 && tier < ROMAN.length ? " " + ROMAN[tier] : "");
        StringBuilder l1 = new StringBuilder(b.own ? "§c§l" : "§c").append(name);
        Matcher hp = HP.matcher(clean);
        if (hp.find()) l1.append(" §a").append(hp.group(1)).append("§c❤");
        Matcher hits = HITS.matcher(clean);
        if (hits.find()) l1.append(" §d").append(hits.group(1)).append(" Hits");

        StringBuilder l2 = new StringBuilder();
        float laser = VoidgloomLaser.remaining();
        if (laser > 0 && ownBoss != null && SlayerHUD.bossTag() == b.tag) {
            l2.append("§bLaser ").append(String.format(java.util.Locale.ROOT, "%.1fs", laser));
        }
        for (ArmorStand s : b.extra) {
            String n = StringUtil.stripTrim(s.getName().getString());
            if (TIMER.matcher(n).matches()) {
                if (!l2.isEmpty()) l2.append(" ");
                l2.append("§e").append(n);
                break;
            }
        }
        if (cfg().slayerNametagCompact && !l2.isEmpty()) {
            b.l1 = l1.append(" ").append(l2).toString();
            b.l2 = "";
        } else {
            b.l1 = l1.toString();
            b.l2 = l2.toString();
        }
    }

    private static String shortName(String full) {
        return switch (full) {
            case "Revenant Horror" -> "Rev";
            case "Atoned Horror" -> "Atoned";
            case "Tarantula Broodfather" -> "Tara";
            case "Conjoined Brood" -> "Brood";
            case "Sven Packmaster" -> "Sven";
            case "Voidgloom Seraph" -> "Void";
            case "Inferno Demonlord" -> "Blaze";
            default -> "Vamp";
        };
    }

    private static void render(PoseStack matrices, Vec3 cam) {
        for (Boss b : bosses.values()) {
            if (b.l1.isEmpty()) continue;
            Vec3 pos = ESPRenderer.lerpBox(b.tag).getCenter();
            ESPRenderer.drawText(matrices, b.l1, pos.add(0, 0.55, 0), 1f, cam, false);
            if (!b.l2.isEmpty()) ESPRenderer.drawText(matrices, b.l2, pos.add(0, 0.28, 0), 1f, cam, false);
        }
        for (Dead d : dead) ESPRenderer.drawText(matrices, d.text, d.pos.add(0, 0.55, 0), 1f, cam, false);
    }
}
