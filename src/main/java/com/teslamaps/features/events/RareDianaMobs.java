package com.teslamaps.features.events;

// After SkyHanni's HighlightRareDianaMob.kt / RareDianaMob.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Sphinx, Minos Inquisitor, Manticore and King Minos: highlighted, with a sound when one shows up. */
public final class RareDianaMobs {

    private static final String[] RARE = {"Sphinx", "Minos Inquisitor", "Manticore", "King Minos"};

    private static List<LivingEntity> mobs = List.of();
    private static final Set<UUID> seen = new HashSet<>();
    private static Object lastLevel;
    private static int ticks;

    private RareDianaMobs() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("RareDianaMobs").section("Events").legit().when(() -> cfg().rareDianaMobHighlight)
                .tick(RareDianaMobs::tick)
                .world(RareDianaMobs::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Rare Diana Mob Highlight", () -> c.rareDianaMobHighlight, v -> c.rareDianaMobHighlight = v)
                            .desc("Sphinx, Minos Inquisitor, Manticore and King Minos get a highlight when they spawn")
                            .tags("diana", "inquisitor", "inq", "sphinx", "manticore", "king minos", "mythological");
                    b.espStyle("Mob", () -> c.rareDianaMobStyle);
                    b.toggle("  Sound", () -> c.rareDianaMobSound, v -> c.rareDianaMobSound = v);
                    b.soundDropdown("    Sound Type", SoundOptions.keys(), () -> c.rareDianaMobSoundType, v -> c.rareDianaMobSoundType = v);
                    b.slider("    Pitch", 0.5f, 2f, () -> c.rareDianaMobSoundPitch, v -> c.rareDianaMobSoundPitch = v, 2);
                    b.button("    Test Sound", RareDianaMobs::playSound);
                });
    }

    private static void playSound() {
        TeslaMapsConfig c = cfg();
        LoudSound.play(SoundOptions.resolve(c.rareDianaMobSoundType), 1f, c.rareDianaMobSoundPitch);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            seen.clear();
            mobs = List.of();
        }
        TeslaMapsConfig c = cfg();
        EspStyle style = EspStyle.of(c.rareDianaMobStyle);
        for (LivingEntity e : mobs) if (e.isAlive()) style.tickEntity(e);
        if (ticks++ % 5 != 0) return;
        if (!SkyblockUtils.isInArea("Hub")) {
            mobs = List.of();
            return;
        }
        List<LivingEntity> out = new ArrayList<>();
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            String n = StringUtil.strip(as.getCustomName().getString());
            if (!isRare(n)) continue;
            LivingEntity mob = NametagMobs.mobForTag(as);
            if (mob == null || !mob.isAlive() || out.contains(mob)) continue;
            out.add(mob);
            if (seen.add(mob.getUUID()) && c.rareDianaMobSound) playSound();
        }
        mobs = out;
    }

    private static boolean isRare(String name) {
        for (String r : RARE) if (name.contains(r)) return true;
        return false;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<LivingEntity> l = mobs;
        if (l.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().rareDianaMobStyle);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (int i = 0; i < l.size(); i++) style.drawEntity(pose, l.get(i), cam, pt);
    }
}
