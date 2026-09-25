package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.LoudSound;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Etherwarp {

    public record EtherPos(boolean succeeded, BlockPos pos, String reason) {
        static final EtherPos NONE = new EtherPos(false, null, "Too far!");
    }

    private static BlockPos lastDebugPos = null; // throttles [EtherDbg] logging to pos changes

    private static ItemStack cachedStack;
    private static CompoundTag cachedData;
    private static long previewShownAt;
    public static String failReason = "";
    private static HudEditElement failEl;

    public static void register() {
        failEl = HudEditElement.of("etherwarpFailReason", "Etherwarp Fail Reason").section("Render")
                .enabled(() -> TeslaMapsConfig.get().etherwarp && TeslaMapsConfig.get().etherwarpFailReason)
                .size(() -> Minecraft.getInstance().font.width("Can't TP: No air above!"), () -> 9)
                .preview((g, mc, x, y, s) -> drawReason(g, mc, x, y, s, "Can't TP: No air above!"));
        Feature.of("EtherwarpFailReason").section("Render")
                .when(() -> TeslaMapsConfig.get().etherwarp && TeslaMapsConfig.get().etherwarpFailReason)
                .hud("etherwarpfailreason", (g, d) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (failReason.isEmpty() || failEl == null || mc.options.hideGui || !previewShown()) return;
                    drawReason(g, mc, failEl.x(), failEl.y(), failEl.scale(), failReason);
                });
    }

    private static String failText(String reason) {
        return switch (reason) {
            case "Too far!" -> "Can't TP: Too far!";
            case "No air above!" -> "Can't TP: No air above!";
            case "No room to stand!" -> "Can't TP: No room to stand!";
            case "Skull in the way!" -> "Can't TP: Skull in the way!";
            default -> "Can't TP: " + reason;
        };
    }

    private static void drawReason(net.minecraft.client.gui.GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        g.text(mc.font, text, 0, 0, 0xFFFF5555, true);
        g.pose().popMatrix();
    }

    /** The guess box was computed in the last frame or two. */
    public static boolean previewShown() {
        return System.currentTimeMillis() - previewShownAt < 100;
    }

    private static CompoundTag etherwarpData(ItemStack stack) {
        if (stack == cachedStack) return cachedData;
        cachedStack = stack;
        cachedData = readEtherwarpData(stack);
        return cachedData;
    }

    private static CompoundTag readEtherwarpData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        boolean isEther = tag.getInt("ethermerge").orElse(0) == 1
                || "ETHERWARP_CONDUIT".equals(tag.getString("id").orElse(""));
        return isEther ? tag : null;
    }

    private static volatile long zeroPingAt;
    private static final java.util.Set<Block> TILLABLE = java.util.Set.of(net.minecraft.world.level.block.Blocks.GRASS_BLOCK,
            net.minecraft.world.level.block.Blocks.DIRT, net.minecraft.world.level.block.Blocks.COARSE_DIRT,
            net.minecraft.world.level.block.Blocks.PODZOL, net.minecraft.world.level.block.Blocks.MYCELIUM,
            net.minecraft.world.level.block.Blocks.MOSS_BLOCK, net.minecraft.world.level.block.Blocks.ROOTED_DIRT);
    private static final java.util.Set<String> NO_TP_ROOMS = java.util.Set.of("New Trap", "Old Trap", "Teleport Maze", "Boulder");

    /** From PacketSendMixin, main thread. hitBlock = null for a plain use packet. */
    public static void onUseSent(BlockPos hitBlock, float yRot, float xRot) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.etherwarpCustomSound || !c.etherwarpZeroPing) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isShiftKeyDown() || player.isPassenger()) return;
        if (hitBlock != null && !TILLABLE.contains(mc.level.getBlockState(hitBlock).getBlock())) return;
        CompoundTag data = etherwarpData(player.getMainHandItem());
        if (data == null) return;
        long now = System.currentTimeMillis();
        if (now - zeroPingAt < 150) return;
        if (com.teslamaps.dungeon.DungeonManager.isInDungeon()) {
            var floor = com.teslamaps.dungeon.DungeonManager.getCurrentFloor();
            if (floor != null && floor.getLevel() == 7 && com.teslamaps.dungeon.DungeonManager.isInBossArea()) return;
            var room = com.teslamaps.dungeon.DungeonManager.getCurrentRoom();
            if (room != null && NO_TP_ROOMS.contains(room.getName())) return;
        }
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult) return;
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh
                && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockState s = mc.level.getBlockState(bh.getBlockPos());
            if (s.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                    || s.getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock
                    || s.getBlock() instanceof net.minecraft.world.level.block.HopperBlock
                    || s.getBlock() instanceof net.minecraft.world.level.block.AbstractCauldronBlock
                    || s.getBlock() instanceof LeverBlock || s.getBlock() instanceof ButtonBlock
                    || s.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock) return;
        }
        double distance = 57.0 + data.getInt("tuned_transmission").orElse(0);
        EtherPos ether = getEtherPos(player.position(), Vec3.directionFromRotation(xRot, yRot), distance);
        if (!ether.succeeded() || ether.pos() == null) return;
        zeroPingAt = now;
        playCustomSound();
    }

    /** Server etherwarp sound arrived; true = our zero-ping sound already covered it. */
    public static boolean coveredByZeroPing() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.etherwarpZeroPing && System.currentTimeMillis() - zeroPingAt < 2000;
    }

    public static void playCustomSound() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        LoudSound.play(com.teslamaps.utils.SoundOptions.resolve(c.etherwarpSound), c.etherwarpSoundVolume, c.etherwarpSoundPitch);
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.etherwarp) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;

        CompoundTag data = etherwarpData(player.getMainHandItem());
        if (data == null) return;


        boolean conduit = "ETHERWARP_CONDUIT".equals(data.getString("id").orElse(""));
        if (!conduit && !player.isShiftKeyDown()) return;

        double distance = 57.0 + data.getInt("tuned_transmission").orElse(0);
        EtherPos ether = config.etherwarpServerPos
                ? getEtherPos(new Vec3(player.xLast, player.yLast, player.zLast),
                        Vec3.directionFromRotation(player.xRotLast, player.yRotLast), distance)
                : getEtherPos(player.position(), player.getLookAngle(), distance);
        previewShownAt = System.currentTimeMillis();
        failReason = ether.succeeded() ? "" : failText(ether.reason());
        if (!ether.succeeded() && !config.etherwarpShowFail) return;
        if (ether.pos() == null) return;

        if (config.debugMode && !ether.pos().equals(lastDebugPos)) {
            lastDebugPos = ether.pos();
            BlockState dbg = mc.level.getBlockState(ether.pos());
            com.teslamaps.TeslaMaps.debug("[EtherDbg] hit={} pos={} success={} eyeY={} blockTop={}",
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(dbg.getBlock()),
                    ether.pos(), ether.succeeded(),
                    String.format("%.2f", player.position().y + (player.isShiftKeyDown() ? 1.27 : 1.62)),
                    dbg.getShape(mc.level, ether.pos()).isEmpty() ? "empty"
                            : String.format("%.2f", dbg.getShape(mc.level, ether.pos()).max(net.minecraft.core.Direction.Axis.Y)));
        }

        int color = ether.succeeded()
                ? TeslaMapsConfig.parseColor(config.colorEtherwarp)
                : TeslaMapsConfig.parseColor(config.colorEtherwarpFail);
        BlockState target = mc.level.getBlockState(ether.pos());
        // leaves are cutout blocks: a depth-tested box on/at them z-fights and flickers -> draw through walls
        boolean through = config.etherwarpThroughWalls || target.is(net.minecraft.tags.BlockTags.LEAVES);
        AABB box = new AABB(ether.pos());
        if (config.etherwarpBlockShape) {
            var shape = target.getShape(mc.level, ether.pos());
            if (!shape.isEmpty()) box = shape.bounds().move(ether.pos());
        }
        box = box.inflate(0.002);
        if (config.etherwarpFilled) {
            ESPRenderer.drawFilledBox(matrices, box, color, cameraPos, through);
            if (config.etherwarpOutline)
                ESPRenderer.drawBoxOutline(matrices, box, color | 0xFF000000, config.etherwarpLineWidth, cameraPos, through);
        } else {
            ESPRenderer.drawBoxOutline(matrices, box, color, config.etherwarpLineWidth, cameraPos, through);
        }
    }

    private static EtherPos getEtherPos(Vec3 position, Vec3 look, double distance) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || position == null) return EtherPos.NONE;

        double eye = player.getPose() == net.minecraft.world.entity.Pose.SWIMMING ? 0.4 : player.isShiftKeyDown() ? 1.27 : 1.62;
        double eyeHeight = eye + TeslaMapsConfig.get().etherwarpEyeOffset;
        Vec3 start = position.add(0, eyeHeight, 0);
        Vec3 end = look.multiply(distance, distance, distance).add(start);
        return traverseVoxels(start, end);
    }

    private static final int PASSABLE = 1, BLOCKS_FEET = 2, SKULL = 4;
    private static int[] flags;

    private static int flags(BlockState s) {
        if (flags == null) {
            int[] f = new int[Block.BLOCK_STATE_REGISTRY.size()];
            for (BlockState st : Block.BLOCK_STATE_REGISTRY) {
                Block b = st.getBlock();
                int v = 0;
                if (st.isAir() || passableBlock(b)) v |= PASSABLE;
                if (b instanceof FlowerPotBlock || b instanceof LadderBlock || b instanceof VineBlock) v |= BLOCKS_FEET;
                if (b instanceof SkullBlock || b instanceof WallSkullBlock) v |= SKULL;
                int id = Block.getId(st);
                if (id >= 0 && id < f.length) f[id] = v;
            }
            flags = f;
        }
        int id = Block.getId(s);
        return id >= 0 && id < flags.length ? flags[id] : 0;
    }

    private static boolean passableBlock(Block b) {
        return b instanceof net.minecraft.world.level.block.LanternBlock
                || b instanceof net.minecraft.world.level.block.DoublePlantBlock
                || b instanceof net.minecraft.world.level.block.GrowingPlantBlock
                || b instanceof net.minecraft.world.level.block.BigDripleafStemBlock
                || b instanceof net.minecraft.world.level.block.piston.PistonHeadBlock
                || b instanceof ButtonBlock
                || b instanceof SkullBlock || b instanceof WallSkullBlock || b instanceof LadderBlock
                || b instanceof SaplingBlock || b instanceof FlowerBlock || b instanceof StemBlock
                || b instanceof CropBlock || b instanceof BaseRailBlock || b instanceof SnowLayerBlock
                || b instanceof BubbleColumnBlock || b instanceof TripWireBlock || b instanceof TripWireHookBlock
                || b instanceof FireBlock || b instanceof TorchBlock || b instanceof FlowerPotBlock
                || b instanceof TallFlowerBlock || b instanceof TallGrassBlock || b instanceof BushBlock
                || b instanceof SeagrassBlock || b instanceof TallSeagrassBlock || b instanceof SugarCaneBlock
                || b instanceof LiquidBlock || b instanceof VineBlock || b instanceof MushroomBlock
                || b instanceof WebBlock || b instanceof DryVegetationBlock || b instanceof SmallDripleafBlock
                || b instanceof LeverBlock || b instanceof NetherWartBlock || b instanceof NetherPortalBlock
                || b instanceof RedStoneWireBlock || b instanceof ComparatorBlock || b instanceof RedstoneTorchBlock
                || b instanceof RepeaterBlock;
    }

    private static EtherPos traverseVoxels(Vec3 start, Vec3 end) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return EtherPos.NONE;

        double x0 = start.x, y0 = start.y, z0 = start.z;
        double x1 = end.x, y1 = end.y, z1 = end.z;

        int x = (int) Math.floor(x0), y = (int) Math.floor(y0), z = (int) Math.floor(z0);
        int endX = (int) Math.floor(x1), endY = (int) Math.floor(y1), endZ = (int) Math.floor(z1);

        double dirX = x1 - x0, dirY = y1 - y0, dirZ = z1 - z0;
        int stepX = (int) Math.signum(dirX), stepY = (int) Math.signum(dirY), stepZ = (int) Math.signum(dirZ);

        double invX = dirX != 0 ? 1.0 / dirX : Double.MAX_VALUE;
        double invY = dirY != 0 ? 1.0 / dirY : Double.MAX_VALUE;
        double invZ = dirZ != 0 ? 1.0 / dirZ : Double.MAX_VALUE;

        double tDeltaX = Math.abs(invX * stepX), tDeltaY = Math.abs(invY * stepY), tDeltaZ = Math.abs(invZ * stepZ);
        double tMaxX = Math.abs((x + Math.max(stepX, 0) - x0) * invX);
        double tMaxY = Math.abs((y + Math.max(stepY, 0) - y0) * invY);
        double tMaxZ = Math.abs((z + Math.max(stepZ, 0) - z0) * invZ);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 1000; i++) {
            cursor.set(x, y, z);
            BlockState hit = level.getBlockState(cursor);
            if ((flags(hit) & PASSABLE) == 0) {
                BlockPos pos = new BlockPos(x, y, z);
                // fences, walls: the feet go on top of the collision shape
                var col = hit.getCollisionShape(level, pos);
                double top = col.isEmpty() ? 1 : col.max(net.minecraft.core.Direction.Axis.Y);
                int base = y + Math.max(1, (int) Math.ceil(top));
                int feet = flags(level.getBlockState(new BlockPos(x, base, z)));
                int head = flags(level.getBlockState(new BlockPos(x, base + 1, z)));
                if ((feet & PASSABLE) == 0 || (head & PASSABLE) == 0) return new EtherPos(false, pos, "No air above!");
                if (((feet | head) & BLOCKS_FEET) != 0) return new EtherPos(false, pos, "No room to stand!");
                if (TeslaMapsConfig.get().etherwarpSkullFail && ((feet | head) & SKULL) != 0)
                    return new EtherPos(false, pos, "Skull in the way!");
                return new EtherPos(true, pos, "");
            }
            if (x == endX && y == endY && z == endZ) return EtherPos.NONE;

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) { tMaxX += tDeltaX; x += stepX; }
            else if (tMaxY <= tMaxZ) { tMaxY += tDeltaY; y += stepY; }
            else { tMaxZ += tDeltaZ; z += stepZ; }
        }
        return EtherPos.NONE;
    }
}
