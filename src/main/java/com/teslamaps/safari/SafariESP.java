package com.teslamaps.safari;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.render.ESPRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Critters carry their name on an armor stand, so one nametag scan covers all 37 of them and
// gives us the rarity colour for free. Bee nests are a plain block scan, the Honeybug spawns
// from punching one.
public class SafariESP {

    private record Marker(AABB box, int color, String label, boolean tracer) {}

    private static final List<Marker> critters = new ArrayList<>();
    private static final List<BlockPos> nests = new ArrayList<>();
    private static final List<BlockPos> walls = new ArrayList<>();
    private static final List<Cluster> spots = new ArrayList<>();
    private static final java.util.Set<Long> looted = new java.util.HashSet<>();
    private static long lastNestScan = 0;
    private static SafariData.Biome lastNestBiome = null;
    private static int spotScanTimer = 0;

    /** A nest you already punched keeps its box otherwise, the scan finds it again every 5s. */
    public static void onNestClick(BlockPos pos) {
        looted.add(pos.asLong());
    }

    public static void tick() {
        critters.clear();
        walls.clear();
        if (!SafariData.inSafari() || LegitMode.blocksCheats()) {
            spots.clear();
            nests.clear();
            looted.clear();
            spotScanTimer = 0;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        // the sweep also feeds the recatch pin, so it runs for that alone too
        if (c.safariCritterESP || c.safariRecatchSpot) scanCritters(mc, c);
        if (c.safariCritterESP) {
            scanRockmites(mc, c);
            scanHidden(mc, c);
        }
        if (c.safariWallMarkers) SafariWalls.collect(mc, walls);
        if (c.safariNestESP) scanNests(mc);
        else nests.clear();   // without this the last scan kept drawing after switching it off
        if (!c.critterSpotESP) {
            spots.clear();
            spotScanTimer = 0;
        } else if (--spotScanTimer <= 0) {   // drops are static, no need to rescan per tick
            spotScanTimer = 10;
            spots.clear();
            scanSpots(mc);
        }
    }

    // a floor drop is a cluster of 3-4 item_display entities holding a re-modelled string item
    private static void scanSpots(Minecraft mc) {
        SafariData.Biome here = SafariData.currentBiome();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Display.ItemDisplay id)) continue;
            SlotAccess slot = id.getSlot(0);
            if (slot == null || slot.get().getItem() != Items.STRING) continue;
            if (here != null && SafariData.biomeAt(e.blockPosition()) != here) continue;

