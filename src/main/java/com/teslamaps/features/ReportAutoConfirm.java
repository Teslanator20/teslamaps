package com.teslamaps.features;

// Idea from SkyblockExtras' "Auto Confirm Reports"
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** After your own /report, answers Hypixel's "Please type /report confirm" prompt. */
public final class ReportAutoConfirm {

    private static final long WINDOW_MS = 60_000L;
    private static long lastReport = 0L;
    private static int confirmIn = -1;

    private ReportAutoConfirm() {}

    public static void register() {
        ClientSendMessageEvents.COMMAND.register(cmd -> {
            String low = cmd.trim().toLowerCase(Locale.ROOT);
            if (low.startsWith("report ") && !low.startsWith("report confirm")) lastReport = System.currentTimeMillis();
        });
        Feature.of("ReportAutoConfirm").section("Chat")
                .when(() -> TeslaMapsConfig.get().reportAutoConfirm)
                .serverChat(ReportAutoConfirm::onChat)
                .tick(ReportAutoConfirm::tick)
                .config("Chat", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.toggle("Auto Confirm /report", () -> c.reportAutoConfirm, v -> c.reportAutoConfirm = v)
                            .desc("After you send /report <player>, the /report confirm Hypixel asks for is sent for you.")
                            .tags("report", "confirm");
                });
    }

    private static void onChat(String text) {
        String t = StringUtil.strip(text).trim();
        if (!t.startsWith("Please type /report confirm")) return;
        if (System.currentTimeMillis() - lastReport > WINDOW_MS) return;
        lastReport = 0L;
        confirmIn = 10;
    }

    private static void tick() {
        if (confirmIn < 0) return;
        if (--confirmIn > 0) return;
        confirmIn = -1;
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.sendCommand("report confirm");
    }
}
