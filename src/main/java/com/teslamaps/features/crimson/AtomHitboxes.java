package com.teslamaps.features.crimson;

// Ported from SkyHanni's nether/AtomHitBox.kt, textures from SkyHanni-REPO Skulls.json
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class AtomHitboxes {

    private enum Atom {
        EXE("§aExe", "9e46a39a8e61a4da07b17adef0ee2209f64da8aeb9a9bd6110a4e24e35ed74f3", 0xFF55FF55),
        WAI("§6Wai", "f83fddf7aa6ff3c50abac7e6a7796e155cca4ccfce45734f66e411cbca989a04", 0xFFFFAA00),
        ZEE("§5Zee", "4e8f19def33085db564909fc8c1b2eabc0ebc59cd89665f31e580f1fc2497b58", 0xFFFF55FF);

        final String label, hash;
        final int color;

        Atom(String label, String hash, int color) {
            this.label = label;
            this.hash = hash;
            this.color = color;
        }

        boolean selected(TeslaMapsConfig c) {
            return switch (this) {
                case EXE -> c.atomExe;
                case WAI -> c.atomWai;
                case ZEE -> c.atomZee;
            };
        }

        static Atom of(String hash) {
            if (hash == null) return null;
            for (Atom a : values()) if (a.hash.equals(hash)) return a;
            return null;
        }
    }

    private record Found(ArmorStand stand, Atom atom) {}

    private static volatile List<Found> found = List.of();
    private static int ticks;

    private AtomHitboxes() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AtomHitboxes").section("Crimson Isle").when(() -> cfg().atomHitboxes)
                .tick(AtomHitboxes::tick)
                .world(AtomHitboxes::render)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Atom Hitboxes", () -> c.atomHitboxes, v -> c.atomHitboxes = v)
                            .desc("Coloured hitbox and name on Crimson Atoms within 50 blocks: Exe green, Wai orange, Zee magenta")
                            .tags("atom", "exe", "wai", "zee", "bestiary", "crimson");
                    b.toggle("  Exe", () -> c.atomExe, v -> c.atomExe = v);
                    b.toggle("  Wai", () -> c.atomWai, v -> c.atomWai = v);
                    b.toggle("  Zee", () -> c.atomZee, v -> c.atomZee = v);
                });
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInArea("Crimson Isle")) {
            found = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        List<Found> out = new ArrayList<>();
        for (ArmorStand a : EntityCache.armorStands()) {
            if (a.isRemoved() || a.distanceToSqr(mc.player) > 2500) continue;
            Atom atom = Atom.of(SoulweaverHider.headTextureUrl(a.getItemBySlot(EquipmentSlot.HEAD)));
            if (atom != null && atom.selected(c)) out.add(new Found(a, atom));
        }
        found = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Found> list = found;
        if (list.isEmpty()) return;
        for (Found f : list) {
            ArmorStand a = f.stand();
            if (a.isRemoved()) continue;
            AABB box = ESPRenderer.lerpBox(a);
            ESPRenderer.drawBoxOutline(pose, box, f.atom().color, 2f, cam, false);
            ESPRenderer.drawText(pose, f.atom().label, new Vec3((box.minX + box.maxX) / 2, box.minY - 0.5, (box.minZ + box.maxZ) / 2), 1f, cam);
        }
    }
}
