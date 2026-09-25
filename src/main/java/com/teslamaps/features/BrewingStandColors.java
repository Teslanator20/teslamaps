package com.teslamaps.features;

// Ported from Skytils' BrewingFeatures.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BrewingStandColors {

    private static final Pattern TIME = Pattern.compile("^(\\d+(?:\\.\\d)?)s$");
    private static final int RED = 0x80FF0000, GREEN = 0x8000FF00;

    private static final Map<BlockPos, Long> stands = new HashMap<>();
    private static BlockPos lastClicked;
    private static long lastClickMs;
    private static Object lastLevel;
    private static boolean onIsland;
    private static int ticks;

    private BrewingStandColors() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BrewingStandColors").section("Inventory").when(() -> cfg().brewingStandColors)
                .tick(BrewingStandColors::tick)
                .world(BrewingStandColors::render);
    }

    public static void onClick(BlockPos pos) {
        if (!cfg().brewingStandColors) return;
        lastClicked = pos.immutable();
        lastClickMs = System.currentTimeMillis();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            stands.clear();
            lastClicked = null;
        }
        if (++ticks % 20 == 0) {
            onIsland = SkyblockUtils.isInArea("Private Island");
            if (onIsland) stands.keySet().removeIf(p -> !mc.level.getBlockState(p).is(Blocks.BREWING_STAND));
        }
        if (!onIsland || lastClicked == null || System.currentTimeMillis() - lastClickMs > 10_000) return;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)
                || !StringUtil.stripTrim(cs.getTitle().getString()).equals("Brewing Stand")) return;
        List<Slot> slots = cs.getMenu().slots;
        double secs = -1;
        if (slots.size() > 22) secs = seconds(slots.get(22).getItem());
        if (secs < 0) {
            for (Slot s : slots) {
                if (s.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                secs = seconds(s.getItem());
                if (secs >= 0) break;
            }
        }
        stands.put(lastClicked, System.currentTimeMillis() + (long) (Math.max(0, secs) * 1000));
    }

    private static double seconds(ItemStack st) {
        if (st.isEmpty()) return -1;
        Matcher m = TIME.matcher(StringUtil.stripTrim(st.getHoverName().getString()));
        if (!m.matches()) return -1;
        try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { return -1; }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!onIsland || stands.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<BlockPos, Long> e : stands.entrySet()) {
            ESPRenderer.drawFilledBox(pose, new AABB(e.getKey()), e.getValue() > now ? RED : GREEN, cam, false);
        }
    }
}
