package com.teslamaps.features.mobvisuals;

// After SkyHanni's combat/mobs/MobHighlight.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.DeadMobHider;
import com.teslamaps.features.theend.EndProtector;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Chest Zealots, Zealots and Bruisers, Runic and Corrupted mobs, Arachne's Keeper glow. */
public final class MobHighlights {

    private static final int CHEST = 0x55FF55, ZEALOT = 0x00AAAA,
            RUNIC = 0xFF55FF, CORRUPTED = 0xAA00AA, KEEPER = 0x0000AA;
    private static final TextColor PURPLE = TextColor.fromLegacyFormat(ChatFormatting.DARK_PURPLE);

    private record Mark(Entity e, int color) {}

    private static List<Mark> marks = List.of();
    private static int ticks;

    private MobHighlights() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MobHighlights").section("Mob Visuals").legit().when(() -> cfg().mobHighlights)
                .tick(MobHighlights::tick)
                .config("Mob Visuals", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Mob Highlights", () -> c.mobHighlights, v -> c.mobHighlights = v).legit()
                            .desc("Glow for special mobs, each one switchable. Special Zealots: The End > Special Zealot Highlight")
                            .tags("zealot", "special zealot", "chest zealot", "bruiser", "runic", "corrupted", "arachne", "keeper");
                    b.toggle("  Chest Zealot", () -> c.mobHighlightChestZealot, v -> c.mobHighlightChestZealot = v);
                    b.toggle("  Zealots & Bruisers", () -> c.mobHighlightZealot, v -> c.mobHighlightZealot = v);
                    b.toggle("  Runic Mobs", () -> c.mobHighlightRunic, v -> c.mobHighlightRunic = v);
                    b.toggle("  Corrupted Mobs", () -> c.mobHighlightCorrupted, v -> c.mobHighlightCorrupted = v);
                    b.toggle("  Arachne's Keeper", () -> c.mobHighlightArachneKeeper, v -> c.mobHighlightArachneKeeper = v);
                });
    }

    private static void tick() {
        if (ticks++ % 5 == 0) scan(Minecraft.getInstance());
        List<Mark> l = marks;
        for (int i = 0; i < l.size(); i++) {
            Mark m = l.get(i);
            if (!m.e.isRemoved()) EntityGlow.mark(m.e, m.color);
        }
    }

    private static void scan(Minecraft mc) {
        marks = List.of();
        if (!SkyblockUtils.isInSkyblock() || SkyblockUtils.getArea().contains("Rift")) return;
        TeslaMapsConfig c = cfg();
        List<Mark> out = new ArrayList<>();

        if (EndProtector.inArea() && (c.mobHighlightChestZealot || c.mobHighlightZealot)) {
            for (EnderMan e : EntityCache.byType(EnderMan.class)) {
                if (e.isRemoved() || e.isDeadOrDying()) continue;
                BlockState held = e.getCarriedBlock();
                if (held != null && held.is(Blocks.END_PORTAL_FRAME)) continue;
                if (held != null && held.is(Blocks.ENDER_CHEST)) {
                    if (c.mobHighlightChestZealot) out.add(new Mark(e, CHEST));
                } else if (c.mobHighlightZealot && zealotOrBruiser(e)) {
                    out.add(new Mark(e, ZEALOT));
                }
            }
        }

        if (c.mobHighlightRunic || c.mobHighlightCorrupted || c.mobHighlightArachneKeeper) {
            for (ArmorStand tag : EntityCache.namedArmorStands()) {
                Component name = tag.getCustomName();
                if (tag.isRemoved() || name == null || tag.distanceToSqr(mc.player) > 4096) continue;
                String s = name.getString();
                if (!s.contains("[Lv")) continue;
                int color;
                if (c.mobHighlightArachneKeeper && s.contains("Arachne's Keeper")) color = KEEPER;
                else if (c.mobHighlightRunic && startsPurple(name, s)) color = RUNIC;
                else if (c.mobHighlightCorrupted && s.contains("Corrupted")) color = CORRUPTED;
                else continue;
                LivingEntity body = bodyUnder(mc, tag);
                if (body != null) out.add(new Mark(body, color));
            }
        }
        marks = out;
    }

    private static boolean zealotOrBruiser(EnderMan e) {
        double hp = e.getAttributeBaseValue(Attributes.MAX_HEALTH);
        return hp == 13_000 || hp == 65_000 || hp == 52_000 || hp == 260_000;
    }

    // runic mobs carry a dark purple name
    private static boolean startsPurple(Component name, String plain) {
        if (plain.startsWith("§5")) return true;
        Optional<Boolean> first = name.visit((style, text) -> text.isBlank() ? Optional.empty()
                : Optional.of(PURPLE.equals(style.getColor())), Style.EMPTY);
        return first.orElse(false);
    }

    private static LivingEntity bodyUnder(Minecraft mc, ArmorStand tag) {
        var box = tag.getBoundingBox().expandTowards(0, -3.5, 0).inflate(0.8, 0, 0.8);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> !(e instanceof ArmorStand) && e != mc.player && !DeadMobHider.isRealPlayer(e))) {
            double d = e.distanceToSqr(tag);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }
}
