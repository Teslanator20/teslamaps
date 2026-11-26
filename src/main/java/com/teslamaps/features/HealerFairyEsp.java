package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.EntityCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Box around the Healer's fairy skull in dungeons. */
public final class HealerFairyEsp {

    private HealerFairyEsp() {}

    private static final Map<ItemStack, Boolean> HEADS = new WeakHashMap<>();
    private static List<ArmorStand> fairies = List.of();
    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HealerFairyEsp").section("ESP").legit().when(() -> cfg().healerFairyEsp)
                .tick(HealerFairyEsp::tick)
                .world(HealerFairyEsp::render)
                .config("ESP", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Healer Fairy ESP", () -> c.healerFairyEsp, v -> c.healerFairyEsp = v)
                            .desc("Dungeons: box around the Healer's fairy, also through walls")
                            .tags("healer", "fairy", "dungeon");
                    b.espStyle("Healer Fairy", () -> c.healerFairyStyle);
                });
    }

    private static void tick() {
        if (ticks++ % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !DungeonManager.isInDungeon()) {
            fairies = List.of();
            return;
        }
        EspStyle style = EspStyle.of(cfg().healerFairyStyle);
        List<ArmorStand> out = new ArrayList<>();
        for (ArmorStand as : EntityCache.armorStands()) {
            ItemStack head = as.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty()) continue;
            if (!HEADS.computeIfAbsent(head, SoulweaverHider::isHealerFairy)) continue;
            out.add(as);
            style.tickEntity(as);
        }
        fairies = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<ArmorStand> l = fairies;
        if (l.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().healerFairyStyle);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (ArmorStand as : l) {
            if (as.isRemoved()) continue;
            Vec3 p = as.getPosition(pt);
            double y = p.y + (as.isSmall() ? 0.85 : 1.7);
            style.drawBox(pose, new AABB(p.x - 0.3, y - 0.3, p.z - 0.3, p.x + 0.3, y + 0.3, p.z + 0.3), cam);
        }
    }
}
