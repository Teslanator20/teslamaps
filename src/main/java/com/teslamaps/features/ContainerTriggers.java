package com.teslamaps.features;

// Ported from Bax's ContainerOpenTrigger.java (AutomationEngine.onScreenOpen)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.ContainerTrigger;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

public class ContainerTriggers {

    public static final String[] ACTIONS = {"Sound", "Title", "Chat", "Close"};

    private static Feature feature;
    private static String lastTitle = "";
    private static long lastAt = 0;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ContainerTriggers").section("Inventory").when(() -> cfg().containerTriggers)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Container Triggers");
                    b.toggle("Container Open Triggers", () -> c.containerTriggers, v -> c.containerTriggers = v)
                            .desc("Sound, title, chat line or close when a menu whose title contains your text opens.");
                    b.button("  Edit Triggers", () -> Minecraft.getInstance().setScreen(
                            new com.teslamaps.screen.ContainerTriggerScreen(b.screen())));
                });
    }

    /** From setScreen TAIL. */
    public static void onScreenSet(Screen screen) {
        if (feature == null || !(screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!feature.active() || cfg().containerTriggerList.isEmpty()) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        long now = System.currentTimeMillis();
        // Hypixel reopens the same menu on page flips
        if (title.equals(lastTitle) && now - lastAt < 1500) { lastAt = now; return; }
        lastTitle = title;
        lastAt = now;
        String low = title.toLowerCase(Locale.ROOT);
        for (ContainerTrigger t : cfg().containerTriggerList) {
            if (t.title == null || t.title.isBlank() || !low.contains(t.title.toLowerCase(Locale.ROOT))) continue;
            run(t, title);
        }
    }

    private static void run(ContainerTrigger t, String title) {
        Minecraft mc = Minecraft.getInstance();
        ExecutionLog.log("Container Trigger: " + t.action + " (" + title + ")");
        switch (t.action == null ? "Sound" : t.action) {
            case "Title" -> {
                mc.gui.setTimes(5, 40, 10);
                mc.gui.setTitle(Component.literal("§e" + title));
            }
            case "Chat" -> {
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[Trigger] §fOpened §e" + title));
            }
            case "Close" -> TickScheduler.schedule(1, () -> {
                if (mc.player != null && !LegitMode.blocksCheats() && mc.screen instanceof AbstractContainerScreen<?>) mc.player.closeContainer();
            });
            default -> LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        }
    }
}
