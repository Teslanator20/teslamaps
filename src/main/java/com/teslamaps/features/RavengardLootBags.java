package com.teslamaps.features;

// Ported from Skysoft's RavengardLootBagCheckmarks.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RavengardLootBags {

    private static final String NS = "hypixel_ravengard";
    private static final String PREFIX = "item/gameplay/lootbags/lootbag_";
    private static final String CHECK = "§a§l✔";
    private static final long OPEN_TIMEOUT = 10_000;

    private static final Set<UUID> opened = new HashSet<>();
    private static List<Vec3> marks = List.of();
    private static UUID pending;
    private static long pendingAt;
    private static Object level;
    private static int ticks;
    private static Feature feature;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("RavengardLootBags").section("Render")
                .when(() -> cfg().ravengardLootBagChecks)
                .tick(RavengardLootBags::tick)
                .world(RavengardLootBags::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Ravengard");
                    b.toggle("Loot Bag Checkmarks", () -> c.ravengardLootBagChecks, v -> c.ravengardLootBagChecks = v)
                            .desc("Green ✔ over Ravengard loot bags you opened yourself.")
                            .tags("ravengard", "lootbag", "dead body");
                });
        UseEntityCallback.EVENT.register((player, lvl, hand, entity, hit) -> {
            if (lvl.isClientSide() && entity instanceof Interaction in && feature.active()) onInteract(in);
            return InteractionResult.PASS;
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (pending == null || !(screen instanceof AbstractContainerScreen<?> cs)) return;
            UUID p = pending;
            pending = null;
            if (System.currentTimeMillis() - pendingAt > OPEN_TIMEOUT) return;
            if (StringUtil.stripTrim(cs.getTitle().getString()).equals("Dead Body")) opened.add(p);
        });
    }

    private static void onInteract(Interaction in) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        syncLevel(mc);
        Display.ItemDisplay best = null;
        double bestD = 1.0;
        for (Display.ItemDisplay d : mc.level.getEntitiesOfClass(Display.ItemDisplay.class, in.getBoundingBox().inflate(2))) {
            if (!isBag(model(d), false)) continue;
            double dist = d.distanceToSqr(in);
            if (dist <= bestD) { bestD = dist; best = d; }
        }
        if (best == null) return;
        pending = best.getUUID();
        pendingAt = System.currentTimeMillis();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        syncLevel(mc);
        if (++ticks % 5 != 0) return;
        if (opened.isEmpty()) {
            if (!marks.isEmpty()) marks = List.of();
            return;
        }
        List<Vec3> out = new ArrayList<>();
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof Display.ItemDisplay d && opened.contains(d.getUUID()) && isBag(model(d), true)) {
                out.add(d.position().add(0, 0.9, 0));
            }
        }
        marks = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        for (int i = 0, n = marks.size(); i < n; i++) {
            ESPRenderer.drawText(pose, CHECK, marks.get(i), 1.2f, cam, false, false);
        }
    }

    private static void syncLevel(Minecraft mc) {
        if (mc.level == level) return;
        level = mc.level;
        opened.clear();
        pending = null;
        marks = List.of();
    }

    private static Identifier model(Display.ItemDisplay d) {
        Display.ItemDisplay.ItemRenderState state = d.itemRenderState();
        if (state == null) return null;
        ItemStack stack = state.itemStack();
        return stack.isEmpty() ? null : stack.get(DataComponents.ITEM_MODEL);
    }

    private static boolean isBag(Identifier model, boolean openOnly) {
        if (model == null || !NS.equals(model.getNamespace()) || !model.getPath().startsWith(PREFIX)) return false;
        return !openOnly || model.getPath().endsWith("_open");
    }
}
