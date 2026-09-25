package com.teslamaps.features;

// Ported from SkyHanni's GardenStartLocation.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Start point and last farmed spot per crop, shown while holding that crop's tool. */
public final class CropStartLocation {

    public static final String[] MODES = {"Both", "Start", "Last Farmed"};

    private static boolean showLast;
    private static String shownCrop;
    private static Vec3 start, last;
    private static AABB startBox, lastBox;
    private static BlockPos lastPos;
    private static Vec3 startText, startSub, lastText, lastSub;
    private static String startLabel, lastLabel;

    private CropStartLocation() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CropStartLocation").section("Garden").when(() -> cfg().cropStartLocation)
                .tick(CropStartLocation::tick)
                .world(CropStartLocation::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Crop Locations");
                    b.toggle("Crop Start / Last Farmed Location", () -> c.cropStartLocation, v -> c.cropStartLocation = v)
                            .desc("Holding a crop's tool shows where you start farming it (white) and where you "
                                    + "stopped (purple beacon, once you are 10+ blocks away). The start is saved on the "
                                    + "first break, /tmap garden startloc moves it.")
                            .tags("waypoint", "farm", "return");
                    b.dropdown("  Show", MODES, () -> c.cropStartLocationMode, v -> c.cropStartLocationMode = v);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("garden")
                        .then(ClientCommands.literal("startloc").executes(c -> { setHere(); return 1; })))));
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + s));
    }

    private static void setHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!SkyblockUtils.isInGarden()) { msg("§cOnly works in the Garden"); return; }
        if (!cfg().cropStartLocation) { msg("§cTurn on Crop Start / Last Farmed Location first (Garden)"); return; }
        String crop = GardenMilestoneData.heldToolCrop();
        if (crop == null) { msg("§cHold a crop specific farming tool"); return; }
        GardenMilestoneData.get().start.put(crop, new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
        GardenMilestoneData.markDirty();
        msg("Crop start location for §e" + crop + "§f set");
    }

    /** From GardenMilestoneData, a crop broken in the garden. */
    public static void onCropBreak(String crop) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !FarmingSensitivity.isFarmingTool(mc.player.getMainHandItem())) return;
        String held = GardenMilestoneData.heldToolCrop();
        if (held != null && !held.equals(crop)) return;
        var data = GardenMilestoneData.get();
        if (!data.start.containsKey(crop)) {
            data.start.put(crop, new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            msg("Saved the crop start location for §e" + crop);
        }
        data.last.put(crop, new double[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
        GardenMilestoneData.markDirty();
        showLast = false;
    }

    private static void tick() {
        start = last = null;
        if (!SkyblockUtils.isInGarden()) return;
        Minecraft mc = Minecraft.getInstance();
        String crop = GardenMilestoneData.heldToolCrop();
        if (crop == null) return;
        if (!crop.equals(shownCrop)) {
            shownCrop = crop;
            startLabel = "§b" + crop;
            lastLabel = "§b" + crop;
        }
        String mode = cfg().cropStartLocationMode;
        var data = GardenMilestoneData.get();
        if (!"Last Farmed".equals(mode)) {
            double[] s = data.start.get(crop);
            if (s != null) {
                start = new Vec3(Math.floor(s[0]) + 0.5, Math.floor(s[1]), Math.floor(s[2]) + 0.5);
                startBox = new AABB(start.x - 0.5, start.y, start.z - 0.5, start.x + 0.5, start.y + 1, start.z + 0.5);
                startText = start.add(0, 2.0, 0);
                startSub = start.add(0, 1.6, 0);
            }
        }
        if (!"Start".equals(mode)) {
            double[] l = data.last.get(crop);
            if (l != null) {
                Vec3 v = new Vec3(l[0] + 0.5, l[1], l[2] + 0.5);
                if (mc.player.position().distanceToSqr(v) >= 100) showLast = true;
                if (showLast) {
                    last = v;
                    lastBox = new AABB(l[0], l[1], l[2], l[0] + 1, l[1] + 1, l[2] + 1);
                    lastPos = BlockPos.containing(l[0], l[1], l[2]);
                    lastText = v.add(0, 2.0, 0);
                    lastSub = v.add(0, 1.6, 0);
                }
            }
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        boolean both = "Both".equals(cfg().cropStartLocationMode);
        Vec3 s = start, l = last;
        if (s != null) {
            ESPRenderer.drawFilledBox(pose, startBox, 0x60FFFFFF, cam, false);
            ESPRenderer.drawText(pose, startLabel, startText, 1.5f, cam);
            if (both) ESPRenderer.drawText(pose, "§aStart Location", startSub, 1.1f, cam, true, true, 0xFFFFFFFF, 0);
        }
        if (l != null) {
            ESPRenderer.drawFilledBox(pose, lastBox, 0x80FF55FF, cam, true);
            ESPRenderer.drawBeaconBeam(pose, lastPos, 0xCCFF55FF, cam);
            ESPRenderer.drawText(pose, lastLabel, lastText, 1.5f, cam);
            if (both) ESPRenderer.drawText(pose, "§eLast Farmed", lastSub, 1.1f, cam, true, true, 0xFFFFFFFF, 0);
        }
    }
}
