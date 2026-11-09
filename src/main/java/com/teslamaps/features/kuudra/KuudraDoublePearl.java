package com.teslamaps.features.kuudra;

// Ported from IQ's supply giant hitbox alert and Shizo's SupplyHighlighter double pearl warning
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.GuiAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** While a supply is being picked up (1-99 %), warns when the pearl would spawn inside a supply giant. */
public final class KuudraDoublePearl {

    private static final Pattern PROGRESS = Pattern.compile("^\\[[| ]+]\\s*(\\d+)%$");

    private static Component lastTitle, lastSub;
    private static int progress;
    private static long progressMs;
    private static Giant giant;
    private static int level, alertedId = -1;

    private KuudraDoublePearl() {}

    public static void register() {
        Feature.of("KuudraDoublePearl").section("Kuudra").when(() -> TeslaMapsConfig.get().kuudraDoublePearl)
                .tick(KuudraDoublePearl::tick)
                .world(KuudraDoublePearl::render);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        giant = null;
        level = 0;
        if (!KuudraRunInfo.inKuudra()) { alertedId = -1; return; }
        GuiAccessor gui = (GuiAccessor) mc.gui;
        if (gui.teslamaps$titleTime() > 0) {
            Component t = gui.teslamaps$title(), s = gui.teslamaps$subtitle();
            if (t != lastTitle || s != lastSub) {
                lastTitle = t;
                lastSub = s;
                int v = Math.max(parse(t), parse(s));
                if (v > 0) { progress = v; progressMs = System.currentTimeMillis(); }
            }
        }
        if (System.currentTimeMillis() - progressMs > 1000 || progress >= 100) { alertedId = -1; return; }

        var p = mc.player;
        Vec3 eye = p.getEyePosition();
        AABB pearl = new AABB(p.getX() - 0.3, eye.y, p.getZ() - 0.3, p.getX() + 0.3, eye.y, p.getZ() + 0.3);
        AABB body = p.getBoundingBox();
        double best = Double.MAX_VALUE;
        for (Giant g : Kuudra.giants()) {
            if (g.isRemoved()) continue;
            AABB box = g.getBoundingBox();
            int l = blocksPearl(box, pearl) ? 2 : box.intersects(body) ? 1 : 0;
            if (l == 0) continue;
            double d = g.distanceToSqr(p);
            if (l > level || (l == level && d < best)) { level = l; giant = g; best = d; }
        }
        if (level == 2 && alertedId != giant.getId()) {
            alertedId = giant.getId();
            mc.gui.hud.setTimes(0, 20, 8);
            mc.gui.hud.setTitle(Component.literal("§c§lDOUBLE PEARL"));
            if (TeslaMapsConfig.get().kuudraDoublePearlSound) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.15f);
        }
    }

    private static boolean blocksPearl(AABB box, AABB pearl) {
        return pearl.minX < box.maxX && pearl.maxX > box.minX && pearl.minZ < box.maxZ && pearl.maxZ > box.minZ
                && pearl.minY >= box.minY && pearl.minY <= box.maxY;
    }

    private static int parse(Component c) {
        if (c == null) return 0;
        Matcher m = PROGRESS.matcher(StringUtil.strip(c.getString()).trim());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Giant g = giant;
        if (g == null || level == 0) return;
        AABB box = ESPRenderer.lerpBox(g).inflate(0.08);
        int c = level == 2 ? 0xFFFF3333 : 0xFFFFAA33;
        ESPRenderer.drawFilledBox(pose, box, (c & 0xFFFFFF) | 0x40000000, cam, true);
        ESPRenderer.drawBoxOutline(pose, box, c, 3f, cam, true);
    }
}
