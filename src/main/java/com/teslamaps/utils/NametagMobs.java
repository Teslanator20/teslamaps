package com.teslamaps.utils;

// Ported from SkyHanni's MobUtils / MobFilter / MobFactories (pairing + name filters, no event system)
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nametag armor stand to mob entity pairing. Hypixel spawns the mob first and its name stand
 * right after, so the stand's entity id is the mob's id + 1 (stacked riders push it further).
 * When the id chain does not line up, the closest mob with the nearest spawn tick wins.
 */
public final class NametagMobs {

    public enum Kind { BASIC, BOSS, SLAYER, SUMMON, DUNGEON }

    /** Parsed nametag. level is the [Lv] level or the slayer tier, -1 if none. */
    public record Info(Kind kind, String name, int level, String owner, boolean starred, boolean corrupted) {}

    private static final String TYPE = "(?:[^\\w\\s✯\\-]+ )?";
    private static final String LEVEL = "(?:\\[Lv(?<level>\\d+)\\] )?";

    private static final Pattern BASIC = Pattern.compile(
            "^" + LEVEL + TYPE + "(?<corrupted>.Corrupted )?(?<name>[^ᛤ]*?)(?: ᛤ)? [\\dBMk.,/❤]+$");
    private static final Pattern SLAYER = Pattern.compile(
            "^" + TYPE + ". (?<name>.*?)(?: (?<tier>[IV]+)|(?<=Atoned Horror|Conjoined Brood)) \\d+.*");
    private static final Pattern BOSS = Pattern.compile(
            "^. " + LEVEL + TYPE + "(?<name>[^ᛤ\\n]*?)(?: ᛤ)?(?: [\\d/BMk.,❤]+| █+)? .$");
    private static final Pattern DUNGEON = Pattern.compile(
            "^" + LEVEL + TYPE + "(?:(?<star>✯)\\s)?"
                    + "(?:(?:Flaming|Stormy|Speedy|Fortified|Healthy|Healing|Boomer|Golden|Stealth)\\s)?"
                    + "(?:\\[[\\w\\d]+\\]\\s)?(?<name>[^ᛤ]+?)(?: ᛤ)?\\s[^\\s]+$");
    private static final Pattern SUMMON = Pattern.compile("^(?<owner>\\w+)'s (?<name>.*) \\d+.*");

    private static final int MAX_STACK = 4;

    private NametagMobs() {}

    /** Name of a stand without color codes, or null if it has no custom name. */
    public static String tagName(ArmorStand stand) {
        if (!stand.hasCustomName() || stand.getCustomName() == null) return null;
        return StringUtil.stripTrim(stand.getCustomName().getString());
    }

    /** Same order as SkyHanni's createSkyblockMob: summon, slayer, boss, then dungeon or basic. */
    public static Info parse(String clean, boolean inDungeon) {
        if (clean == null || clean.isEmpty()) return null;
        Matcher m = SUMMON.matcher(clean);
        if (m.matches()) return new Info(Kind.SUMMON, m.group("name"), -1, m.group("owner"), false, false);
        m = SLAYER.matcher(clean);
        if (m.matches()) {
            String tier = m.group("tier");
            return new Info(Kind.SLAYER, m.group("name"), tier == null ? 5 : roman(tier), null, false, false);
        }
        m = BOSS.matcher(clean);
        if (m.matches() && isBossBracket(clean)) {
            return new Info(Kind.BOSS, m.group("name").trim(), level(m), null, false, false);
        }
        if (inDungeon) {
            m = DUNGEON.matcher(clean);
            if (m.matches()) return new Info(Kind.DUNGEON, m.group("name"), level(m), null, m.group("star") != null, false);
        }
        m = BASIC.matcher(clean);
        if (m.find()) {
            return new Info(Kind.BASIC, m.group("name").trim(), level(m), null, false, m.group("corrupted") != null);
        }
        return null;
    }

    public static Info parse(ArmorStand stand, boolean inDungeon) {
        return parse(tagName(stand), inDungeon);
    }

