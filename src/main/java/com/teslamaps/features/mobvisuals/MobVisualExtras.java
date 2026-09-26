package com.teslamaps.features.mobvisuals;

// Ported from GanKura's hideCapturedCritters / keepShownSafari* / showNameplateCapsule, SkyHanni's InvisibugHighlighter.kt (box)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Mob Visuals add-ons: Invisibug box, captured-critter hiding, capsule count on Wumpa / Doomspiral. */
public final class MobVisualExtras {

    private static final Pattern CAPSULE = Pattern.compile("You threw a Critter Capsule at the (.+?)!", Pattern.CASE_INSENSITIVE);
    private static final long RESET_AFTER_MS = 60_000;

    private static int wumpaHits, doomHits;
    private static long wumpaSeen, doomSeen;
    private static final List<Vec3> boxes = new ArrayList<>();
    private static int ticks;

    private MobVisualExtras() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MobVisualExtras").section("Mob Visuals")
                .when(() -> cfg().mobVisuals)
                .tick(MobVisualExtras::tick)
                .world(MobVisualExtras::render)
                .serverChat(MobVisualExtras::onChat)
                .config("Mob Visuals", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("  Invisibug Box", () -> c.mobVisualInvisibugBox, v -> c.mobVisualInvisibugBox = v)
                            .desc("Filled box on Invisibugs found by their crit particles, only while in line of sight (32 blocks)")
                            .tags("invisibug", "galatea", "moonglade", "box");
                    b.color("    Box Color", () -> c.colorInvisibugBox, v -> c.colorInvisibugBox = v);
                    b.toggle("  Hide Caught Safari Critters", () -> c.mobVisualHideCaught, v -> c.mobVisualHideCaught = v)
                            .desc("Critters already caught this Safari run drop out of glow, tracer and nameplate")
                            .tags("safari", "captured", "caught", "hide");
                    b.searchList("    Keep Shown", MobVisualExtras::safariOptions,
                            o -> c.mobVisualKeepShown.contains(SAFARI.getOrDefault(o, "")),
                            o -> {
                                String id = SAFARI.get(o);
                                if (id != null && !c.mobVisualKeepShown.remove(id)) c.mobVisualKeepShown.add(id);
                            });
                    b.toggle("  Capsule Count On Nameplate", () -> c.mobVisualCapsuleCount, v -> c.mobVisualCapsuleCount = v)
                            .desc("Wumpa and Doomspiral: Critter Capsules thrown at it as a second nameplate line")
                            .tags("capsule", "wumpa", "doomspiral", "nameplate");
                });
    }

    private static final Map<String, String> SAFARI = new LinkedHashMap<>();

    private static List<String> safariOptions() {
        if (SAFARI.isEmpty()) {
            for (Mob m : Mob.values()) if (m.area().isSafari()) SAFARI.put(m.plainLabel(), m.name());
        }
        return new ArrayList<>(SAFARI.keySet());
    }

    static String withCapsules(Mob mob, String label) {
        if (!cfg().mobVisualCapsuleCount) return label;
        long now = System.currentTimeMillis();
        if (mob == Mob.WUMPA) {
            if (now - wumpaSeen > RESET_AFTER_MS) wumpaHits = 0;
            wumpaSeen = now;
            return label + "\n§7Capsules: §e" + wumpaHits;
        }
        if (mob == Mob.DOOMSPIRAL) {
            if (now - doomSeen > RESET_AFTER_MS) doomHits = 0;
            doomSeen = now;
            return label + "\n§7Capsules: §e" + doomHits;
        }
        return label;
    }

    private static void onChat(String raw) {
        if (!cfg().mobVisualCapsuleCount) return;
        Matcher m = CAPSULE.matcher(StringUtil.strip(raw).trim());
        if (!m.matches()) return;
        String target = m.group(1).trim();
        if (target.equalsIgnoreCase("Wumpa")) wumpaHits++;
        else if (target.equalsIgnoreCase("Doomspiral")) doomHits++;
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        boxes.clear();
        if (!cfg().mobVisualInvisibugBox || !MobVisuals.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (Entity e : MobVisualBosses.invisibugs()) {
            if (e.isRemoved() || e.distanceToSqr(mc.player) > 32 * 32 || !mc.player.hasLineOfSight(e)) continue;
            boxes.add(e.position());
        }
    }

    private static void render(PoseStack matrices, Vec3 cam) {
        if (boxes.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().colorInvisibugBox);
        if ((color >>> 24) == 0) color |= 0x80000000;
        for (Vec3 p : boxes) {
            AABB box = new AABB(p.x - 0.3, p.y + 0.3, p.z - 0.3, p.x + 0.3, p.y + 0.9, p.z + 0.3);
            ESPRenderer.drawFilledBox(matrices, box, color, cam, false);
            ESPRenderer.drawBoxOutline(matrices, box, color | 0xFF000000, 2f, cam, false);
        }
    }
}
