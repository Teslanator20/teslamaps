package com.teslamaps.slayer;

// Steak/Twinclaws from Skyblocker's StakeIndicator / TwinClawsIndicator,
// ichor and spring highlight from SkyHanni's VampireSlayerFeatures
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BloodfiendHelper {

    private static final String ICHOR = "c0340923a6de4825a176813d133503eff186db0896e32b6704928c2a2bf68422";
    private static final String SPRING = "77f7a7bc8ac86f23ca7bf98afeb76960227e1832fe209a3026f6ceb8bde74f54";

    private static boolean steak = false;
    private static int steakTicks = 0;
    private static boolean claws = false;
    private static int clawGen = 0;
    private static final List<ArmorStand> ichors = new ArrayList<>();
    private static final List<ArmorStand> springs = new ArrayList<>();
    private static int scanTicks = 0;

    private BloodfiendHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BloodfiendHelper").section("Combat")
                .when(() -> cfg().bloodfiendSteakTitle || cfg().bloodfiendTwinclawsTitle || cfg().bloodfiendIchorHighlight)
                .tick(BloodfiendHelper::tick)
                .world(BloodfiendHelper::render)
                .config("Combat", b -> {
                    b.label("Riftstalker Bloodfiend");
                    b.toggle("Steak Stake Title", () -> cfg().bloodfiendSteakTitle, v -> cfg().bloodfiendSteakTitle = v)
                            .desc("\"STEAK!\" while your Bloodfiend can be finished with the Steak Stake");
                    b.toggle("Twinclaws Title", () -> cfg().bloodfiendTwinclawsTitle, v -> cfg().bloodfiendTwinclawsTitle = v)
                            .desc("\"TWINCLAWS\" when your boss starts the Twinclaws attack");
                    b.slider("  Twinclaws Delay (ticks)", 0, 30, () -> (float) cfg().bloodfiendTwinclawsDelay,
                            v -> cfg().bloodfiendTwinclawsDelay = Math.round(v), 0);
                    b.toggle("Ichor / Killer Spring Highlight", () -> cfg().bloodfiendIchorHighlight, v -> cfg().bloodfiendIchorHighlight = v)
                            .desc("Box + label on Blood Ichors and Killer Springs during your fight");
                    b.color("  Ichor Color", () -> cfg().bloodfiendIchorColor, v -> cfg().bloodfiendIchorColor = v);
                    b.color("  Spring Color", () -> cfg().bloodfiendSpringColor, v -> cfg().bloodfiendSpringColor = v);
                    b.toggle("  Line Boss To Killer Spring", () -> cfg().bloodfiendSpringLines, v -> cfg().bloodfiendSpringLines = v)
                            .desc("Line from your boss's head to each Killer Spring tower");
                    b.toggle("Fix Killer Spring Sound Spam", () -> cfg().killerSpringSoundFix, v -> cfg().killerSpringSoundFix = v)
                            .desc("The Rift: drops duplicate wither spawn sounds in the same tick, so the Killer Spring does not choke every other sound");
                });
    }

    private static boolean fighting() {
        String b = SlayerQuest.boss();
        return SlayerQuest.bossActive() && b != null && b.contains("Bloodfiend");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        ArmorStand tag = fighting() ? SlayerHUD.bossTag() : null;
        if (tag != null && !tag.getName().getString().contains("Bloodfiend")) tag = null;

        boolean nowSteak = tag != null && c.bloodfiendSteakTitle && tag.getName().getString().contains("҉");
        if (nowSteak) {
            if (!steak) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.5f);
            if (steakTicks++ % 5 == 0) title(mc, "§c§lSTEAK!", 10);
        } else {
            steakTicks = 0;
        }
        steak = nowSteak;

        boolean nowClaws = false;
        if (tag != null && c.bloodfiendTwinclawsTitle) {
            for (ArmorStand s : NametagMobs.namedStands(tag, 2.5)) {
                if (s.getName().getString().contains("TWINCLAWS")) {
                    nowClaws = true;
                    break;
                }
            }
        }
        if (nowClaws && !claws) {
            int gen = ++clawGen;
            Runnable show = () -> {
                if (gen != clawGen || !claws) return;
                title(Minecraft.getInstance(), "§6§lTWINCLAWS", 30);
                LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
            };
            claws = true;
            if (c.bloodfiendTwinclawsDelay > 0) TickScheduler.schedule(c.bloodfiendTwinclawsDelay, show);
            else show.run();
        }
        if (!nowClaws) claws = false;

        if (++scanTicks % 5 != 0) return;
        ichors.clear();
        springs.clear();
        if (!c.bloodfiendIchorHighlight || !fighting() || mc.player == null) return;
        for (ArmorStand s : EntityCache.armorStands()) {
            if (s.distanceToSqr(mc.player) > 30 * 30) continue;
            String url = SoulweaverHider.headTextureUrl(s.getItemBySlot(EquipmentSlot.HEAD));
            if (url == null) continue;
            if (url.equals(ICHOR)) ichors.add(s);
            else if (url.equals(SPRING)) springs.add(s);
        }
    }

    private static long lastWitherSpawnTick = Long.MIN_VALUE;

    /** SoundPlayMixin: true = drop this duplicate Killer Spring sound. */
    public static boolean muteSpringSound(String id) {
        if (!id.equals("minecraft:entity.wither.spawn") || !SkyblockUtils.isInArea("The Rift")) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        long t = mc.level.getGameTime();
        if (t == lastWitherSpawnTick) return true;
        lastWitherSpawnTick = t;
        return false;
    }

    private static void title(Minecraft mc, String text, int stay) {
        mc.gui.setTimes(0, stay, 3);
        mc.gui.setTitle(Component.literal(text));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (ichors.isEmpty() && springs.isEmpty()) return;
        int ic = TeslaMapsConfig.parseColor(cfg().bloodfiendIchorColor) | 0xFF000000;
        int sc = TeslaMapsConfig.parseColor(cfg().bloodfiendSpringColor) | 0xFF000000;
        draw(pose, cam, ichors, ic, "§4Ichor");
        draw(pose, cam, springs, sc, "§6Spring");
        if (!cfg().bloodfiendSpringLines || springs.isEmpty()) return;
        net.minecraft.world.entity.Entity boss = SlayerHUD.getBossEntity();
        if (boss == null || !boss.isAlive()) return;
        Vec3 eye = boss.getEyePosition(1f);
        for (ArmorStand s : springs) {
            if (s.isAlive()) ESPRenderer.drawLine(pose, eye, s.getPosition(1f).add(0, 1.7, 0), sc, 3.0f, cam, true);
        }
    }

    private static void draw(PoseStack pose, Vec3 cam, List<ArmorStand> list, int color, String label) {
        for (ArmorStand s : list) {
            if (!s.isAlive()) continue;
            Vec3 p = s.getPosition(1f);
            AABB head = new AABB(p.x - 0.35, p.y + 1.35, p.z - 0.35, p.x + 0.35, p.y + 2.05, p.z + 0.35);
            ESPRenderer.drawBoxOutline(pose, head, color, 2.0f, cam, false);
            ESPRenderer.drawText(pose, label, p.add(0, 2.4, 0), 1.0f, cam);
        }
    }
}
