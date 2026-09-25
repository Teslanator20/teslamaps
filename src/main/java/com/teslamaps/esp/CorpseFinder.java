package com.teslamaps.esp;

// Ported from Skyblocker's CorpseFinder.java / CorpseType.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Legit corpse finder: a corpse only shows once it was in line of sight, never through walls. */
public final class CorpseFinder {

    enum Type {
        LAPIS("Lapis", "LAPIS_ARMOR_HELMET", "§9", 0x5555FF),
        UMBER("Umber", "ARMOR_OF_YOG_HELMET", "§6", 0xFFAA00),
        TUNGSTEN("Tungsten", "MINERAL_HELMET", "§7", 0xAAAAAA),
        VANGUARD("Vanguard", "VANGUARD_HELMET", "§b", 0x55FFFF);

        final String name, helmet, code;
        final int color;

        Type(String name, String helmet, String code, int color) {
            this.name = name;
            this.helmet = helmet;
            this.code = code;
            this.color = color;
        }

        static Type ofHelmet(String id) {
            for (Type t : values()) if (t.helmet.equals(id)) return t;
            return null;
        }
    }

    static final class Corpse {
        final int id;
        final BlockPos pos;
        final Type type;
        boolean seen, looted, shared;

        Corpse(int id, BlockPos pos, Type type) {
            this.id = id;
            this.pos = pos;
            this.type = type;
        }
    }

    private static final Pattern LOOT = Pattern.compile("^([A-Z]+) CORPSE LOOT!$");
    private static final Pattern COORDS = Pattern.compile("x: (-?\\d+), y: (-?\\d+), z: (-?\\d+)");
    private static final Pattern SHARED_TYPE = Pattern.compile("\\((\\w+) Corpse\\)");
    private static final List<BlockPos> sharedByOthers = new ArrayList<>();

    private static final List<Corpse> corpses = new ArrayList<>();
    private static Object lastLevel;
    private static int nextId;

    private CorpseFinder() {}

