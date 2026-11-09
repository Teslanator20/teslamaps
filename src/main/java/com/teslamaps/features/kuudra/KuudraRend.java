package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/phasefour/BackboneTimer.kt, legitcatmod's RendDamage and WpcMod's RendAnnounce.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.item.ItemStack;

/** Bonemerang backbone timer and rend pull damage in P4. */
public final class KuudraRend {

    private static final int BACKBONE_TICKS = 22, BAR = 20, COOLDOWN = 32;

    private static Feature feature;
    private static HudEditElement hud;
    private static long hitAt = -1, cooldownUntil, rendUntil;
    private static int total = BACKBONE_TICKS, barW;
    private static float lastHp = 25_000f;
    private static String bar = "", percent = "";

    private KuudraRend() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("kuudraBackbone", "Backbone Timer").section("Kuudra")
                .enabled(() -> cfg().kuudraBackboneTimer)
                .size(() -> Minecraft.getInstance().font.width(barText(0.5)[0]) + 8, () -> 30)
                .preview((g, mc, x, y, s) -> { String[] t = barText(0.5); draw(g, mc, x, y, s, t[0], t[1]); });
        feature = Feature.of("KuudraRend").section("Kuudra")
                .when(() -> cfg().kuudraBackboneTimer || cfg().kuudraRendDamage)
                .tick(KuudraRend::tick)
                .hud("kuudrabackbone", KuudraRend::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Backbone Timer", () -> c.kuudraBackboneTimer, v -> c.kuudraBackboneTimer = v)
                            .desc("After throwing a Bonemerang a bar fills until the backbone hit (22 ticks), then REND! and a chat line "
                                    + "with your held item. Display only. Move it in /tmap gui.")
                            .tags("bonemerang", "rend", "backbone");
                    b.slider("  Advance Ticks", 0, 6, () -> (float) c.kuudraBackboneAdvance, v -> c.kuudraBackboneAdvance = Math.round(v), 0)
                            .desc("Ends the bar this many ticks early to cover your reaction time");
                    b.toggle("  Sound", () -> c.kuudraBackboneSound, v -> c.kuudraBackboneSound = v);
                    b.toggle("Rend Pull Damage", () -> c.kuudraRendDamage, v -> c.kuudraRendDamage = v)
                            .desc("In Kuudra's lair (P4): 'Someone pulled for X damage' when Kuudra loses more than 1666 HP at once");
                });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && feature.active()) onUse(player.getItemInHand(hand));
            return InteractionResult.PASS;
        });
    }

    private static void onUse(ItemStack held) {
        long now = ServerTickTimer.now();
        if (!cfg().kuudraBackboneTimer || KuudraContext.phase() == KuudraContext.NONE) return;
        if (!StringUtil.strip(held.getHoverName().getString()).contains("Bonemerang") || now < cooldownUntil) return;
        cooldownUntil = now + COOLDOWN;
        total = Math.max(1, BACKBONE_TICKS - cfg().kuudraBackboneAdvance);
        hitAt = now + total;
        rendUntil = 0;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = ServerTickTimer.now();
        if (hitAt > 0) {
            if (now >= hitAt) {
                hitAt = -1;
                rendUntil = now + 20;
                if (cfg().kuudraBackboneSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
                ItemStack held = mc.player.getMainHandItem();
                ItemStack helmet = mc.player.getItemBySlot(EquipmentSlot.HEAD);
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Backbone hit in §f" + ServerTickTimer.seconds(total)
                        + "§7, holding §r" + (held.isEmpty() ? "Nothing" : held.getHoverName().getString())
                        + "§7, wearing §r" + (helmet.isEmpty() ? "no helmet" : helmet.getHoverName().getString())));
            } else {
                String[] t = barText(1.0 - (hitAt - now) / (double) total);
                bar = t[0];
                percent = t[1];
            }
        }
        if (cfg().kuudraRendDamage) rendDamage(mc);
    }

    private static void rendDamage(Minecraft mc) {
        MagmaCube boss = KuudraContext.boss();
        if (KuudraContext.phase() != KuudraContext.LAIR || boss == null) { lastHp = 25_000f; return; }
        float hp = boss.getHealth();
        if (hp > 25_000f) return;
        float diff = lastHp - hp;
        if (diff > 1666f) {
            long start = KuudraContext.runStart();
            String at = start > 0 ? " §7at " + String.format(java.util.Locale.ROOT, "%.2fs", (System.currentTimeMillis() - start) / 1000.0) : "";
            mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] Someone pulled for §a" + ItemPrice.format(diff * 9600.0) + "§c damage" + at));
        }
        lastHp = hp;
    }

    private static String[] barText(double p) {
        p = Math.max(0, Math.min(1, p));
        int filled = (int) Math.round(p * BAR);
        String color = p > 0.85 ? "§a" : p > 0.6 ? "§6" : "§c";
        StringBuilder sb = new StringBuilder("§f§l[");
        for (int i = 0; i < BAR; i++) sb.append(i < filled ? color : "§7").append("§l|");
        sb.append("§f§l]");
        return new String[]{sb.toString(), "§f" + (int) (p * 100) + "%"};
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !cfg().kuudraBackboneTimer) return;
        long now = ServerTickTimer.now();
        if (now < rendUntil) draw(g, mc, hud.x(), hud.y(), hud.scale(), "§a§lREND!", "");
        else if (hitAt > 0) draw(g, mc, hud.x(), hud.y(), hud.scale(), bar, percent);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String top, String bottom) {
        if (barW == 0) barW = mc.font.width(barText(0)[0]);
        int w = barW;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        HudStyle.panel(g, 0, 0, w + 8, 30);
        if (bottom.isEmpty()) {
            pose.pushMatrix();
            pose.translate(4 + w / 2f, 8);
            pose.scale(2, 2);
            g.text(mc.font, top, -mc.font.width(top) / 2, 0, 0xFFFFFFFF, true);
            pose.popMatrix();
        } else {
            g.text(mc.font, top, 4, 5, 0xFFFFFFFF, true);
            g.text(mc.font, bottom, 4 + (w - mc.font.width(bottom)) / 2, 17, 0xFFFFFFFF, true);
        }
        pose.popMatrix();
    }
}
