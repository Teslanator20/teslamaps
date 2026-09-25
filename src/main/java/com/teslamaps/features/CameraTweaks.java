package com.teslamaps.features;

// No front view: legitcatmod CameraTypeMixin + DulkirMod selfie key. Sneak/riding/fov fixes: NoammAddons Camera.kt,
// old crouch / no swim: legitcatmod AvatarMixin
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;

import java.util.ArrayList;
import java.util.List;

public final class CameraTweaks {

    private static final int DATA_POSE = 6;
    private static boolean keyDown;

    private CameraTweaks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CameraTweaks").when(() -> cfg().noFrontViewKey != -1)
                .tickAlways(CameraTweaks::tick)
                .config("Hide", b -> {
                    b.toggle("No Front View (F5)", () -> cfg().noFrontView, v -> cfg().noFrontView = v)
                            .desc("F5 only switches between first person and third person from behind");
                    b.keybind("  Toggle Key", () -> cfg().noFrontViewKey, v -> cfg().noFrontViewKey = v);
                    b.toggle("Double Sneak Fix", () -> cfg().doubleSneakFix, v -> cfg().doubleSneakFix = v)
                            .desc("Ignores the server's pose updates for yourself, so sneaking does not flicker");
                    b.toggle("Riding Input Fix", () -> cfg().ridingInputFix, v -> cfg().ridingInputFix = v)
                            .desc("Fixes the mouse delay while riding (MC-206540)");
                    b.toggle("Custom FOV", () -> cfg().customFov, v -> cfg().customFov = v)
                            .desc("Replaces the vanilla FOV, 1 to 179");
                    b.slider("  FOV", 1f, 179f, () -> (float) cfg().customFovValue, v -> cfg().customFovValue = v.intValue());
                    b.toggle("Camera Distance (F5)", () -> cfg().customCameraDistance, v -> cfg().customCameraDistance = v)
                            .desc("Third person camera distance, vanilla is 4 blocks");
                    b.slider("  Distance", 3f, 12f, () -> cfg().cameraDistance, v -> cfg().cameraDistance = v, 1);
                    b.toggle("Camera Clip (F5)", () -> cfg().cameraClip, v -> cfg().cameraClip = v)
                            .desc("The third person camera goes through blocks instead of moving closer");
                    b.toggle("1.8 Sneak Height", () -> cfg().oldCrouch, v -> cfg().oldCrouch = v)
                            .desc("Sneaking keeps the 1.8 height. Not on Galatea / The Park");
                    b.toggle("No Swim / Crawl Camera", () -> cfg().noSwimPose, v -> cfg().noSwimPose = v)
                            .desc("Keeps the standing eye height while swimming or crawling");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.screen == null && KeyNames.isDown(cfg().noFrontViewKey);
        if (down && !keyDown) {
            cfg().noFrontView = !cfg().noFrontView;
            TeslaMapsConfig.save();
            if (mc.player != null)
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fNo Front View: "
                        + (cfg().noFrontView ? "§aON" : "§cOFF")));
            if (cfg().noFrontView && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT)
                mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        keyDown = down;
    }

    /** Replacement for CameraType.cycle, null = vanilla. */
    public static CameraType cycle(CameraType current) {
        if (!cfg().noFrontView) return null;
        return current == CameraType.FIRST_PERSON ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON;
    }

    public static List<SynchedEntityData.DataValue<?>> filterEntityData(List<SynchedEntityData.DataValue<?>> values,
                                                                       ClientboundSetEntityDataPacket packet) {
        if (!cfg().doubleSneakFix) return values;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || packet.id() != mc.player.getId()) return values;
        boolean found = false;
        for (SynchedEntityData.DataValue<?> v : values) if (v.id() == DATA_POSE) { found = true; break; }
        if (!found) return values;
        List<SynchedEntityData.DataValue<?>> out = new ArrayList<>(values.size());
        for (SynchedEntityData.DataValue<?> v : values) if (v.id() != DATA_POSE) out.add(v);
        return out;
    }

    // Debugify's MC-206540 fix
    public static void onPassengerTurned(Entity vehicle, Entity passenger) {
        if (!cfg().ridingInputFix || passenger != Minecraft.getInstance().player) return;
        passenger.setYBodyRot(vehicle.getYRot());
        float f = Mth.wrapDegrees(passenger.getYRot() - vehicle.getYRot());
        float g = Mth.clamp(f, -180.0F, 180.0F);
        passenger.yRotO += g - f;
        passenger.setYRot(passenger.getYRot() + g - f);
        passenger.setYHeadRot(passenger.getYRot());
    }

    public static float fov(float fov) {
        if (!cfg().customFov) return fov;
        int base = Minecraft.getInstance().options.fov().get();
        return base <= 0 ? fov : fov * (Mth.clamp(cfg().customFovValue, 1, 179) / (float) base);
    }

    public static float cameraDistance(float distance) {
        if (!cfg().customCameraDistance) return distance;
        return distance / 4f * Mth.clamp(cfg().cameraDistance, 3f, 12f);
    }

    public static boolean cameraClip() {
        return cfg().cameraClip;
    }

    /** Own player's dimensions for a pose, null = vanilla. */
    public static EntityDimensions poseDimensions(Pose pose, EntityDimensions original, EntityDimensions standing) {
        TeslaMapsConfig c = cfg();
        if (pose == Pose.CROUCHING ? !c.oldCrouch : pose != Pose.SWIMMING || !c.noSwimPose) return null;
        String area = SkyblockUtils.getArea();
        if (area.equalsIgnoreCase("Galatea") || area.equalsIgnoreCase("The Park")) return null;
        return pose == Pose.CROUCHING ? standing.withEyeHeight(1.54f) : original.withEyeHeight(standing.eyeHeight());
    }
}
