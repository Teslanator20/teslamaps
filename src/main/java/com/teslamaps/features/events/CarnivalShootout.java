package com.teslamaps.features.events;

// Ported from SkyHanni's CarnivalZombieShootout.kt and Skyblocker's ZombieShootout.java / CatchAFish.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CarnivalShootout {

    private static final String YELLOW_FISH = "360f4f99c78adddeab276ebdf6ab9a0fcffabd49f0b543e1981ac7bd3d55b18a";
    private static final AABB FISH_AREA = new AABB(-87, 65, 25, -68, 85, 53);
    private static final List<String> SAMPLE = List.of("§6Lamp disappears in §a4.2s");

    private enum Type {
        LEATHER(30, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, 0xA52A2A, 8000),
        IRON(50, Items.IRON_HELMET, Items.IRON_CHESTPLATE, 0xC0C0C0, 7000),
        GOLD(80, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, 0xFFD700, 6000),
        DIAMOND(120, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, 0x2CD6FA, 5000);

        final int points;
        final Item helmet, chest;
        final int color;
        final long lifetime;

        Type(int points, Item helmet, Item chest, int color, long lifetime) {
            this.points = points;
            this.helmet = helmet;
            this.chest = chest;
            this.color = color;
            this.lifetime = lifetime;
        }
    }

    private static final class Target {
        final Zombie zombie;
        final Type type;
        String label;

        Target(Zombie zombie, Type type) {
            this.zombie = zombie;
            this.type = type;
        }

        Zombie zombie() { return zombie; }
        Type type() { return type; }
    }

    private static boolean started;
    private static BlockPos lamp;
    private static long lampAt;
    private static List<Target> targets = List.of();
    private static final Map<Integer, Long> firstSeen = new HashMap<>();
    private static List<ArmorStand> fish = List.of();
    private static List<String> lampLines = List.of();
    private static Object lastLevel;
    private static int ticks;
    private static HudEditElement hud;

    private CarnivalShootout() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("carnivalLampTimer", "Carnival Lamp Timer").ctx(HudEditElement.HudCtx.ANY).section(Carnival.CATEGORY)
                .enabled(() -> cfg().carnivalZombieShootout && cfg().carnivalShootoutLampTimer)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(1))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        Feature.of("CarnivalShootout").section(Carnival.CATEGORY)
                .when(() -> cfg().carnivalZombieShootout || cfg().carnivalCatchAFish)
                .tick(CarnivalShootout::tick)
                .serverChat(CarnivalShootout::onChat)
                .world(CarnivalShootout::render)
                .hudHiddenInInventory("carnivallamptimer", CarnivalShootout::renderHud)
                .config(Carnival.CATEGORY, b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Carnival");
                    b.toggle("Zombie Shootout Helper", () -> c.carnivalZombieShootout, v -> c.carnivalZombieShootout = v)
                            .desc("Carnival Zombie Shootout: zombies glow in their armor colour (leather/iron/gold/diamond), lit redstone lamps are boxed. Needs the Chivalrous Carnival perk.")
                            .tags("carnival", "zombie", "shootout", "lamp");
                    b.toggle("  Only Highest Value", () -> c.carnivalShootoutHighestOnly, v -> c.carnivalShootoutHighestOnly = v);
                    b.toggle("  Despawn Timers", () -> c.carnivalShootoutTimers, v -> c.carnivalShootoutTimers = v);
                    b.toggle("  Line To Lamp", () -> c.carnivalShootoutLampLine, v -> c.carnivalShootoutLampLine = v);
                    b.toggle("  Lamp Timer HUD", () -> c.carnivalShootoutLampTimer, v -> c.carnivalShootoutLampTimer = v)
                            .desc("Time until the lit lamp goes out again. Position in /tmap gui");
                    b.toggle("Catch a Fish Helper", () -> c.carnivalCatchAFish, v -> c.carnivalCatchAFish = v)
                            .desc("Carnival Catch a Fish: the yellow (bonus) fish glow.")
                            .tags("carnival", "fish", "yellow");
                });
    }

    private static void onChat(String raw) {
        if (!Carnival.inArea()) return;
        String s = StringUtil.stripTrim(raw);
        if (s.equals("[NPC] Carnival Cowboy: Good luck, pal!")) {
            started = true;
            firstSeen.clear();
        } else if (s.equals("Zombie Shootout")) {
            reset();
        }
    }

    private static void reset() {
        started = false;
        lamp = null;
        targets = List.of();
        firstSeen.clear();
        lampLines = List.of();
    }

    static void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!started || !oldState.is(Blocks.REDSTONE_LAMP) || !newState.is(Blocks.REDSTONE_LAMP)) return;
        boolean was = oldState.getValue(BlockStateProperties.LIT), now = newState.getValue(BlockStateProperties.LIT);
        if (!was && now) {
            lamp = pos.immutable();
            lampAt = System.currentTimeMillis();
        } else if (was && !now && pos.equals(lamp)) {
            lamp = null;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
            fish = List.of();
        }
        TeslaMapsConfig c = cfg();
        boolean area = Carnival.inArea();
        if (!area && started) reset();

        if (area && c.carnivalZombieShootout && started) {
            for (Target t : targets) if (t.zombie().isAlive()) EntityGlow.mark(t.zombie(), t.type().color);
        }
        if (area && c.carnivalCatchAFish) {
            for (ArmorStand s : fish) if (s.isAlive()) EntityGlow.mark(s, 0xFFFF55);
        }
        if (++ticks % 2 != 0) return;

        if (area && c.carnivalZombieShootout && started) scanZombies(mc, c);
        else if (!targets.isEmpty()) targets = List.of();

        if (area && c.carnivalCatchAFish && mc.player != null && FISH_AREA.contains(mc.player.position())) scanFish(mc);
        else if (!fish.isEmpty()) fish = List.of();

        if (c.carnivalShootoutTimers) {
            long now = System.currentTimeMillis();
            for (Target t : targets) {
                Long seen = firstSeen.get(t.zombie().getId());
                long left = seen == null ? 0 : t.type().lifetime - (now - seen);
                t.label = left <= 0 ? null : prefix(left, t.type().lifetime) + (left / 100) / 10.0 + "s";
            }
        }
        if (lamp != null && System.currentTimeMillis() - lampAt > 8000) lamp = null;
        if (lamp != null && c.carnivalShootoutLampTimer) {
            long left = 6000 - (System.currentTimeMillis() - lampAt);
            lampLines = List.of("§6Lamp disappears in " + prefix(left, 6000) + String.format("%.1fs", Math.max(0, left) / 1000.0));
        } else if (!lampLines.isEmpty()) {
            lampLines = List.of();
        }
    }

    private static void scanZombies(Minecraft mc, TeslaMapsConfig c) {
        List<Target> all = new ArrayList<>();
        Type max = null;
        for (Zombie z : mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(50))) {
            if (z.getHealth() <= 0) continue;
            Type t = typeOf(z);
            if (t == null) continue;
            all.add(new Target(z, t));
            if (max == null || t.points > max.points) max = t;
            firstSeen.putIfAbsent(z.getId(), System.currentTimeMillis());
        }
        if (c.carnivalShootoutHighestOnly && max != null) {
            Type m = max;
            all.removeIf(t -> t.type() != m);
        }
        targets = all;
        if (firstSeen.size() > 200) firstSeen.clear();
    }

    private static Type typeOf(Zombie z) {
        ItemStack head = z.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = z.getItemBySlot(EquipmentSlot.CHEST);
        for (Type t : Type.values()) if (head.is(t.helmet) || chest.is(t.chest)) return t;
        return null;
    }

    private static void scanFish(Minecraft mc) {
        List<ArmorStand> out = new ArrayList<>();
        for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, FISH_AREA.inflate(4))) {
            if (YELLOW_FISH.equals(SoulweaverHider.headTextureUrl(s.getItemBySlot(EquipmentSlot.HEAD)))) out.add(s);
        }
        fish = out;
    }

    private static String prefix(long left, long total) {
        if (left > total * 2 / 3) return "§a";
        if (left > total / 3) return "§e";
        return "§c";
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!started || !cfg().carnivalZombieShootout || !Carnival.inArea()) return;
        TeslaMapsConfig c = cfg();
        BlockPos l = lamp;
        if (l != null) {
            AABB box = new AABB(l);
            ESPRenderer.drawFilledBox(pose, box, 0x66FF0000, cam, true);
            ESPRenderer.drawBoxOutline(pose, box, 0xFFFF0000, 3f, cam, true);
            if (c.carnivalShootoutLampLine) ESPRenderer.drawTracerFromCamera(pose, box.getCenter(), 0xFFFF0000, cam, true, 3f);
        }
        if (!c.carnivalShootoutTimers) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Target t : targets) {
            Zombie z = t.zombie();
            String label = t.label;
            if (label == null || !z.isAlive()) continue;
            float h = z.isBaby() ? z.getBbHeight() / 2 : z.getBbHeight();
            ESPRenderer.drawText(pose, label, z.getPosition(pt).add(0, h + 0.5, 0), 1.25f, cam);
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lampLines;
        if (l.isEmpty() || hud == null || !cfg().carnivalZombieShootout) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), l, TextHud.width(l));
    }
}
