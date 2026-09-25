package com.teslamaps.safari;

// Ported from critterMod's client/HideonwallSpots.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The eight fixed Haunted perches a Hideonwall can sit on. A perch resolves when you see it
 * or a thrown capsule flies through it: occupied keeps its own colour, empty disappears.
 */
public final class HideonwallPerches {

    private static final BlockPos[] PERCHES = {
            new BlockPos(18, 71, -57), new BlockPos(-5, 71, -85), new BlockPos(-24, 71, -76),
            new BlockPos(-25, 71, -65), new BlockPos(-25, 79, -57), new BlockPos(6, 79, -84),
            new BlockPos(17, 80, -75), new BlockPos(17, 79, -52),
    };
    private static final double HIT_MARGIN = 0.5;
    private static final double ON_PERCH_SQ = 3.0 * 3.0;

    /** null = unresolved, TRUE = one sits there, FALSE = empty. */
    private static final Boolean[] resolved = new Boolean[PERCHES.length];
    private static final String[] labels = new String[PERCHES.length];
    private static boolean any;
    private static int sightTimer;

    private HideonwallPerches() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HideonwallPerches").section("Critter Safari")
                .when(() -> cfg().safariCritterESP && cfg().safariHideonwallPerches)
                .tick(HideonwallPerches::tick)
                .serverChat(HideonwallPerches::onChat)
                .world(HideonwallPerches::render);
    }

    private static void reset() {
        java.util.Arrays.fill(resolved, null);
        java.util.Arrays.fill(labels, null);
        any = false;
    }

    private static void tick() {
        if (!SafariData.inSafari()) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        any = SafariData.currentBiome() == SafariData.Biome.HAUNTED;
        if (!any) return;

        if (--sightTimer <= 0) {
            sightTimer = 4;
            for (int i = 0; i < PERCHES.length; i++) {
                if (inView(PERCHES[i]) && SafariSight.canSee(PERCHES[i])) resolved[i] = occupied(PERCHES[i]);
            }
        }

        for (Display.ItemDisplay d : EntityCache.byType(Display.ItemDisplay.class)) {
            if (!isCapsule(d)) continue;
            Vec3 to = d.position();
            Vec3 from = new Vec3(d.xOld, d.yOld, d.zOld);
            if (from.equals(to)) continue;
            for (int i = 0; i < PERCHES.length; i++) {
                if (new AABB(PERCHES[i]).inflate(HIT_MARGIN).clip(from, to).isPresent()) {
                    resolved[i] = occupied(PERCHES[i]);
                }
            }
        }

        Vec3 eye = mc.player.getEyePosition();
        for (int i = 0; i < PERCHES.length; i++) {
            Boolean r = resolved[i];
            if (Boolean.FALSE.equals(r)) { labels[i] = null; continue; }
            int dist = (int) Math.sqrt(Vec3.atCenterOf(PERCHES[i]).distanceToSqr(eye));
            labels[i] = (r == null ? "§5Hideonwall perch §7(" : "§aHideonwall §7(") + dist + "m)";
        }
    }

    private static void onChat(String raw) {
        if (!SafariData.inSafari()) return;
        String msg = StringUtil.strip(raw).trim();
        if (!msg.contains("Hideonwall")) return;
        if (SafariTracker.CAUGHT.matcher(msg).matches() || SafariTracker.FOUND.matcher(msg).matches()
                || SafariTracker.LOOT_SHARE.matcher(msg).matches()) {
            for (int i = 0; i < resolved.length; i++) if (resolved[i] != null) resolved[i] = Boolean.FALSE;
        }
    }

    private static boolean inView(BlockPos pos) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera == null) return true;
        Frustum f = camera.getCullFrustum();
        return f == null || f.isVisible(new AABB(pos));
    }

    private static boolean isCapsule(Display.ItemDisplay d) {
        ItemStack stack = itemOf(d);
        return !stack.isEmpty() && StringUtil.strip(stack.getHoverName().getString()).equals("Critter Capsule");
    }

    private static ItemStack itemOf(Display.ItemDisplay d) {
        SlotAccess slot = d.getSlot(0);
        return slot == null ? ItemStack.EMPTY : slot.get();
    }

    private static boolean occupied(BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            if (as.position().distanceToSqr(c) > ON_PERCH_SQ + 4) continue;
            if (as.getCustomName() != null && as.getCustomName().getString().contains("Hideonwall")) return true;
        }
        for (Display.ItemDisplay d : EntityCache.byType(Display.ItemDisplay.class)) {
            if (d.position().distanceToSqr(c) <= ON_PERCH_SQ && itemOf(d).is(Items.PURPLE_SHULKER_BOX)) return true;
        }
        return false;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!any) return;
        TeslaMapsConfig c = cfg();
        boolean through = !c.safariLegitMode;
        int open = TeslaMapsConfig.parseColor(c.colorHideonwallPerch) | 0xFF000000;
        int found = TeslaMapsConfig.parseColor(c.colorHideonwallFound) | 0xFF000000;
        for (int i = 0; i < PERCHES.length; i++) {
            String label = labels[i];
            if (label == null) continue;
            BlockPos p = PERCHES[i];
            int color = resolved[i] == null ? open : found;
            AABB box = new AABB(p);
            if (c.safariFilled) ESPRenderer.drawFilledBox(pose, box, color & 0x00FFFFFF, cam, through);
            ESPRenderer.drawBoxOutline(pose, box, color, 2.0f, cam, through);
            ESPRenderer.drawText(pose, label, new Vec3(p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5), 1.2f, cam, true, through);
        }
    }
}