    public static void register() {
        Feature.of("CorpseFinder").section("Mining").when(() -> cfg().corpseFinder || waypointMode())
                .tick(CorpseFinder::tick)
                .world(CorpseFinder::render)
                .chat(CorpseFinder::onChat);
    }

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    private static boolean waypointMode() {
        return cfg().mineshaftWaypoints && cfg().mineshaftWpCorpses;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            corpses.clear();
            sharedByOthers.clear();
        }
        if (!CorpseESP.inMineshaft()) return;
        for (ArmorStand as : com.teslamaps.utils.EntityCache.armorStands()) {
            if (as.isRemoved() || as.hasCustomName() || as.isInvisible()) continue;
            Type type = Type.ofHelmet(ItemUtil.skyblockId(as.getItemBySlot(EquipmentSlot.HEAD)));
            if (type == null) continue;
            Corpse c = find(as.blockPosition());
            if (c == null) {
                c = new Corpse(nextId++, as.blockPosition(), type);
                corpses.add(c);
            }
            if (!c.seen && mc.player.hasLineOfSight(as)) {
                c.seen = true;
                announce(c);
            }
        }
        if (cfg().mineshaftWaypoints && cfg().mineshaftAutoShareCorpses
                && com.teslamaps.utils.PartyTracker.inParty()) autoShare(mc.player.blockPosition());
    }

    // SkyHanni's CorpseSharing: the corpse you stand at goes to party chat once, unless someone already sent it
    private static void autoShare(BlockPos player) {
        for (Corpse c : corpses) {
            if (!c.seen || c.shared || c.pos.distSqr(player) > 25) continue;
            c.shared = true;
            boolean known = false;
            for (BlockPos p : sharedByOthers) if (p.distSqr(c.pos) <= 25) { known = true; break; }
            if (!known) Announce.send(Announce.PARTY, shareLine(c));
            return;
        }
    }

    private static String shareLine(Corpse c) {
        BlockPos p = c.pos.above();
        return "x: " + (p.getX() + 1) + ", y: " + p.getY() + ", z: " + (p.getZ() + 1) + " | (" + c.type.name + " Corpse)";
    }

    /** Share line of the nearest seen corpse within {@code range} blocks, or null. */
    public static String nearestShareLine(BlockPos player, double range) {
        Corpse pick = null;
        double best = range * range;
        for (Corpse c : corpses) {
            if (!c.seen) continue;
            double d = c.pos.distSqr(player);
            if (d <= best) {
                best = d;
                pick = c;
            }
        }
        return pick == null ? null : shareLine(pick);
    }

    public static double nearestDistSq(BlockPos player) {
        double best = Double.MAX_VALUE;
        for (Corpse c : corpses) if (c.seen) best = Math.min(best, c.pos.distSqr(player));
        return best;
    }

    private static Corpse find(BlockPos pos) {
        for (Corpse c : corpses) if (c.pos.equals(pos)) return c;
        return null;
    }

    private static void announce(Corpse c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos p = c.pos.above();
        boolean canShare = !Announce.NOBODY.equals(cfg().corpseFinderChannel);
        Component line = Component.literal("§b[TeslaMaps] §7Found a " + c.type.code + c.type.name + " Corpse §7at §f"
                        + p.getX() + " " + p.getY() + " " + p.getZ() + "§7!")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/tmap corpse share " + c.id))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(canShare
                                ? "§aClick to share in " + cfg().corpseFinderChannel.toLowerCase() + " chat"
                                : "§7Set a Share Channel in Mining to share"))));
        mc.player.sendSystemMessage(line);
    }

    /** /tmap corpse share [id]: the given corpse, or the nearest seen one. */
    public static boolean share(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Corpse pick = null;
        double best = Double.MAX_VALUE;
        for (Corpse c : corpses) {
            if (id >= 0 ? c.id != id : !c.seen) continue;
            double d = c.pos.distSqr(mc.player.blockPosition());
            if (d < best) {
                best = d;
                pick = c;
            }
        }
        if (pick == null) return false;
        Announce.send(cfg().corpseFinderChannel, shareLine(pick));
        return true;
    }

    private static void onChat(String message) {
        if (corpses.isEmpty() && !waypointMode()) return;
        String t = StringUtil.strip(message).trim();
        Matcher loot = LOOT.matcher(t);
        if (loot.matches()) {
            markLooted(loot.group(1));
            return;
        }
        if (!(cfg().corpseFinderParseChat || waypointMode()) || t.startsWith("[TeslaMaps]")) return;
        PlayerChat.Msg msg = PlayerChat.current();
        if (msg == null || msg.own()) return;
        Matcher m = COORDS.matcher(msg.message());
        if (!m.find()) return;
        BlockPos pos;
        try {
            pos = new BlockPos(Integer.parseInt(m.group(1)) - 1, Integer.parseInt(m.group(2)) - 1,
                    Integer.parseInt(m.group(3)) - 1);
        } catch (NumberFormatException e) {
            return;
        }
        sharedByOthers.add(pos);
        Corpse c = find(pos);
        if (c != null) {
            c.seen = true;
            return;
        }
        if (!waypointMode() || !CorpseESP.inMineshaft()) return;
        Matcher tm = SHARED_TYPE.matcher(msg.message());
        if (!tm.find()) return;
        Type type = null;
        for (Type ty : Type.values()) if (ty.name.equalsIgnoreCase(tm.group(1))) type = ty;
        if (type == null) return;
        Corpse shared = new Corpse(nextId++, pos, type);
        shared.seen = true;
        shared.shared = true;
        corpses.add(shared);
    }

    private static void markLooted(String typeName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Corpse pick = null;
        double best = Double.MAX_VALUE;
        for (Corpse c : corpses) {
            if (c.looted || !c.type.name().equals(typeName)) continue;
            double d = c.pos.distSqr(mc.player.blockPosition());
            if (d < best) {
                best = d;
                pick = c;
            }
        }
        if (pick != null) pick.looted = true;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (corpses.isEmpty()) return;
        boolean outline = cfg().corpseFinder, waypoints = waypointMode();
        for (Corpse c : corpses) {
            if (!c.seen) continue;
            if (waypoints) {
                ESPRenderer.drawFilledBox(pose, new AABB(c.pos), 0x66000000 | c.type.color, cam, true);
                ESPRenderer.drawText(pose, (c.looted ? "§a" : "§e") + c.type.name + " Corpse",
                        new Vec3(c.pos.getX() + 0.5, c.pos.getY() + 2.5, c.pos.getZ() + 0.5), 1f, cam);
            }
            if (!outline || c.looted) continue;
            AABB box = new AABB(c.pos.getX() + 0.1, c.pos.getY(), c.pos.getZ() + 0.1,
                    c.pos.getX() + 0.9, c.pos.getY() + 2.0, c.pos.getZ() + 0.9);
            ESPRenderer.drawBoxOutline(pose, box, c.type.color, 2f, cam, false);
        }
    }
}
