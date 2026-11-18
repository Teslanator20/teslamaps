package com.teslamaps.safari;

// Ported from Sparkling Mutuals' SafariFloorDrops / FloorDropState / SafariEsp (floor part)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real type of every Safari floor drop, boxed only while that type is still needed. */
public final class FloorDropDecoder {

    private static final long BANNER_MS = 5000;
    private static final String[] BIRDS = {"Bluebird", "Parakeet", "Macaw"};

    private record Shown(AABB tile, int color, String label, Vec3 at, float scale) {}

    private static final FloorDropPredictor predictor = new FloorDropPredictor();
    private static List<FloorDropPredictor.Drop> live = List.of();
    private static List<Shown> shown = List.of();
    private static final Map<FloorDropPredictor.Type, Integer> acquired = new EnumMap<>(FloorDropPredictor.Type.class);
    private static final Map<FloorDropPredictor.Type, Integer> peak = new EnumMap<>(FloorDropPredictor.Type.class);
    private static final Set<Integer> collected = new HashSet<>();
    private static final Map<Integer, Integer> missing = new HashMap<>();
    private static final Set<SafariData.Biome> announced = EnumSet.noneOf(SafariData.Biome.class);
    private static boolean gemzieOpened;
    private static boolean doomSummoned;
    private static Level level;
    private static int ticks;
    private static int slowTicks;

    private static String banner;
    private static int bannerColor;
    private static long bannerAt;

