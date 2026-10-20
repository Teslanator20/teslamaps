package com.teslamaps.adm.misc;

import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from ADM's misc/I4Complete.kt: title when your 4th device is done while you stand on its plate
public final class I4Complete {
    private static final String TITLE = "§a§li4 Complete!";
    private static final BlockPos PLATE = new BlockPos(63, 127, 35);
    private static final AABB STAND_BOX = new AABB(62.5, 124.5, 33.5, 64.5, 127.5, 35.5);
    private static final Pattern DEVICE_LINE = Pattern.compile("^(.{1,16}) completed a device! \\(\\d/\\d\\)$");

    private static Boolean tagWasActive;
    private static boolean shown;

    private I4Complete() {}

    static void register() {
        AdmCore.onWorldLoad(I4Complete::reset);
        Feature.of("AdmI4Complete").section(Adm.SECTION).when(() -> TeslaMapsConfig.get().admRandomStuff)
                .tick(I4Complete::tick)
                .serverChat(I4Complete::onChat);
    }

    private static void reset() {
        tagWasActive = null;
        shown = false;
    }

    private static boolean onPlate() {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        AABB b = p.getBoundingBox();
        return b.maxX > PLATE.getX() && b.minX < PLATE.getX() + 1 && b.maxZ > PLATE.getZ() && b.minZ < PLATE.getZ() + 1
                && b.minY >= PLATE.getY() - 0.01 && b.minY < PLATE.getY() + 0.25;
    }

    private static void done() {
        if (shown || !onPlate()) return;
        shown = true;
        AdmMisc.alert(TITLE, true);
    }

    private static void onChat(String raw) {
        String msg = AdmMisc.strip(raw);
        if (msg.equals("[BOSS] Goldor: Who dares trespass into my domain?")) { reset(); return; }
        Matcher m = DEVICE_LINE.matcher(msg);
        var p = Minecraft.getInstance().player;
        if (m.matches() && p != null && m.group(1).equals(p.getGameProfile().name())) done();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || (!com.teslamaps.dungeon.DungeonManager.isInDungeon() && !mc.hasSingleplayerServer())) return;
        boolean active = false;
        for (ArmorStand s : level.getEntitiesOfClass(ArmorStand.class, STAND_BOX, ArmorStand::hasCustomName)) {
            if (s.getCustomName() != null && "Active".equals(com.teslamaps.utils.StringUtil.strip(s.getCustomName().getString()))) {
                active = true;
                break;
            }
        }
        Boolean was = tagWasActive;
        tagWasActive = active;
        if (active && Boolean.FALSE.equals(was)) done();
    }
}