    /** The mob a name stand belongs to, or null. */
    public static LivingEntity mobForTag(ArmorStand tag) {
        Level level = tag.level();
        for (int off = 1; off <= MAX_STACK; off++) {
            Entity e = level.getEntity(tag.getId() - off);
            if (e == null) break;
            if (e instanceof ArmorStand as) {
                if (as.hasCustomName()) break;
                continue;
            }
            if (e instanceof LivingEntity le && le.isAlive() && closeXZ(le, tag)) return le;
            break;
        }
        return closestMob(null, tag);
    }

    /** Like {@link #mobForTag} but only accepts the given entity type. */
    public static <T extends Entity> T mobForTag(EntityType<T> type, ArmorStand tag) {
        Level level = tag.level();
        for (int off = 1; off <= MAX_STACK; off++) {
            Entity e = level.getEntity(tag.getId() - off);
            if (e == null) break;
            if (e instanceof ArmorStand as && !as.hasCustomName()) continue;
            if (e.getType() == type && e.isAlive() && closeXZ(e, tag)) {
                @SuppressWarnings("unchecked") T t = (T) e;
                return t;
            }
            if (e instanceof ArmorStand) break;
        }
        return closestMob(type, tag);
    }

    /**
     * Skyblocker's findClosestMobEntity: candidates under the stand, sorted by distance,
     * the one whose spawn tick is nearest the stand's wins.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> T closestMob(EntityType<T> type, ArmorStand tag) {
        List<Entity> found = tag.level().getEntities(tag, tag.getBoundingBox().inflate(0, 1.5, 0),
                e -> e.isAlive() && !(e instanceof ArmorStand) && e instanceof LivingEntity
                        && (type == null || e.getType() == type)
                        && !(e instanceof net.minecraft.world.entity.player.Player p && isRealPlayer(p)));
        if (found.isEmpty()) return null;
        if (found.size() == 1) return (T) found.getFirst();
        Entity best = null;
        int bestDiff = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : found) {
            int diff = Math.abs(e.tickCount - tag.tickCount);
            double dist = e.distanceToSqr(tag);
            if (diff < bestDiff || (diff == bestDiff && dist < bestDist)) {
                best = e;
                bestDiff = diff;
                bestDist = dist;
            }
        }
        return (T) best;
    }

    /** The name stand of a mob, or null. */
    public static ArmorStand tagForMob(Entity mob) {
        Level level = mob.level();
        for (int off = 1; off <= MAX_STACK; off++) {
            Entity e = level.getEntity(mob.getId() + off);
            if (e == null) break;
            if (e instanceof ArmorStand as) {
                if (as.hasCustomName()) return closeXZ(as, mob) ? as : null;
                continue;
            }
            if (!(e instanceof LivingEntity)) break;
        }
        return null;
    }

    /** Skyblocker's getEntityArmorStands: every named stand around the entity. */
    public static List<ArmorStand> namedStands(Entity entity, double expandY) {
        List<ArmorStand> out = new ArrayList<>();
        for (Entity e : entity.level().getEntities(entity, entity.getBoundingBox().inflate(0.1, expandY, 0.1),
                x -> x instanceof ArmorStand && x.hasCustomName())) {
            out.add((ArmorStand) e);
        }
        return out;
    }

    /** Hypixel NPCs and mobs shaped like players use a v2 uuid, real players v4. */
    public static boolean isRealPlayer(net.minecraft.world.entity.player.Player p) {
        return p.getUUID().version() == 4;
    }

    private static boolean closeXZ(Entity a, Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz < 4.0;
    }

    private static boolean isBossBracket(String s) {
        return s.startsWith("﴾") && s.endsWith("﴿");
    }

    private static int level(Matcher m) {
        String l = m.group("level");
        if (l == null || l.isEmpty()) return -1;
        try { return Integer.parseInt(l); } catch (NumberFormatException e) { return -1; }
    }

    private static int roman(String r) {
        return switch (r) {
            case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
            default -> -1;
        };
    }
}
