package com.teslamaps.features.crimson;

// After SkyHanni's nether/ashfang/AshfangFreezeCooldown, AshfangNextResetCooldown, AshfangHighlights
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AshfangHelper {

    private static final String SOUL_TEXTURE = "8287b397daf9516a0bd76f5f1b7bf979515df3d5d833e0635fa68b37ee082212";
    private static final String ORB_TEXTURE = "6de3564d5f2056a7e17e96419218b4213848a2f4d2b5fe035b7460c986e6f48c";
    private static final String FREEZE_PREFIX = "Ashfang Follower's Cryogenic Blast hit you for ";
    private static final AABB AREA = new AABB(-527, 120, -1055, -442, 170, -935);
    private static final long FREEZE_MS = 3000, RESET_MS = 46_100;
    private static final double MAX_LABEL = 15.0;
    private static final List<String> SAMPLE = List.of("§cAshfang Freeze: §a2.1s", "§cAshfang next reset in: §b31.4s");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width;
    private static long unfrozenAt, lastSpawn;
    private static boolean near;
    private static int ticks;
    private static Object lastLevel;
    private static final Set<UUID> seenMobs = new HashSet<>();
    private static List<ArmorStand> souls = List.of(), orbs = List.of();

    private AshfangHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("ashfangTimers", "Ashfang Timers").section("Crimson Isle")
                .enabled(() -> cfg().ashfangHelper)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? 2 : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("AshfangHelper").section("Crimson Isle").when(() -> cfg().ashfangHelper)
                .tick(AshfangHelper::tick)
                .serverChat(AshfangHelper::onChat)
                .hudHiddenInInventory("ashfangtimers", AshfangHelper::render)
                .world(AshfangHelper::renderWorld)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Ashfang");
                    b.toggle("Ashfang Helper", () -> c.ashfangHelper, v -> c.ashfangHelper = v)
                            .desc("Only near Ashfang. Timers move in /tmap gui")
                            .tags("ashfang", "blazing soul", "gravity orb", "freeze", "crimson");
                    b.toggle("  Freeze Timer", () -> c.ashfangFreezeTimer, v -> c.ashfangFreezeTimer = v)
                            .desc("3 s after a Cryogenic Blast hit, abilities are blocked");
                    b.toggle("  Next Reset Timer", () -> c.ashfangResetTimer, v -> c.ashfangResetTimer = v)
                            .desc("46.1 s from the first sighting of a minion wave");
                    b.toggle("  Blazing Souls", () -> c.ashfangBlazingSouls, v -> c.ashfangBlazingSouls = v);
                    b.color("    Soul Color", () -> c.colorAshfangSoul, v -> c.colorAshfangSoul = v);
                    b.toggle("  Gravity Orbs", () -> c.ashfangGravityOrbs, v -> c.ashfangGravityOrbs = v)
                            .desc("3.5 block ring around each orb, label goes green to red with distance");
                    b.color("    Orb Color", () -> c.colorAshfangOrb, v -> c.colorAshfangOrb = v);
                });
    }

    private static void onChat(String raw) {
        if (near && StringUtil.stripTrim(raw).startsWith(FREEZE_PREFIX)) unfrozenAt = System.currentTimeMillis() + FREEZE_MS;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seenMobs.clear();
            lastSpawn = 0;
            unfrozenAt = 0;
        }
        if (++ticks % 5 == 0) {
            near = Crimson.here() && AREA.contains(mc.player.position());
            scan();
        }
        if (!near) {
            lines = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>(2);
        if (cfg().ashfangFreezeTimer && unfrozenAt > now) out.add("§cAshfang Freeze: §a" + Crimson.tenths(unfrozenAt - now));
        if (cfg().ashfangResetTimer && lastSpawn > 0) {
            long left = lastSpawn + RESET_MS - now;
            out.add("§cAshfang next reset in: " + (left <= 0 ? "§aNow!" : "§b" + Crimson.tenths(left)));
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static void scan() {
        if (!near) {
            souls = List.of();
            orbs = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        List<ArmorStand> s = new ArrayList<>(), o = new ArrayList<>();
        for (ArmorStand as : EntityCache.armorStands()) {
            if (as.isRemoved()) continue;
            if (as.hasCustomName()) {
                String n = StringUtil.strip(as.getCustomName().getString());
                if (n.contains("Ashfang ") && seenMobs.add(as.getUUID()) && now - lastSpawn > 10_000) lastSpawn = now;
                continue;
            }
            String tex = SoulweaverHider.headTextureUrl(as.getItemBySlot(EquipmentSlot.HEAD));
            if (tex == null) continue;
            if (SOUL_TEXTURE.equals(tex)) s.add(as);
            else if (ORB_TEXTURE.equals(tex)) o.add(as);
        }
        souls = s;
        orbs = o;
        if (seenMobs.size() > 500) seenMobs.clear();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        if (!near) return;
        Minecraft mc = Minecraft.getInstance();
        if (cfg().ashfangBlazingSouls && !souls.isEmpty()) {
            int color = TeslaMapsConfig.parseColor(cfg().colorAshfangSoul);
            for (ArmorStand as : souls) {
                if (as.isRemoved()) continue;
                Vec3 p = as.position();
                ESPRenderer.drawFilledBox(pose, new AABB(p.x - 0.35, p.y + 1.4, p.z - 0.35, p.x + 0.35, p.y + 2.1, p.z + 0.35), color, cam, true);
                label(pose, cam, mc, p, "Blazing Soul");
            }
        }
        if (cfg().ashfangGravityOrbs && !orbs.isEmpty()) {
            int color = TeslaMapsConfig.parseColor(cfg().colorAshfangOrb);
            for (ArmorStand as : orbs) {
                if (as.isRemoved()) continue;
                Vec3 p = as.position();
                ESPRenderer.drawCircle(pose, p.x, p.y - 2, p.z, 3.5, color, 3f, cam, true);
                ESPRenderer.drawCircle(pose, p.x, p.y + 2.5, p.z, 3.5, color, 3f, cam, true);
                label(pose, cam, mc, p, "Gravity Orb");
            }
        }
    }

    private static void label(PoseStack pose, Vec3 cam, Minecraft mc, Vec3 p, String text) {
        double dist = mc.player.position().distanceTo(p);
        if (dist >= MAX_LABEL) return;
        float f = (float) (dist / MAX_LABEL);
        int r = (int) (0x55 + (0xFF - 0x55) * f), gr = (int) (0xFF + (0x55 - 0xFF) * f);
        ESPRenderer.drawText(pose, text, p.add(0, 2.5, 0), 1f, cam, true, true, 0xFF000000 | r << 16 | gr << 8 | 0x55);
    }
}
