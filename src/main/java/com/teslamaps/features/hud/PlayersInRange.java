package com.teslamaps.features.hud;

// Ported from Skytils' MiscFeatures.PlayersInRangeHud
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PlayersInRange {

    private static HudEditElement hud;
    private static ItemStack book;
    private static String text = null;
    private static int timer;

    private PlayersInRange() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("playersInRange", "Players In Range")
                .enabled(() -> cfg().playersInRange)
                .size(() -> 20 + Minecraft.getInstance().font.width("69"), () -> 16)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, "69"));
        Feature.of("PlayersInRange").when(() -> cfg().playersInRange)
                .tick(PlayersInRange::tick)
                .hudHiddenInInventory("playersinrange", PlayersInRange::render)
                .config("HUD Widgets", b -> {
                    b.label("Players In Range");
                    b.toggle("Players In Range", () -> cfg().playersInRange, v -> cfg().playersInRange = v)
                            .desc("Real players within 30 blocks (enchanting / experiment bonus). Position in /tmap gui.")
                            .tags("players", "nearby", "range");
                });
    }

    private static void tick() {
        if (++timer % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInSkyblock()) { text = null; return; }
        int n = 0;
        for (Player p : mc.level.players()) {
            if (!(p instanceof RemotePlayer) || p.isInvisible()) continue; // watchdog bot is invisible
            int v = p.getUUID().version();
            if ((v == 4 || v == 1) && p.distanceToSqr(mc.player) <= 900) n++;
        }
        text = String.valueOf(n);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String t = text;
        Minecraft mc = Minecraft.getInstance();
        if (t == null || hud == null || mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), t);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, String t) {
        if (book == null) book = new ItemStack(Items.ENCHANTED_BOOK);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.item(book, 0, 0);
        g.text(mc.font, t, 18, 4, 0xFFFFAA00, true);
        g.pose().popMatrix();
    }
}