    private FloorDropDecoder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FloorDropDecoder").section("Critter Safari").legit().when(() -> cfg().safariFloorDecoder)
                .tick(FloorDropDecoder::tick)
                .world(FloorDropDecoder::render)
                .hud("floordropbanner", FloorDropDecoder::renderBanner)
                .serverChat(FloorDropDecoder::onChat)
                .config("Critter Safari", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Floor Drop Decoder");
                    b.toggle("Floor Drop Decoder", () -> c.safariFloorDecoder, v -> c.safariFloorDecoder = v).cheat()
                            .desc("Tells every floor drop apart before you pick it up (gem, bird food, incense, coin, icebreaker) from its entity ids. Boxes only the types you still need.")
                            .tags("floor", "drop", "gem", "incense", "coin", "icebreaker", "food");
                    b.toggle("  Show Every Type", () -> c.safariFloorDecoderAll, v -> c.safariFloorDecoderAll = v)
                            .desc("Also the types you already have enough of");
                    b.toggle("  Reward Drops", () -> c.safariFloorDecoderRewards, v -> c.safariFloorDecoderRewards = v)
                            .desc("Also the XP / Essence / Shard drops");
                    b.toggle("  Only In Your Biome", () -> c.safariFloorDecoderBiome, v -> c.safariFloorDecoderBiome = v);
                    b.toggle("  Biome Done Banner", () -> c.safariFloorDecoderBanner, v -> c.safariFloorDecoderBanner = v)
                            .desc("\"<Biome> Floor Drops Done\" once every drop of the biome you stand in is gone");
                    b.color("  Drop Color", () -> c.safariFloorDecoderColor, v -> c.safariFloorDecoderColor = v);
                    b.color("  Reward Color", () -> c.safariFloorDecoderRewardColor, v -> c.safariFloorDecoderRewardColor = v);
                    b.slider("  Label Size", 0.5f, 3.0f, () -> c.safariFloorDecoderScale, v -> c.safariFloorDecoderScale = v, 1);
                });
    }

    private static void reset() {
        predictor.reset();
        live = List.of();
        shown = List.of();
        acquired.clear();
        peak.clear();
        collected.clear();
        missing.clear();
        announced.clear();
        gemzieOpened = false;
        doomSummoned = false;
        ticks = 0;
        slowTicks = 0;
    }

    /** Floor Spot ESP leaves the drops this one boxes to it. */
    public static boolean covers(Vec3 p) {
        if (!cfg().safariFloorDecoder || com.teslamaps.features.LegitMode.blocksCheats()) return false;
        for (Shown s : shown) {
            double dx = s.at().x - p.x, dy = s.at().y - 1.2 - p.y, dz = s.at().z - p.z;
            if (dx * dx + dy * dy + dz * dz < 4.0) return true;
        }
        return false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            reset();
            level = mc.level;
        }
        if (!SafariData.inSafari()) {
            if (!shown.isEmpty()) shown = List.of();
            return;
        }
        if (++ticks >= 5) {
            ticks = 0;
            scan(mc);
        }
        if (++slowTicks >= 20) {
            slowTicks = 0;
            inventory(mc);
            SafariData.Biome here = SafariData.currentBiome();
            if (here != null && cfg().safariFloorDecoderBanner && !announced.contains(here) && bannerReady(mc, here)) {
                announced.add(here);
                banner = here.label + " Floor Drops Done";
                bannerColor = SafariData.argb(here);
                bannerAt = System.currentTimeMillis();
            }
        }
    }

    private static void scan(Minecraft mc) {
        List<FloorDropPredictor.Display> displays = new ArrayList<>();
        for (Display.ItemDisplay d : EntityCache.byType(Display.ItemDisplay.class)) {
            if (!activeDisplay(d)) continue;
            ItemStack stack = itemOf(d);
            String desc = BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + StringUtil.strip(stack.getHoverName().getString());
            displays.add(new FloorDropPredictor.Display(d.getId(), d.getX(), d.getY(), d.getZ(), desc));
        }
        live = predictor.scan(displays, System.currentTimeMillis());

        TeslaMapsConfig c = cfg();
        SafariData.Biome here = SafariData.currentBiome();
        int color = TeslaMapsConfig.parseColor(c.safariFloorDecoderColor);
        int reward = TeslaMapsConfig.parseColor(c.safariFloorDecoderRewardColor);
        List<Shown> out = new ArrayList<>();
        for (FloorDropPredictor.Drop d : live) {
            if (!eligible(mc, d, here)) continue;
            int bx = (int) Math.floor(d.x), by = (int) Math.floor(d.y), bz = (int) Math.floor(d.z);
            int col = d.type == FloorDropPredictor.Type.GENERIC ? reward : color;
            out.add(new Shown(new AABB(bx, by + 1.0, bz, bx + 1.0, by + 1.0, bz + 1.0), col, d.type.label,
                    new Vec3(d.x, d.y + 1.2, d.z), c.safariFloorDecoderScale));
        }
        shown = out;
    }

    private static boolean activeDisplay(Entity e) {
        return e instanceof Display.ItemDisplay d && !d.isRemoved() && !d.isInvisible() && itemOf(d).is(Items.STRING);
    }

    private static ItemStack itemOf(Display.ItemDisplay d) {
        SlotAccess slot = d.getSlot(0);
        return slot == null ? ItemStack.EMPTY : slot.get();
    }

    private static boolean eligible(Minecraft mc, FloorDropPredictor.Drop d, SafariData.Biome here) {
        if (d.actual != null || d.type == null || d.biome == null) return false;
        for (int i = 0; i < 3; i++) {
            Entity e = mc.level.getEntity(d.id + i);
            if (e == null || e.isRemoved()) return false;
        }
        if (!selected(d)) return false;
        return !cfg().safariFloorDecoderBiome || here == d.biome;
    }

    private static boolean selected(FloorDropPredictor.Drop d) {
        FloorDropPredictor.Type t = d.type;
        if (t == FloorDropPredictor.Type.GENERIC) return cfg().safariFloorDecoderRewards;
        if (t == FloorDropPredictor.Type.INCENSE && doomSummoned) return false;
        if (cfg().safariFloorDecoderAll) return true;
        return needed(t) && biomeEnabled(d.biome);
    }

    private static int amount(FloorDropPredictor.Type t) {
        return Math.max(acquired.getOrDefault(t, 0), peak.getOrDefault(t, 0));
    }

    private static boolean caught(String name) {
        SafariData.Critter c = SafariData.byName(name);
        return c != null && SafariTracker.hasCaught(c);
    }

    // no run modes here: a normal run, food and coins always count, gems and icebreaker until you hold one
    private static boolean needed(FloorDropPredictor.Type t) {
        return switch (t) {
            case LIME, PURPLE, ORANGE, ICEBREAKER -> amount(t) == 0;
            case COIN -> !caught("Gimmiegold");
            case INCENSE -> amount(t) < 4 && !caught("Doomspiral");
            case WORM, BERRY, SEEDS -> true;
            case GENERIC -> false;
        };
    }

    private static boolean biomeEnabled(SafariData.Biome biome) {
        return switch (biome) {
            case CAVERN -> !gemzieOpened && !caught("Gemzie")
                    && (needed(FloorDropPredictor.Type.LIME) || needed(FloorDropPredictor.Type.PURPLE) || needed(FloorDropPredictor.Type.ORANGE));
            case FOREST -> {
                for (String b : BIRDS) if (!caught(b)) yield true;
                yield false;
            }
            case HAUNTED -> needed(FloorDropPredictor.Type.COIN) || needed(FloorDropPredictor.Type.INCENSE);
            case ICY -> needed(FloorDropPredictor.Type.ICEBREAKER);
        };
    }

    private static void inventory(Minecraft mc) {
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        Map<FloorDropPredictor.Type, Integer> held = new EnumMap<>(FloorDropPredictor.Type.class);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String name = StringUtil.strip(s.getHoverName().getString()).trim();
            for (FloorDropPredictor.Type t : FloorDropPredictor.Type.values()) {
                if (t != FloorDropPredictor.Type.GENERIC && name.equalsIgnoreCase(t.label)) held.merge(t, s.getCount(), Integer::sum);
            }
        }
        for (var e : held.entrySet()) peak.merge(e.getKey(), e.getValue(), Math::max);
    }

    // every known drop of the biome picked up, or gone from within 24 blocks on two checks in a row
    private static boolean bannerReady(Minecraft mc, SafariData.Biome biome) {
        boolean any = false;
        for (FloorDropPredictor.Drop d : predictor.known()) {
            if (d.biome != biome) continue;
            any = true;
            if (d.actual != null) {
                collected.add(d.id);
                continue;
            }
            boolean present = false;
            for (int i = 0; i < 3 && !present; i++) present = activeDisplay(mc.level.getEntity(d.id + i));
            if (present) {
                collected.remove(d.id);
                missing.remove(d.id);
            } else if (mc.player.distanceToSqr(d.x, d.y, d.z) <= 576.0 && mc.level.hasChunkAt(BlockPos.containing(d.x, d.y, d.z))) {
                if (missing.merge(d.id, 1, Integer::sum) >= 2) collected.add(d.id);
            }
        }
        if (!any) return false;
        for (FloorDropPredictor.Drop d : predictor.known()) {
            if (d.biome == biome && !collected.contains(d.id)) return false;
        }
        return true;
    }

    private static void onChat(String raw) {
        if (!SafariData.inSafari()) return;
        String s = StringUtil.strip(raw).trim();
        if (s.startsWith("FLOOR DROP!")) {
            FloorDropPredictor.Pickup p = FloorDropPredictor.Pickup.parse(s);
            if (p == null) return;
            acquired.merge(p.type(), p.quantity(), Integer::sum);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) predictor.pickup(p, mc.player.getX(), mc.player.getY(), mc.player.getZ(), System.currentTimeMillis());
        } else if (s.startsWith("A rumbling sound can be heard")) {
            gemzieOpened = true;
        } else if (s.startsWith("Your ritual summoned a Doomspiral") || s.equals("Something stirs in the Haunted Biome...")) {
            doomSummoned = true;
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (shown.isEmpty() || SparklingFocus.focusActive()) return;
        boolean through = !cfg().safariLegitMode;
        for (Shown s : shown) {
            ESPRenderer.drawBoxOutline(pose, s.tile(), s.color() | 0xFF000000, 2.0f, cam, through);
            int c = s.color();
            ESPRenderer.drawFilledBox(pose, s.tile(), (c & 0xFFFFFF) | ((c >>> 25) << 24), cam, through);
        }
        for (Shown s : shown) {
            ESPRenderer.drawText(pose, s.label(), s.at(), s.scale(), cam, true, through, s.color() | 0xFF000000);
        }
    }

    private static void renderBanner(GuiGraphicsExtractor g, DeltaTracker d) {
        if (banner == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long age = System.currentTimeMillis() - bannerAt;
        if (age > BANNER_MS) {
            banner = null;
            return;
        }
        int alpha = age > BANNER_MS - 1000 ? (int) (0xFF * (BANNER_MS - age) / 1000.0) : 0xFF;
        float scale = 2.0f;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.centeredText(mc.font, banner, (int) (g.guiWidth() / (2 * scale)), (int) (g.guiHeight() * 0.30 / scale),
                (Math.max(alpha, 8) << 24) | (bannerColor & 0xFFFFFF));
        g.pose().popMatrix();
    }
}
