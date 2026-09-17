package com.teslamaps.features;

// Ported from Skysoft's FishingHotspotDetector/Tracker/Sharing/WaypointRenderer/ClosedTitle
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hotspots are an armor stand named HOTSPOT with a "+Stat" tag right above.
 * Tracks them, announces new ones to the party, renders waypoints for shared
 * ones and titles when the one you fish at closes.
 */
public class HotspotShare {

    private static final String SHARE_MARKER = "Found a Hotspot!";
    private static final Pattern SHARE_PATTERN = Pattern.compile(
            "Found a Hotspot! \\((?<stat>[^)]+)\\) @ (?<x>-?\\d+) (?<y>-?\\d+) (?<z>-?\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_LINE = Pattern.compile("^Party > (?<sender>.+?): (?<body>.*)$");
    private static final Pattern NON_STAT = Pattern.compile("[^A-Za-z0-9+.% -]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final long MISSING_GRACE_MS = 1000;
    private static final long PENDING_SHARE_DELAY_MS = 2000;
    private static final long PENDING_SHARE_EXPIRE_MS = 60_000;
    private static final long SHARE_SUPPRESS_MS = 10 * 60 * 1000;
    private static final long WAYPOINT_LIFETIME_MS = 20_000;
    private static final double STAT_PAIR_DISTANCE_SQ = 1.0;
    private static final double MATCH_HORIZONTAL_SQ = 36.0;
    private static final double MATCH_VERTICAL = 3.0;
    private static final double ARRIVAL_HORIZONTAL_SQ = 20.0 * 20.0;
    private static final double ARRIVAL_VERTICAL = 12.0;

    private static final int WAYPOINT_OUTLINE = 0xDCFF55FF;
    private static final int WAYPOINT_FILL = 0x46AA00FF;

    private static final class Tracked {
        final UUID id;
        final String stat;
        final BlockPos pos;
        final long firstSeen = System.currentTimeMillis();
        long missingSince = 0;
        long sharedAt = 0;

        Tracked(UUID id, String stat, BlockPos pos) {
            this.id = id;
            this.stat = stat;
            this.pos = pos;
        }
    }

    private static final class Waypoint {
        final String stat;
        final BlockPos pos;
        final String sender;
        final long createdAt = System.currentTimeMillis();

        Waypoint(String stat, BlockPos pos, String sender) {
            this.stat = stat;
            this.pos = pos;
            this.sender = sender;
        }
    }

    private static final Map<UUID, Tracked> tracked = new HashMap<>();
    private static final Map<String, Long> recentShares = new HashMap<>();
    private static final List<Waypoint> waypoints = new ArrayList<>();
    private static int tickCounter = 0;
    private static String lastArea = "";

    private static boolean inHotspotIsland() {
        String area = SkyblockUtils.getArea();
        return area.equalsIgnoreCase("Crimson Isle") || area.equalsIgnoreCase("Backwater Bayou");
    }

    private static boolean active(TeslaMapsConfig c) {
        return c.hotspotShare || c.hotspotShareWaypoints || c.hotspotClosedTitle;
    }

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!active(c) || mc.level == null || mc.player == null) {
            if (!tracked.isEmpty()) tracked.clear();
            if (!waypoints.isEmpty()) waypoints.clear();
            return;
        }

        String area = SkyblockUtils.getArea();
        if (!area.equals(lastArea)) {
            lastArea = area;
            tracked.clear();
            waypoints.clear();
        }
        if (!inHotspotIsland()) return;

        pruneWaypoints(mc);

        if (++tickCounter % SCAN_INTERVAL_TICKS != 0) return;
        long now = System.currentTimeMillis();

        UUID nearestBefore = nearestHotspotId(mc);

        // scan
        Map<UUID, Tracked> seen = new HashMap<>();
        List<ArmorStand> stands = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStand stand && stand.isAlive() && stand.hasCustomName()) stands.add(stand);
        }
        for (ArmorStand stand : stands) {
            String name = StringUtil.strip(stand.getCustomName().getString()).trim();
            if (!name.equals("HOTSPOT")) continue;
            ArmorStand statTag = null;
            for (ArmorStand other : stands) {
                if (other == stand) continue;
                String tag = StringUtil.strip(other.getCustomName().getString()).trim();
                if (!tag.startsWith("+") || tag.length() > 64 || tag.chars().noneMatch(Character::isLetter)) continue;
                if (other.distanceToSqr(stand) > STAT_PAIR_DISTANCE_SQ) continue;
                statTag = other;
                break;
            }
            if (statTag == null) continue;
            String stat = cleanStat(StringUtil.strip(statTag.getCustomName().getString()));
            BlockPos pos = stand.blockPosition().below();
            seen.put(stand.getUUID(), new Tracked(stand.getUUID(), stat, pos));
        }

        // reconcile
        for (Map.Entry<UUID, Tracked> entry : seen.entrySet()) {
            Tracked existing = tracked.get(entry.getKey());
            if (existing == null) {
                tracked.put(entry.getKey(), entry.getValue());
            } else {
                existing.missingSince = 0;
            }
        }
        Iterator<Tracked> it = tracked.values().iterator();
        while (it.hasNext()) {
            Tracked t = it.next();
            if (seen.containsKey(t.id)) continue;
            if (!mc.level.isLoaded(t.pos)) {
                t.missingSince = 0; // unloaded chunk is not a closed hotspot
                continue;
            }
            if (t.missingSince == 0) {
                t.missingSince = now;
            } else if (now - t.missingSince >= MISSING_GRACE_MS) {
                if (c.hotspotClosedTitle && t.id.equals(nearestBefore)) {
                    mc.gui.setTimes(0, 50, 5);
                    mc.gui.setTitle(Component.literal("§d§lHotspot closed"));
                    LoudSound.play(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 0.9f);
                }
                it.remove();
            }
        }

        // share new hotspots
        if (c.hotspotShare && mc.getConnection() != null && holdingRod(mc)) {
            for (Tracked t : tracked.values()) {
                if (t.sharedAt != 0 || t.missingSince != 0) continue;
                long age = now - t.firstSeen;
                if (age < PENDING_SHARE_DELAY_MS || age > PENDING_SHARE_EXPIRE_MS) continue;
                String key = t.pos.getX() + "," + t.pos.getY() + "," + t.pos.getZ() + ":" + t.stat.toLowerCase(Locale.ROOT);
                Long last = recentShares.get(key);
                if (last != null && now - last < SHARE_SUPPRESS_MS) {
                    t.sharedAt = now;
                    continue;
                }
                recentShares.put(key, now);
                t.sharedAt = now;
                mc.getConnection().sendCommand("pc " + SHARE_MARKER + " (" + t.stat + ") @ "
                        + t.pos.getX() + " " + t.pos.getY() + " " + t.pos.getZ());
                break; // one share per scan
            }
        }
        recentShares.values().removeIf(time -> now - time > SHARE_SUPPRESS_MS);
    }

    public static void onChatMessage(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hotspotShareWaypoints || !inHotspotIsland()) return;

        String text = StringUtil.strip(raw).trim();
        Matcher party = PARTY_LINE.matcher(text);
        if (!party.matches()) return;
        Matcher m = SHARE_PATTERN.matcher(party.group("body"));
        if (!m.find()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String sender = party.group("sender");
        int space = sender.lastIndexOf(' ');
        if (space >= 0) sender = sender.substring(space + 1);

        String stat = m.group("stat").trim();
        BlockPos pos = new BlockPos(Integer.parseInt(m.group("x")),
                Integer.parseInt(m.group("y")), Integer.parseInt(m.group("z")));

        // own messages / hotspots we already see: no waypoint
        if (sender.equalsIgnoreCase(mc.getUser().getName())) return;
        for (Tracked t : tracked.values()) {
            if (matches(t.stat, t.pos, stat, pos)) return;
        }
        for (Waypoint w : waypoints) {
            if (matches(w.stat, w.pos, stat, pos)) return;
        }
        waypoints.add(new Waypoint(stat, pos, sender));
    }

    private static boolean matches(String statA, BlockPos posA, String statB, BlockPos posB) {
        if (!statA.equalsIgnoreCase(statB)) return false;
        double dx = posA.getX() - posB.getX(), dz = posA.getZ() - posB.getZ();
        return Math.abs(posA.getY() - posB.getY()) <= MATCH_VERTICAL && dx * dx + dz * dz <= MATCH_HORIZONTAL_SQ;
    }

    private static void pruneWaypoints(Minecraft mc) {
        long now = System.currentTimeMillis();
        Vec3 player = mc.player.position();
        waypoints.removeIf(w -> {
            if (now - w.createdAt > WAYPOINT_LIFETIME_MS) return true;
            double dx = player.x - (w.pos.getX() + 0.5), dz = player.z - (w.pos.getZ() + 0.5);
            return dx * dx + dz * dz <= ARRIVAL_HORIZONTAL_SQ
                    && Math.abs(player.y - w.pos.getY()) <= ARRIVAL_VERTICAL;
        });
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (waypoints.isEmpty() || !TeslaMapsConfig.get().hotspotShareWaypoints) return;

        for (Waypoint w : waypoints) {
            AABB box = new AABB(w.pos.getX(), w.pos.getY(), w.pos.getZ(),
                    w.pos.getX() + 1, w.pos.getY() + 1, w.pos.getZ() + 1);
            ESPRenderer.drawFilledBox(matrices, box, WAYPOINT_FILL, cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, box, WAYPOINT_OUTLINE, 3f, cameraPos, true);

            Vec3 anchor = new Vec3(w.pos.getX() + 0.5, w.pos.getY() + 1.8, w.pos.getZ() + 0.5);
            ESPRenderer.drawText(matrices, "§d§lHOTSPOT", anchor.add(0, 0.8, 0), 1.2f, cameraPos);
            ESPRenderer.drawText(matrices, "§b" + w.stat, anchor.add(0, 0.4, 0), 1.0f, cameraPos);
            ESPRenderer.drawText(matrices, "§7Shared by " + w.sender, anchor, 0.9f, cameraPos);
        }
    }

    private static UUID nearestHotspotId(Minecraft mc) {
        Vec3 player = mc.player.position();
        UUID best = null;
        double bestDist = Double.MAX_VALUE;
        for (Tracked t : tracked.values()) {
            double d = player.distanceToSqr(t.pos.getX() + 0.5, t.pos.getY() + 0.5, t.pos.getZ() + 0.5);
            if (d < bestDist) {
                bestDist = d;
                best = t.id;
            }
        }
        return best;
    }

    private static boolean holdingRod(Minecraft mc) {
        return mc.player.getMainHandItem().getItem() instanceof FishingRodItem
                || mc.player.getOffhandItem().getItem() instanceof FishingRodItem;
    }

    private static String cleanStat(String stat) {
        return SPACES.matcher(NON_STAT.matcher(stat).replaceAll(" ")).replaceAll(" ").trim();
    }
}
