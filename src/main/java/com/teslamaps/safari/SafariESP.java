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
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// Critters carry their name on an armor stand, so one nametag scan covers all 37 of them and
// gives us the rarity colour for free. Bee nests are a plain block scan, the Honeybug spawns
// from punching one.
public class SafariESP {

    private record Marker(AABB box, int color, String label) {}

    private static final List<Marker> critters = new ArrayList<>();
    private static final List<BlockPos> nests = new ArrayList<>();
    private static final List<Cluster> spots = new ArrayList<>();
    private static long lastNestScan = 0;
    private static SafariData.Biome lastNestBiome = null;

    public static void tick() {
        critters.clear();
        spots.clear();
        if (!SafariData.inSafari() || LegitMode.blocksCheats()) {
            nests.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.safariCritterESP) scanCritters(mc, c);
        if (c.safariNestESP) scanNests(mc);
        if (c.critterSpotESP) scanSpots(mc);
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

    private record Cluster(Vec3 sum, int n) {
        Vec3 center() { return sum.scale(1.0 / n); }
        Cluster add(Vec3 p) { return new Cluster(sum.add(p), n + 1); }
    }

    private static void scanCritters(Minecraft mc, TeslaMapsConfig c) {
        int sparklingColor = TeslaMapsConfig.parseColor(c.colorSafariSparkling);
        SafariData.Biome here = SafariData.currentBiome();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand as)) continue;
            if (as.getCustomName() == null) continue;

            String name = as.getCustomName().getString().replaceAll("(?i)§[0-9A-FK-OR]", "");
            SafariData.Critter critter = matchCritter(name);
            if (critter == null) continue;
            if (here != null && SafariData.biomeAt(as.blockPosition()) != here) continue;

            boolean sparkling = name.contains("SPARKLING");
            if (c.safariOnlyMissing && !sparkling && SafariTracker.hasCaught(critter)) continue;

            int color = sparkling ? sparklingColor : critter.rarity().color;
            String label = null;
            if (sparkling) label = "§d§lSPARKLING " + critter.name();
            else if (c.safariNames || isHeadliner(critter)) label = rarityCode(critter) + critter.name();

            critters.add(new Marker(bodyOf(mc, as), color, label));
        }

        if (c.safariFastDetect) scanBodies(mc, c, here);
    }

    /**
     * Hypixel spawns the critter body before its nametag armor stand, so waiting for the tag makes
     * boxes show up late. This adds the bodies straight away, by the entity classes each biome uses.
     */
    private static void scanBodies(Minecraft mc, TeslaMapsConfig c, SafariData.Biome here) {
        if (here == null) return;
        int color = TeslaMapsConfig.parseColor(c.colorSafariUnknown);

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || e instanceof ArmorStand) continue;
            if (isFloorSpotDisplay(e)) continue;
            if (!isCritterBody(e, here)) continue;
            if (SafariData.biomeAt(e.blockPosition()) != here) continue;

            AABB box = e.getBoundingBox();
            if (box.getXsize() < 0.1 || box.getYsize() < 0.1) box = box.inflate(0.4, 0.4, 0.4);
            if (alreadyMarked(box)) continue;
            critters.add(new Marker(box, color, null));
        }
    }

    private static boolean isFloorSpotDisplay(Entity e) {
        if (!(e instanceof Display.ItemDisplay id)) return false;
        SlotAccess slot = id.getSlot(0);
        return slot != null && slot.get().getItem() == Items.STRING;
    }

    private static boolean alreadyMarked(AABB box) {
        Vec3 mid = box.getCenter();
        for (Marker m : critters) {
            if (m.box().getCenter().distanceToSqr(mid) < 4) return true;
        }
        return false;
    }

    private static boolean isCritterBody(Entity e, SafariData.Biome biome) {
        return switch (biome) {
            case ICY -> e instanceof SnowGolem || e instanceof PolarBear || e instanceof GlowSquid
                    || e instanceof Goat || e instanceof Dolphin || e instanceof Ravager
                    || e instanceof TropicalFish || e instanceof Display.ItemDisplay;
            case HAUNTED -> e instanceof CaveSpider || e instanceof Bat || e instanceof Phantom
                    || e instanceof Warden || e instanceof Endermite
                    || e instanceof Shulker || e instanceof Display.ItemDisplay;
            case CAVERN -> e instanceof Armadillo || e instanceof Vex || e instanceof Sniffer
                    || e instanceof TropicalFish || e instanceof Silverfish
                    || e instanceof Display.ItemDisplay;
            case FOREST -> e instanceof Fox || e instanceof Bee || e instanceof Parrot
                    || e instanceof Frog || e instanceof Creaking || e instanceof Panda
                    || e instanceof Shulker || e instanceof Display.ItemDisplay;
        };
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
    private static AABB bodyOf(Minecraft mc, ArmorStand tag) {
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
                chunk.findBlocks(state -> state.is(Blocks.BEE_NEST) || state.is(Blocks.BEEHIVE), (pos, state) -> {
                    if (SafariData.biomeAt(pos) == SafariData.Biome.FOREST) nests.add(pos.immutable());
                });
            }
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (critters.isEmpty() && nests.isEmpty() && spots.isEmpty()) return;

        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean filled = c.safariFilled;

        for (Marker m : critters) {
            if (filled) ESPRenderer.drawFilledBox(matrices, m.box(), m.color(), cameraPos, true);
            ESPRenderer.drawBoxOutline(matrices, m.box(), m.color() | 0xFF000000, 2.0f, cameraPos, true);
            if (m.label() == null) continue;

            Vec3 mid = m.box().getCenter();
            Vec3 at = new Vec3(mid.x, m.box().maxY + 0.5, mid.z);
            ESPRenderer.drawText(matrices, m.label(), at, 1.5f, cameraPos);
            if (c.safariTracers) {
                ESPRenderer.drawTracerFromCamera(matrices, mid, m.color() | 0xFF000000, cameraPos);
            }
        }

        int nestColor = TeslaMapsConfig.parseColor(c.colorSafariNest);
        for (BlockPos p : nests) {
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
