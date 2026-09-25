package com.teslamaps.features;

// Ported from NEU's CustomItemEffects.java (hoe, scythe, dirt wand, prismapump) and CrystalOverlay.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FarmToolOverlays {

    private record Crystal(int radius, int rgb) {}

    private static final Map<UUID, Crystal> CRYSTALS = Map.of(
            UUID.fromString("d9c3168a-8654-3dd8-b297-4d3b7e55b95a"), new Crystal(8, 0xDAA520),
            UUID.fromString("949d100c-aa74-3b09-a642-af5529f808aa"), new Crystal(40, 0x6E5A49),
            UUID.fromString("bd79a474-cf07-3f8c-b5a4-98657c33520a"), new Crystal(12, 0x01A552),
            UUID.fromString("2e474ee3-5361-3218-84db-880eb1cface1"), new Crystal(15, 0x1972A6),
            UUID.fromString("7a237e5c-ca9a-3dc1-b1d9-b385fc200aa7"), new Crystal(16, 0x3FFCFF));

    private static final int WATER = 0x5014A0A0;

    private static List<AABB> boxes = List.of();
    private static int boxColor;
    private static List<AABB> rings = List.of();
    private static int[] ringColors = new int[0];
    private static int ticks;
    private static boolean island, garden;
    private static long crystalShown;

    private FarmToolOverlays() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FarmToolOverlays").section("Garden").when(() -> cfg().farmToolOverlays)
                .tick(FarmToolOverlays::tick)
                .world(FarmToolOverlays::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Farming Tool Overlays");
                    b.toggle("Farming Tool Overlays", () -> c.farmToolOverlays, v -> c.farmToolOverlays = v)
                            .desc("Garden / Private Island: boxes on the blocks a farming tool ability would hit.");
                    b.toggle("  Hoe Of Tilling", () -> c.farmToolHoe, v -> c.farmToolHoe = v);
                    b.toggle("  Sam's / Garden Scythe", () -> c.farmToolScythe, v -> c.farmToolScythe = v);
                    b.toggle("  InfiniDirt Wand", () -> c.farmToolDirtWand, v -> c.farmToolDirtWand = v);
                    b.toggle("  Prismapump Water Flow", () -> c.farmToolPrismapump, v -> c.farmToolPrismapump = v)
                            .desc("Water bucket next to a Prismapump: where the pumped water runs.");
                    b.toggle("  Minion Crystal Radius", () -> c.minionCrystalRadius, v -> c.minionCrystalRadius = v)
                            .desc("Ring around placed minion crystals while you hold a crystal (10s after).");
                    b.toggle("    Always Show Crystal Radius", () -> c.minionCrystalAlways, v -> c.minionCrystalAlways = v);
                    b.color("  Overlay Color", () -> c.farmToolColor, v -> c.farmToolColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (++ticks % 20 == 0) {
            garden = SkyblockUtils.isInGarden();
            island = !garden && SkyblockUtils.isInArea("Private Island");
        }
        if (!island && !garden) {
            boxes = List.of();
            rings = List.of();
            return;
        }
        String id = ItemUtil.skyblockId(mc.player.getMainHandItem());
        if (c.minionCrystalRadius && ticks % 10 == 0) crystals(mc, c, id);
        if (ticks % 2 != 0) return;
        List<AABB> out = new ArrayList<>();
        boxColor = TeslaMapsConfig.parseColor(c.farmToolColor);
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && mc.level != null) {
            Level level = mc.level;
            switch (id) {
                case "HOE_OF_GREAT_TILLING", "HOE_OF_GREATER_TILLING" -> {
                    if (c.farmToolHoe) hoe(level, hit.getBlockPos(), id.equals("HOE_OF_GREAT_TILLING") ? 1 : 2, out);
                }
                case "HOE_OF_GREATEST_TILLING" -> {
                    if (c.farmToolHoe) hoeLine(level, hit.getBlockPos(), mc.player.getDirection(), out);
                }
                case "SAM_SCYTHE", "GARDEN_SCYTHE" -> {
                    if (c.farmToolScythe) scythe(level, hit.getBlockPos(), id.equals("SAM_SCYTHE") ? 1 : 2, out);
                }
                case "INFINIDIRT_WAND" -> {
                    if (c.farmToolDirtWand) dirt(level, hit, out);
                }
                case "WATER_BUCKET", "MAGICAL_WATER_BUCKET" -> {
                    if (c.farmToolPrismapump && island && prismapump(level, hit.getBlockPos().relative(hit.getDirection()), out)) boxColor = WATER;
                }
                default -> {}
            }
            if (id.isEmpty() && c.farmToolPrismapump && island && mc.player.getMainHandItem().is(Items.WATER_BUCKET)
                    && prismapump(level, hit.getBlockPos().relative(hit.getDirection()), out)) boxColor = WATER;
        }
        boxes = out;
    }

    private static boolean tillable(Level level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        return st.is(Blocks.DIRT) || (st.is(Blocks.GRASS_BLOCK) && level.getBlockState(p.above()).isAir());
    }

    private static void hoe(Level level, BlockPos target, int r, List<AABB> out) {
        if (!tillable(level, target)) return;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                BlockPos p = target.offset(x, 0, z);
                if (tillable(level, p)) out.add(new AABB(p));
            }
        }
    }

    private static void hoeLine(Level level, BlockPos target, Direction dir, List<AABB> out) {
        if (!tillable(level, target)) return;
        out.add(new AABB(target));
        BlockPos p = target;
        for (int i = 0; i < 100; i++) {
            p = p.relative(dir);
            if (!tillable(level, p)) break;
            out.add(new AABB(p));
        }
    }

    private static boolean scytheBlock(BlockState st) {
        return st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.FLOWERS)
                || st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.FERN) || st.is(Blocks.LARGE_FERN);
    }

    private static void scythe(Level level, BlockPos target, int r, List<AABB> out) {
        if (!scytheBlock(level.getBlockState(target))) return;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = target.offset(x, y, z);
                    if (scytheBlock(level.getBlockState(p))) out.add(new AABB(p));
                }
            }
        }
    }

    private static void dirt(Level level, BlockHitResult hit, List<AABB> out) {
        BlockPos p = hit.getBlockPos().relative(hit.getDirection());
        if (garden && (p.getX() > 239 || p.getX() < -240 || p.getZ() > 239 || p.getZ() < -240 || p.getY() <= 66 || p.getY() > 76)) return;
        if (level.getBlockState(p).isAir()) out.add(new AABB(p));
    }

    private static boolean prismapump(Level level, BlockPos hover, List<AABB> out) {
        boolean any = false;
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (!((x == 0 && y == 0) || (x == 0 && z == 0) || (z == 0 && y == 0))) continue;
                    if (x == 0 && y == 0 && z == 0) continue;
                    if (!level.getBlockState(hover.offset(-x, -y, -z)).is(Blocks.DARK_PRISMARINE)) continue;
                    for (int i = 0; i < 300; i++) {
                        BlockPos p = hover.offset(x * i, y * i, z * i);
                        if (Math.abs(p.getX()) > 128 || Math.abs(p.getY()) > 319 || Math.abs(p.getZ()) > 128) break;
                        BlockState st = level.getBlockState(p);
                        if (!st.isAir() && !st.is(Blocks.WATER)) break;
                        if (y == 0 && level.getBlockState(p.below()).isAir()) break;
                        out.add(new AABB(p));
                        any = true;
                    }
                }
            }
        }
        return any;
    }

    private static void crystals(Minecraft mc, TeslaMapsConfig c, String held) {
        long now = System.currentTimeMillis();
        if (held.endsWith("_CRYSTAL") && !held.equals("POWER_CRYSTAL")) crystalShown = now;
        if (!c.minionCrystalAlways && now - crystalShown > 10_000L) {
            if (!rings.isEmpty()) rings = List.of();
            return;
        }
        List<AABB> out = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (ArmorStand stand : EntityCache.armorStands()) {
            if (!stand.isSmall()) continue;
            ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
            if (!head.is(Items.PLAYER_HEAD)) continue;
            ResolvableProfile prof = head.get(DataComponents.PROFILE);
            if (prof == null) continue;
            Crystal cr = CRYSTALS.get(prof.partialProfile().id());
            if (cr == null) continue;
            BlockPos center = BlockPos.containing(stand.getX(), stand.getY() + 0.5, stand.getZ());
            int r = cr.radius();
            int col = 0x60000000 | cr.rgb();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    float d = (x - 0.5f) * (x - 0.5f) + (z - 0.5f) * (z - 0.5f);
                    if (d > (r - 1) * (r - 1) && d < r * r) {
                        out.add(new AABB(center.offset(x, 0, z)));
                        cols.add(col);
                    }
                }
            }
        }
        int[] arr = new int[cols.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = cols.get(i);
        ringColors = arr;
        rings = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AABB> list = boxes;
        if (!list.isEmpty()) {
            int color = boxColor;
            ESPRenderer.beginBoxBatch(pose, cam, true, false);
            for (AABB b : list) ESPRenderer.batchBox(b.inflate(0.002), color, 1f);
            ESPRenderer.endBoxBatch();
            ESPRenderer.beginBoxBatch(pose, cam, false, false);
            for (AABB b : list) ESPRenderer.batchBox(b.inflate(0.002), color | 0xFF000000, 1.5f);
            ESPRenderer.endBoxBatch();
        }
        List<AABB> rs = rings;
        int[] cols = ringColors;
        if (!rs.isEmpty() && cols.length == rs.size()) {
            ESPRenderer.beginBoxBatch(pose, cam, true, false);
            for (int i = 0; i < rs.size(); i++) ESPRenderer.batchBox(rs.get(i), cols[i], 1f);
            ESPRenderer.endBoxBatch();
        }
    }
}
