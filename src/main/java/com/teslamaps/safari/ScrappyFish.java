package com.teslamaps.safari;

// Ported from GanKura's EntityHighlightManager (showSafariFish)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ScrappyFish {

    private static final List<Entity> fish = new ArrayList<>();
    private static int scanTicks = 0;

    private ScrappyFish() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ScrappyFish").section("Critter Safari").when(() -> cfg().safariScrappyFish)
                .tick(ScrappyFish::tick)
                .world(ScrappyFish::render);
        Feature.of("ScrappyFood").section("Critter Safari").legit().when(() -> cfg().safariScrappyFood)
                .tick(ScrappyFish::foodTick)
                .world(ScrappyFish::foodRender)
                .config("Critter Safari", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Scrappy Food");
                    b.toggle("Scrappy Food Decoder", () -> c.safariScrappyFood, v -> c.safariScrappyFood = v).cheat()
                            .desc("Which food each Scrappy wants (Wheat / Fish / Lily Pad), read from the order the three spawned in")
                            .tags("scrappy", "food", "wheat", "fish", "lily");
                    b.color("  Wheat Color", () -> c.safariScrappyWheatColor, v -> c.safariScrappyWheatColor = v);
                    b.color("  Fish Color", () -> c.safariScrappyFishColor, v -> c.safariScrappyFishColor = v);
                    b.color("  Lily Pad Color", () -> c.safariScrappyLilyColor, v -> c.safariScrappyLilyColor = v);
                    b.slider("  Label Size", 0.5f, 3.0f, () -> c.safariScrappyFoodScale, v -> c.safariScrappyFoodScale = v, 1);
                });
    }

    // Ported from Sparkling Mutuals' ScrappyFoods.kt: the three Scrappys spawn 3 entity ids apart, wheat / fish / lily pad
    private enum Food { WHEAT("Wheat"), FISH("Fish"), LILY_PAD("Lily Pad");
        final String label;
        Food(String label) { this.label = label; }
    }

    private static final java.util.Map<java.util.UUID, Integer> bodies = new java.util.LinkedHashMap<>();
    private static final java.util.Set<Integer> otherIds = new java.util.HashSet<>();
    private static Integer base;
    private static boolean invalid;
    private static net.minecraft.world.level.Level foodLevel;
    private static int foodTicks;
    private static final List<Entity> scrappys = new ArrayList<>();

    private static void foodReset() {
        bodies.clear();
        otherIds.clear();
        base = null;
        invalid = false;
        scrappys.clear();
    }

    private static void foodTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != foodLevel) {
            foodReset();
            foodLevel = mc.level;
        }
        if (!SafariData.inSafari()) {
            scrappys.clear();
            return;
        }
        if (++foodTicks < 5) return;
        foodTicks = 0;
        scrappys.clear();
        scrappys.addAll(EntityCache.byType(net.minecraft.world.entity.animal.armadillo.Armadillo.class));
        Integer start = base;
        for (Entity e : scrappys) {
            if (start == null || (e.getId() - start) % 3 == 0 && e.getId() - start >= 0 && e.getId() - start <= 6) {
                bodies.putIfAbsent(e.getUUID(), e.getId());
            }
        }
        if (start != null || invalid) return;
        java.util.Set<Integer> known = new java.util.HashSet<>(bodies.values());
        int room = Math.max(8192 - otherIds.size(), 0);
        for (Entity e : EntityCache.all()) {
            if (room <= 0) break;
            int id = e.getId();
            if (known.contains(id) || otherIds.contains(id)) continue;
            otherIds.add(id);
            room--;
        }
        otherIds.removeAll(known);
        if (known.isEmpty()) return;
        int min = java.util.Collections.min(known);
        List<Integer> fits = new ArrayList<>();
        for (int s : new int[]{min, min - 3, min - 6}) {
            List<Integer> slots = List.of(s, s + 3, s + 6);
            if (!slots.containsAll(known)) continue;
            boolean clean = true;
            for (int id : slots) if (otherIds.contains(id)) { clean = false; break; }
            if (clean) fits.add(s);
        }
        base = fits.size() == 1 ? fits.get(0) : null;
        invalid = fits.isEmpty() || otherIds.size() >= 8192;
        if (base != null || invalid) otherIds.clear();
    }

    private static Food food(Entity e) {
        Integer id = bodies.get(e.getUUID());
        if (id == null || base == null) return null;
        int offset = id - base;
        return offset >= 0 && offset < 7 && offset % 3 == 0 ? Food.values()[offset / 3] : null;
    }

    private static void foodRender(PoseStack pose, Vec3 cam) {
        if (scrappys.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity e : scrappys) {
            if (e.isRemoved()) continue;
            Food f = food(e);
            if (f == null) continue;
            String hex = switch (f) {
                case WHEAT -> c.safariScrappyWheatColor;
                case FISH -> c.safariScrappyFishColor;
                case LILY_PAD -> c.safariScrappyLilyColor;
            };
            Vec3 p = e.getPosition(pt);
            Vec3 at = new Vec3(p.x, p.y + e.getBbHeight() + 0.2, p.z);
            com.teslamaps.render.ESPRenderer.drawText(pose, f.label, at, c.safariScrappyFoodScale, cam, true, true,
                    TeslaMapsConfig.parseColor(hex) | 0xFF000000);
        }
    }

    private static boolean active() {
        if (!SafariData.inSafari()) return false;
        if (!cfg().safariScrappyFishStopCaught) return true;
        SafariData.Critter scrappy = SafariData.byName("Scrappy");
        return scrappy == null || !SafariTracker.hasCaught(scrappy);
    }

    private static void tick() {
        if (!active()) {
            fish.clear();
            return;
        }
        if (++scanTicks >= 10) {
            scanTicks = 0;
            scan();
        }
        fish.removeIf(Entity::isRemoved);
        EspStyle style = EspStyle.of(cfg().safariScrappyFishStyle);
        for (Entity e : fish) style.tickEntity(e);
    }

    private static void scan() {
        fish.clear();
        List<Entity> labels = new ArrayList<>();
        List<Entity> all = EntityCache.all();
        for (Entity e : all) {
            if (!e.hasCustomName() || e instanceof AbstractFish) continue;
            String n = StringUtil.strip(e.getCustomName().getString()).trim();
            if (n.regionMatches(true, 0, "Sparkling ", 0, 10)) n = n.substring(10);
            if (SafariData.byName(n) != null) labels.add(e);
        }
        for (Entity e : all) {
            if (!(e instanceof AbstractFish) || e.isInvisible() || e.hasCustomName()) continue;
            if (critterBody(e, labels)) continue;
            fish.add(e);
        }
    }

    // a fish right under a critter label is that critter, not food
    private static boolean critterBody(Entity e, List<Entity> labels) {
        for (Entity l : labels) {
            if (l.position().distanceToSqr(e.position()) < 9.0) return true;
        }
        return false;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (fish.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().safariScrappyFishStyle);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Entity e : fish) if (!e.isRemoved()) style.drawEntity(pose, e, cam, pt);
    }
}
