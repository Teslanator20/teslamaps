package com.teslamaps.features.events;

// After SkyHanni's FrozenTreasureHighlighter.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FishingHelpers;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Frozen Treasures in the Glacial Cave: the ice block holding one gets a box. */
public final class FrozenTreasureHighlighter {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private static List<AABB> boxes = List.of();
    private static int ticks;

    private FrozenTreasureHighlighter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Events", "Skyblock", true);
        Feature.of("FrozenTreasureHighlighter").section("Events").when(() -> cfg().frozenTreasureHighlight)
                .tick(FrozenTreasureHighlighter::tick)
                .world(FrozenTreasureHighlighter::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Jerry's Workshop");
                    b.toggle("Frozen Treasure Highlighter", () -> c.frozenTreasureHighlight, v -> c.frozenTreasureHighlight = v)
                            .desc("Glacial Cave: boxes the ice blocks that hold a Frozen Treasure.")
                            .tags("jerry", "winter", "glacial", "ice", "treasure");
                    b.espStyle("Treasure", () -> c.frozenTreasureStyle);
                });
    }

    private static void tick() {
        if (ticks++ % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !SkyblockUtils.isInArea("Jerry's Workshop")
                || !FishingHelpers.subArea().equals("Glacial Cave")) {
            boxes = List.of();
            return;
        }
        List<AABB> out = new ArrayList<>();
        for (ArmorStand as : EntityCache.armorStands()) {
            if (as.distanceToSqr(mc.player) > 2500) continue;
            int n = 0;
            for (EquipmentSlot s : SLOTS) if (!as.getItemBySlot(s).isEmpty()) n++;
            if (n != 1) continue;
            ItemStack helmet = as.getItemBySlot(EquipmentSlot.HEAD);
            if (helmet.isEmpty()) continue;
            if (helmet.is(Items.PLAYER_HEAD) && helmet.getHoverName().getString().endsWith("Head")) continue;
            BlockPos pos = as.blockPosition().above(2);
            BlockState st = mc.level.getBlockState(pos);
            if (!st.is(Blocks.ICE) && !st.is(Blocks.PACKED_ICE)) continue;
            out.add(new AABB(pos));
        }
        boxes = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AABB> l = boxes;
        if (l.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().frozenTreasureStyle);
        for (int i = 0; i < l.size(); i++) style.drawBox(pose, l.get(i), cam);
    }
}
