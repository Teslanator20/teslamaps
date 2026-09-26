package com.teslamaps.features.events;

// Ported from SkyHanni's HoppityCallWarning.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LoreCosts;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;

public final class HoppityCallWarning {

    private static final long CALL_MS = 7_000;
    private static final String PICKUP_COMMAND = "selectnpcoption hoppity r_2_1";

    private static long warnUntil;
    private static int ticks;
    private static Object lastLevel;
    private static long lastBlock;
    private static Feature feature;

    private HoppityCallWarning() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Events", "Skyblock", true);
        feature = Feature.of("HoppityCallWarning").section("Events")
                .when(() -> cfg().hoppityCallWarning || cfg().hoppityCallBlockPickup)
                .tick(HoppityCallWarning::tick)
                .serverChat(HoppityCallWarning::onChat)
                .hud("hoppitycallflash", HoppityCallWarning::render)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Hoppity");
                    b.toggle("Hoppity Call Warning", () -> c.hoppityCallWarning, v -> c.hoppityCallWarning = v)
                            .desc("When Hoppity calls you over the Abiphone the screen flashes and a sound plays until you pick up (7s max).")
                            .tags("hoppity", "abiphone", "call", "chocolate", "rabbit");
                    b.toggle("  Flash Screen", () -> c.hoppityCallFlash, v -> c.hoppityCallFlash = v);
                    b.color("  Flash Color", () -> c.hoppityCallFlashColor, v -> c.hoppityCallFlashColor = v);
                    b.toggle("  Sound", () -> c.hoppityCallSound, v -> c.hoppityCallSound = v);
                    b.toggle("Block Pickup Without Coins", () -> c.hoppityCallBlockPickup, v -> c.hoppityCallBlockPickup = v)
                            .desc("Stops opening Hoppity's trade menu from a call while your purse is below the threshold, "
                                    + "with a clickable link to /bank. A second try within 5s goes through.")
                            .tags("hoppity", "purse", "bank");
                    b.slider("  Coin Threshold (M)", 0.25f, 5f, () -> c.hoppityCallCoinThreshold / 1_000_000f,
                            v -> c.hoppityCallCoinThreshold = Math.round(v * 4) * 250_000, 2);
                });
    }

    private static void onChat(String raw) {
        if (!cfg().hoppityCallWarning) return;
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("✆ Hoppity ✆")) {
            if (warnUntil == 0) warnUntil = System.currentTimeMillis() + CALL_MS;
        } else if (t.startsWith("[NPC] Hoppity: ✆")) {
            warnUntil = 0;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            warnUntil = 0;
        }
        if (warnUntil == 0) return;
        if (System.currentTimeMillis() >= warnUntil) {
            warnUntil = 0;
            return;
        }
        if (cfg().hoppityCallSound && ++ticks % 2 == 0) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
    }

    /** Outgoing command packet; true cancels it. Clicked chat options skip ALLOW_COMMAND, so this sits on the packet. */
    public static boolean blockCommand(String command) {
        TeslaMapsConfig c = cfg();
        if (!c.hoppityCallBlockPickup || feature == null || !feature.active()) return false;
        if (!PICKUP_COMMAND.equals(command.trim())) return false;
        if (!SkyblockUtils.inSkyblockCached()) return false;
        long now = System.currentTimeMillis();
        if (now - lastBlock < 5_000) return false;
        long purse = LoreCosts.purse();
        if (purse < 0 || purse >= c.hoppityCallCoinThreshold) return false;
        lastBlock = now;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§c[TeslaMaps] Blocked picking up Hoppity without enough coins! §e[Open Bank]")
                    .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/bank"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to open the bank menu")))));
        });
        return true;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        TeslaMapsConfig c = cfg();
        if (warnUntil == 0 || !c.hoppityCallWarning || !c.hoppityCallFlash) return;
        int alpha = (int) ((2 + Math.sin(System.currentTimeMillis() / 1000.0)) * 255 / 4);
        alpha = Math.max(0, Math.min(255, alpha));
        int rgb = TeslaMapsConfig.parseColor(c.hoppityCallFlashColor) & 0xFFFFFF;
        Minecraft mc = Minecraft.getInstance();
        g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), (alpha << 24) | rgb);
    }
}
