package com.teslamaps.features;

// After Skysoft's features/combat/BetterShurikens.kt + BetterShurikenReminderMarkers.kt (nametag marker only)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BetterShurikens {

    private static final String MARKER = " ✯";

    private static volatile IntOpenHashSet markedStands = new IntOpenHashSet();
    private static final List<LivingEntity> tagged = new ArrayList<>();
    private static final List<LivingEntity> reminders = new ArrayList<>();
    private static int reminderHash = -1;
    private static List<String> reminderNames = List.of();
    private static Feature feature;
    private static int ticks;

    private BetterShurikens() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("BetterShurikens").section("Combat").when(() -> cfg().betterShurikens)
                .tick(BetterShurikens::tick)
                .world(BetterShurikens::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Shurikens");
                    b.toggle("Better Shurikens", () -> c.betterShurikens, v -> c.betterShurikens = v)
                            .desc("Green ✔ at the feet of mobs that carry your Extremely Real Shuriken debuff (read from Hypixel's ✯ nametag marker).")
                            .tags("shuriken", "debuff", "slayer", "kuudra");
                    b.toggle("  Hide ✯ Nametag Marker", () -> c.betterShurikensHideMarker, v -> c.betterShurikensHideMarker = v);
                    b.mobList("  Reminder Mobs", () -> c.betterShurikensReminder)
                            .desc("Mob names (part of the name is enough) that get a red ✖ while they have no shuriken yet.");
                });
    }

    /** Render thread, from MobIconsMixin: nametag without the ✯ marker. */
    public static Component adjust(Entity entity, Component tag) {
        if (!(entity instanceof ArmorStand) || markedStands.isEmpty() || !markedStands.contains(entity.getId())) return tag;
        if (feature == null || !feature.active() || !cfg().betterShurikensHideMarker) return tag;
        String full = tag.getString();
        if (!full.endsWith(MARKER)) return tag;
        int remaining = full.length() - MARKER.length();
        MutableComponent out = Component.empty();
        for (Component part : tag.toFlatList()) {
            if (remaining <= 0) break;
            String text = part.getString();
            int len = Math.min(remaining, text.length());
            if (len > 0) {
                out.append(Component.literal(text.substring(0, len)).withStyle(part.getStyle()));
                remaining -= len;
            }
        }
        return out;
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        tagged.clear();
        reminders.clear();
        if (!SkyblockUtils.inSkyblockCached()) {
            markedStands = new IntOpenHashSet();
            return;
        }
        List<String> raw = cfg().betterShurikensReminder;
        if (raw.hashCode() != reminderHash) {
            reminderHash = raw.hashCode();
            List<String> names = new ArrayList<>();
            for (String s : raw) if (!s.isBlank()) names.add(s.trim().toLowerCase(Locale.ROOT));
            reminderNames = names;
        }
        IntOpenHashSet marks = new IntOpenHashSet();
        Set<LivingEntity> taggedSet = new HashSet<>();
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            if (s.distanceToSqr(mc.player) > 48 * 48) continue;
            String name = NametagMobs.tagName(s);
            if (name == null) continue;
            if (name.endsWith(MARKER.trim()) && name.length() > 2 && name.charAt(name.length() - 2) == ' ') {
                marks.add(s.getId());
                LivingEntity mob = NametagMobs.mobForTag(s);
                if (mob != null && mob.isAlive() && taggedSet.add(mob)) tagged.add(mob);
                continue;
            }
            if (reminderNames.isEmpty()) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            for (String r : reminderNames) {
                if (!lower.contains(r)) continue;
                LivingEntity mob = NametagMobs.mobForTag(s);
                if (mob != null && mob.isAlive()) reminders.add(mob);
                break;
            }
        }
        reminders.removeIf(taggedSet::contains);
        markedStands = marks;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        for (LivingEntity e : tagged) badge(pose, cam, e, "§a§l✔");
        for (LivingEntity e : reminders) badge(pose, cam, e, "§c§l✖");
    }

    private static void badge(PoseStack pose, Vec3 cam, LivingEntity e, String text) {
        if (!e.isAlive()) return;
        Vec3 p = ESPRenderer.lerpBox(e).getBottomCenter().add(0, 0.25, 0);
        ESPRenderer.drawText(pose, text, p, 1.5f, cam, true, false);
    }
}