            Vec3 p = e.position();
            boolean merged = false;
            for (int i = 0; i < spots.size(); i++) {
                Cluster cl = spots.get(i);
                if (cl.center().distanceToSqr(p) < 9) {
                    spots.set(i, cl.add(p));
                    merged = true;
                    break;
                }
            }
            if (!merged) spots.add(new Cluster(p, 1));
        }
    }

    /**
     * A Rockmite comes out of a mound you break, and a mound is an interaction entity - a bare
     * hitbox, no nametag and no model, so the nametag scan never sees one. Ported from
     * critterMod's MoundSpotter: the size band alone is weak, the falling fish in the waterfalls
     * wear interaction boxes too, so a mound also has to sit on the Cavern floor, be centred on
     * a block (a falling fish is at whatever fraction of a block it has reached) and have
     * nothing inside it (a fish is a mob the box is drawn around, a mound is only the box).
     */
    private static final double MOUND_MIN_W = 0.35, MOUND_MAX_W = 1.10;
    private static final double MOUND_MIN_H = 0.25, MOUND_MAX_H = 0.95;
    private static final double MOUND_MAX_Y = 65.0;
    private static final double MOUND_CENTRE_TOLERANCE = 0.05;

    private static void scanRockmites(Minecraft mc, TeslaMapsConfig c) {
        SafariData.Critter rockmite = SafariData.byName("Rockmite");
        if (rockmite == null || SafariData.currentBiome() != rockmite.biome()) return;
        if (c.safariOnlyMissing && SafariTracker.hasCaught(rockmite)) return;

        List<Entity> mounds = new ArrayList<>();
        List<Entity> creatures = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getType() != EntityType.INTERACTION) {
                if (isCreature(e)) creatures.add(e);
                continue;
            }
            if (e.getY() > MOUND_MAX_Y) continue;
            AABB box = e.getBoundingBox();
            if (!moundSized(box.getXsize(), box.getYsize())) continue;
            if (offCentre(e.getX()) || offCentre(e.getZ())) continue;
            if (SafariData.biomeAt(e.blockPosition()) != rockmite.biome()) continue;
            mounds.add(e);
        }

        int color = rockmite.rarity().color;
        String label = c.safariNames ? rarityCode(rockmite) + "Mound" : null;
        for (Entity m : mounds) {
            if (wrapsCreature(m, creatures)) continue;
            critters.add(new Marker(m.getBoundingBox(), color, label, c.safariTracers));
        }
    }

    private static boolean moundSized(double w, double h) {
        return w >= MOUND_MIN_W && w <= MOUND_MAX_W && h >= MOUND_MIN_H && h <= MOUND_MAX_H && h <= w + 0.1;
    }

    private static boolean offCentre(double coord) {
        return Math.abs(coord - (Math.floor(coord) + 0.5)) >= MOUND_CENTRE_TOLERANCE;
    }

    /** A box with a mob in it is the wrapper around that mob, not a mound. */
    private static boolean wrapsCreature(Entity box, List<Entity> creatures) {
        for (Entity e : creatures) {
            if (Math.abs(e.getX() - box.getX()) > 0.35) continue;
            if (Math.abs(e.getZ() - box.getZ()) > 0.35) continue;
            if (Math.abs(e.getY() - box.getY()) <= 1.0) return true;
        }
        return false;
    }

    /** Every nearby interaction box by size, with how many pass each mound rule. */
    public static List<String> debugMounds() {
        Minecraft mc = Minecraft.getInstance();
        List<String> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) return out;

        List<Entity> creatures = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getType() != EntityType.INTERACTION && isCreature(e)) creatures.add(e);
        }

        java.util.Map<String, int[]> bySize = new java.util.TreeMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getType() != EntityType.INTERACTION) continue;
            if (e.distanceToSqr(mc.player) > 64 * 64) continue;
            AABB box = e.getBoundingBox();
            String size = String.format("%.2f x %.2f", box.getXsize(), box.getYsize());
            int[] n = bySize.computeIfAbsent(size, k -> new int[4]);
            n[0]++;
            if (e.getY() <= MOUND_MAX_Y) n[1]++;
            if (!offCentre(e.getX()) && !offCentre(e.getZ())) n[2]++;
            if (e.getY() <= MOUND_MAX_Y && !offCentre(e.getX()) && !offCentre(e.getZ())
                    && !wrapsCreature(e, creatures)) n[3]++;
        }

        out.add("§b=== interaction boxes within 64 ===");
        bySize.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0] - a.getValue()[0])
                .forEach(e -> {
                    String[] wh = e.getKey().split(" x ");
                    boolean sized = moundSized(Double.parseDouble(wh[0]), Double.parseDouble(wh[1]));
                    int[] n = e.getValue();
                    out.add(String.format("  %-14s x%-3d %s", e.getKey(), n[0], sized
                            ? String.format("§a<- mound-sized: %d counted (low %d, centred %d, of %d)",
                                    n[3], n[1], n[2], n[0])
                            : ""));
                });
        if (bySize.isEmpty()) out.add("  none");
        return out;
    }

    /** Skips the scaffolding entities Hypixel builds its props out of. */
    private static boolean isCreature(Entity e) {
        EntityType<?> t = e.getType();
        return t != EntityType.ARMOR_STAND && t != EntityType.ITEM_DISPLAY
                && t != EntityType.BLOCK_DISPLAY && t != EntityType.TEXT_DISPLAY
                && t != EntityType.PLAYER && t != EntityType.ITEM;
    }

    /**
     * Hideonfloor, Duplico and Bloodbat sit there disguised and only get their nametag once they
     * are revealed, so the tag scan never boxes them while they still matter.
     */
    private static void scanHidden(Minecraft mc, TeslaMapsConfig c) {
        SafariData.Biome here = SafariData.currentBiome();
        if (here != SafariData.Biome.FOREST && here != SafariData.Biome.HAUNTED) return;

        for (Entity e : mc.level.entitiesForRendering()) {
            SafariData.Critter critter = hiddenCritter(e, here);
            if (critter == null) continue;
            if (c.safariOnlyMissing && SafariTracker.hasCaught(critter)) continue;
            if (SafariData.biomeAt(e.blockPosition()) != here) continue;
            if (hasCritterTag(mc, e)) continue;   // already boxed by the nametag scan

            AABB box = e.getBoundingBox();
            if (box.getXsize() < 0.1 || box.getYsize() < 0.1) box = box.inflate(0.4, 0.4, 0.4);
            String label = c.safariNames ? rarityCode(critter) + critter.name() : null;
            critters.add(new Marker(box, critter.rarity().color, label, c.safariTracers));
        }
    }

    private static SafariData.Critter hiddenCritter(Entity e, SafariData.Biome here) {
        if (here == SafariData.Biome.FOREST) {
            if (e instanceof Shulker s && s.getColor() == DyeColor.GREEN) return SafariData.byName("Hideonfloor");
            if (e instanceof Display.ItemDisplay id && itemOf(id).is(Items.GREEN_SHULKER_BOX)) {
                return SafariData.byName("Hideonfloor");
            }
            return null;
        }
        if (e instanceof Bat) return SafariData.byName("Bloodbat");
        // Gimmiegold uses a head, a moving Hideonwall the purple box - everything else on the
        // three-tick interpolation is a Duplico
        if (e instanceof Display.ItemDisplay id && id.getPosRotInterpolationDuration() == 3) {
            ItemStack stack = itemOf(id);
            if (!stack.is(Items.PLAYER_HEAD) && !stack.is(Items.PURPLE_SHULKER_BOX)) {
                return SafariData.byName("Duplico");
            }
        }
        return null;
    }

    private static ItemStack itemOf(Display.ItemDisplay id) {
        SlotAccess slot = id.getSlot(0);
        return slot == null ? ItemStack.EMPTY : slot.get();
    }

    /** Rockmites are the only Cavern critter without a nametag, so a tagged head is not one. */
    private static boolean hasCritterTag(Minecraft mc, Entity head) {
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class,
                head.getBoundingBox().inflate(2.0, 2.5, 2.0), a -> a.getCustomName() != null)) {
            if (matchCritter(com.teslamaps.utils.StringUtil.strip(as.getCustomName().getString())) != null) return true;
        }
        return false;
    }

    private record Cluster(Vec3 sum, int n) {
        Vec3 center() { return sum.scale(1.0 / n); }
        Cluster add(Vec3 p) { return new Cluster(sum.add(p), n + 1); }
    }

    private static void scanCritters(Minecraft mc, TeslaMapsConfig c) {
        int sparklingColor = TeslaMapsConfig.parseColor(c.colorSafariSparkling);
        SafariData.Biome here = SafariData.currentBiome();
        RecatchSpots.beginSweep();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand as)) continue;
            if (as.getCustomName() == null) continue;

            String name = com.teslamaps.utils.StringUtil.strip(as.getCustomName().getString());
            SafariData.Critter critter = matchCritter(name);
            if (critter == null) continue;
            if (here != null && SafariData.biomeAt(as.blockPosition()) != here) continue;

            // a capsule mid-capture wears the species name as well, but has no body under it
            Entity body = bodyEntity(mc, as);
            if (body != null) RecatchSpots.see(critter, body.getBoundingBox());
            if (!c.safariCritterESP) continue;

            boolean sparkling = name.contains("SPARKLING");
            if (c.safariOnlyMissing && !sparkling && SafariTracker.hasCaught(critter)) continue;

            int color = sparkling ? sparklingColor : critter.rarity().color;
            String label = null;
            if (sparkling) label = "§d§lSPARKLING " + critter.name();
            else if (c.safariNames || isHeadliner(critter)) label = rarityCode(critter) + critter.name();

            // Hideyho hides, so it gets a line of its own without tracing everything else
            boolean tracer = c.safariTracers
                    || (c.safariHideyhoTracer && critter.name().equals("Hideyho"));
            critters.add(new Marker(bodyOf(mc, as), color, label, tracer));
        }
    }

    /** Hideyho hides and the legendaries are one-per-run, so those always get a label. */
    private static boolean isHeadliner(SafariData.Critter c) {
        return c.rarity() == SafariData.Rarity.LEGENDARY || c.name().equals("Hideyho");
    }

    private static String rarityCode(SafariData.Critter c) {
        return switch (c.rarity()) {
            case COMMON -> "§f";
            case UNCOMMON -> "§a";
            case RARE -> "§9";
            case EPIC -> "§5";
            case LEGENDARY -> "§6§l";
        };
    }

    // the nametag rides above the critter, so box the body under it instead of the stand itself
    private static Entity bodyEntity(Minecraft mc, ArmorStand tag) {
        AABB search = tag.getBoundingBox().inflate(1.5, 2.5, 1.5);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, search, x -> true)) {
            if (e instanceof ArmorStand || e instanceof Display.TextDisplay) continue;
            if (e == mc.player) continue;
            double d = e.position().distanceToSqr(tag.position());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private static AABB bodyOf(Minecraft mc, ArmorStand tag) {
        Entity best = bodyEntity(mc, tag);
        if (best != null) {
            AABB b = best.getBoundingBox();
            // display entities report a zero-size box, give those something visible
            if (b.getXsize() < 0.1 || b.getYsize() < 0.1) return b.inflate(0.4, 0.4, 0.4);
            return b;
        }
        return new AABB(tag.getX() - 0.5, tag.getY() - 1.6, tag.getZ() - 0.5,
                tag.getX() + 0.5, tag.getY() + 0.4, tag.getZ() + 0.5);
    }

    private static SafariData.Critter matchCritter(String nametag) {
        for (SafariData.Critter c : SafariData.ALL) {
            if (nametag.contains(c.name())) return c;
        }
        return null;
    }

    // findBlocks walks the section palettes, so this stays cheap even over a wide chunk radius
    private static void scanNests(Minecraft mc) {
        SafariData.Biome here = SafariData.currentBiome();
        long now = System.currentTimeMillis();
        // a biome change has to show up right away, not after the next throttled scan
        if (here == lastNestBiome && now - lastNestScan < 5000) return;
        lastNestScan = now;
        lastNestBiome = here;

        nests.clear();
        // Honeybugs only live in the Forest, and the chunk radius reaches well past its border
        if (here != SafariData.Biome.FOREST) return;

        int cx = mc.player.chunkPosition().x();
        int cz = mc.player.chunkPosition().z();
        for (int x = cx - 6; x <= cx + 6; x++) {
            for (int z = cz - 6; z <= cz + 6; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, false);
                if (chunk == null) continue;
                chunk.findBlocks(state -> state.is(Blocks.BEE_NEST), (pos, state) -> {
                    if (SafariData.biomeAt(pos) == SafariData.Biome.FOREST) nests.add(pos.immutable());
                });
            }
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        AABB pin = RecatchSpots.pinned();
        if (critters.isEmpty() && nests.isEmpty() && spots.isEmpty() && walls.isEmpty() && pin == null) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean filled = c.safariFilled;

        if (pin != null && c.safariRecatchSpot) {
            int pinColor = TeslaMapsConfig.parseColor(c.colorSafariRecatch) | 0xFF000000;
            if (filled) ESPRenderer.drawFilledBox(matrices, pin, pinColor & 0x00FFFFFF, cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, pin, pinColor, 2.0f, cameraPos, true);
            SafariData.Critter pinned = RecatchSpots.pinnedCritter();
            if (pinned != null) {
                Vec3 mid = pin.getCenter();
                ESPRenderer.drawText(matrices, "§eComes back: §f" + pinned.name(),
                        new Vec3(mid.x, pin.maxY + 0.5, mid.z), 1.5f, cameraPos);
            }
        }

        int wallColor = TeslaMapsConfig.parseColor(c.colorSafariWall) | 0xFF000000;
        for (BlockPos p : walls) {
            AABB box = new AABB(p);
            if (filled) ESPRenderer.drawFilledBox(matrices, box, wallColor & 0x00FFFFFF, cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, box, wallColor, 2.0f, cameraPos, true);
        }

        for (Marker m : critters) {
            if (filled) ESPRenderer.drawFilledBox(matrices, m.box(), m.color(), cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, m.box(), m.color() | 0xFF000000, 2.0f, cameraPos, true);
            if (m.label() == null) continue;

            Vec3 mid = m.box().getCenter();
            Vec3 at = new Vec3(mid.x, m.box().maxY + 0.5, mid.z);
            ESPRenderer.drawText(matrices, m.label(), at, 1.5f, cameraPos);
            if (m.tracer()) {
                ESPRenderer.drawTracerFromCamera(matrices, mid, m.color() | 0xFF000000, cameraPos);
            }
        }

        int nestColor = TeslaMapsConfig.parseColor(c.colorSafariNest);
        for (BlockPos p : nests) {
            if (looted.contains(p.asLong())) continue;
            AABB box = new AABB(p);
            if (filled) ESPRenderer.drawFilledBox(matrices, box, nestColor, cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, box, nestColor | 0xFF000000, 2.0f, cameraPos, true);
        }

        int spotColor = TeslaMapsConfig.parseColor(c.colorCritterSpot) | 0xFF000000;
        for (Cluster cl : spots) {
            Vec3 p = cl.center();
            AABB box = new AABB(p.x - 0.5, p.y - 0.05, p.z - 0.5, p.x + 0.5, p.y + 0.5, p.z + 0.5);
            if (filled) ESPRenderer.drawFilledBox(matrices, box, spotColor & 0x00FFFFFF, cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, box, spotColor, 2.0f, cameraPos, true);
        }
    }
}
