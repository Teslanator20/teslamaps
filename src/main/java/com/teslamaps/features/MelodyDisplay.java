package com.teslamaps.features;

// Ported from NoammAddons' MelodyDisplay.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.PlayerChat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

public class MelodyDisplay {

    private static final String SAMPLE = "§bSteve §fhas §e2/3 §fmelody";

    private static String line = null;
    private static long shownAt = 0;
    private static HudEditElement hud;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("melodyDisplay", "Melody Display").ctx(HudEditElement.HudCtx.DUNGEON)
                .section("Puzzles & Terminals")
                .enabled(() -> cfg().melodyDisplay)
                .size(() -> Minecraft.getInstance().font.width(SAMPLE), () -> 9)
                .scaleRange(0.5f, 4.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE));
        Feature.of("MelodyDisplay").section("Puzzles & Terminals").when(() -> cfg().melodyDisplay)
                .tick(MelodyDisplay::tick)
                .chat(MelodyDisplay::onChat)
                .hud("melodydisplay", MelodyDisplay::render);
    }

    private static void onChat(String raw) {
        if (!TerminalProgress.inP3() || raw.indexOf('>') < 0) return;
        PlayerChat.Msg m = PlayerChat.parse(raw);
        if (m == null || m.channel() != PlayerChat.Channel.PARTY || m.own()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && m.author().equals(mc.player.getName().getString())) return;
        String msg = m.message();
        // 3 rows since the october melody change; old 4-row senders still parse
        int progress = -1, max = 3;
        for (int i = 3; i >= 0 && progress < 0; i--) if (msg.contains(i + "/3")) progress = i;
        if (progress < 0 && (msg.contains("67%") || msg.contains("66%"))) progress = 2;
        if (progress < 0 && msg.contains("33%")) progress = 1;
        if (progress < 0) {
            max = 4;
            for (int i = 4; i >= 0; i--) {
                if (msg.contains(i + "/4") || msg.contains((i * 25) + "%")) { progress = i; break; }
            }
        }
        if (progress < 0) return;
        line = TerminalProgress.classColor(m.author()) + m.author() + " §fhas " + (progress == max ? "§a" : "§e")
                + progress + "/" + max + " §fmelody";
        shownAt = System.currentTimeMillis();
        if (cfg().melodyDisplaySound) LoudSound.play(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    private static void tick() {
        if (line != null && (System.currentTimeMillis() - shownAt > cfg().melodyDisplayDuration * 1000 || !TerminalProgress.inP3())) {
            line = null;
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        String l = line;
        if (l == null || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
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
