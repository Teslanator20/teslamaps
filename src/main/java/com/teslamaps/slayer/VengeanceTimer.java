package com.teslamaps.slayer;

// After Zen's VengTimer.kt and VengDamage.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VengeanceTimer {

    private static final Pattern DAMAGE = Pattern.compile("^\\D{0,3}(\\d{1,3}(?:,\\d{3})+|\\d{6,})\\D{0,3}$");
    private static final String SAMPLE = "§6Veng proc: §f4.2s";

    private static long hitMs;
    private static boolean usedThisFight;
    private static String line;
    private static final IntOpenHashSet seen = new IntOpenHashSet();
    private static HudEditElement hud;

    private VengeanceTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("vengeanceTimer", "Vengeance Timer").section("Combat")
                .enabled(() -> cfg().vengeanceTimer)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) onAttack(entity);
            return InteractionResult.PASS;
        });

        Feature.of("VengeanceTimer").section("Combat").when(() -> cfg().vengeanceTimer)
                .tick(VengeanceTimer::tick)
                .hud("vengeancetimer", VengeanceTimer::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Vengeance Timer (Pyrochaos)", () -> c.vengeanceTimer, v -> c.vengeanceTimer = v)
                            .desc("First Pyrochaos Dagger hit on your own Inferno Demonlord starts a \"Veng proc\" countdown. Move it in /tmap gui.")
                            .tags("slayer", "inferno", "blaze", "dagger", "veng");
                    b.slider("  Proc Delay (s)", 1f, 15f, () -> c.vengeanceProcSeconds, v -> c.vengeanceProcSeconds = v, 1);
                    b.toggle("  Vengeance Damage in Chat", () -> c.vengeanceDamageChat, v -> c.vengeanceDamageChat = v)
                            .desc("Damage splashes over 500k within 5 blocks of your boss are written to chat as \"Veng DMG: N\".");
                });
    }

    private static boolean ownInferno() {
        String name = SlayerHUD.bossName();
        return SlayerHUD.ownBoss() && name != null && name.startsWith("Inferno");
    }

    private static void onAttack(Entity target) {
        TeslaMapsConfig c = cfg();
        if (!c.vengeanceTimer || !c.section("Combat")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target != SlayerHUD.getBossEntity() || !ownInferno()) return;
        if (!mc.player.getMainHandItem().getHoverName().getString().contains("Pyrochaos Dagger")) return;
        usedThisFight = true;
        if (hitMs == 0) hitMs = System.currentTimeMillis();
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        Entity boss = SlayerHUD.getBossEntity();
        boolean fight = boss != null && boss.isAlive() && ownInferno();
        if (!fight) {
            hitMs = 0;
            usedThisFight = false;
            line = null;
            if (!seen.isEmpty()) seen.clear();
            return;
        }
        long left = hitMs == 0 ? 0 : hitMs + (long) (c.vengeanceProcSeconds * 1000) - System.currentTimeMillis();
        if (hitMs != 0 && left <= 0) hitMs = 0;
        line = hitMs == 0 ? null : "§6Veng proc: §f" + (left / 1000) + "." + (left % 1000 / 100) + "s";

        if (!c.vengeanceDamageChat || !usedThisFight) return;
        Minecraft mc = Minecraft.getInstance();
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            if (seen.contains(s.getId())) continue;
            if (s.distanceToSqr(boss) > 25) continue;
            seen.add(s.getId());
            Matcher m = DAMAGE.matcher(StringUtil.stripTrim(s.getName().getString()));
            if (!m.find()) continue;
            long dmg;
            try {
                dmg = Long.parseLong(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (dmg <= 500_000 || mc.player == null) continue;
            mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §cVeng DMG: §f" + m.group(1)));
        }
        if (seen.size() > 4096) seen.clear();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
