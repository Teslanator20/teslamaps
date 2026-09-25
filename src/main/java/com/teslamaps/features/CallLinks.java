package com.teslamaps.features;

// Ported from Skyblocker's CallMismyla / RedialOnBadSignal / CallTrevor
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CallLinks {

    private static final Pattern MISMYLA = Pattern.compile("^[\\w' ]+ Commission Complete! Visit the King to claim your rewards!$");
    private static final Pattern FRED = Pattern.compile("^\\[NPC] (Fred): ✆ .*$");
    private static final String TREVOR = "Return to the Trapper soon to get a new animal to hunt!";

    private CallLinks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CallLinks").section("Chat").when(() -> cfg().callLinks)
                .serverChat(CallLinks::onChat)
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Call Links");
                    b.toggle("Clickable /call Links", () -> c.callLinks, v -> c.callLinks = v)
                            .desc("Adds a clickable chat line to call the right Abiphone contact.");
                    b.toggle("  Queen Mismyla (commission done)", () -> c.callLinkMismyla, v -> c.callLinkMismyla = v);
                    b.toggle("  Redial Fred (bad signal)", () -> c.callLinkFred, v -> c.callLinkFred = v);
                    b.toggle("  Trevor (new animal)", () -> c.callLinkTrevor, v -> c.callLinkTrevor = v);
                });
    }

    private static void onChat(String raw) {
        TeslaMapsConfig c = cfg();
        String t = StringUtil.stripTrim(raw);
        if (c.callLinkMismyla && t.endsWith("Visit the King to claim your rewards!") && MISMYLA.matcher(t).matches()) {
            if (!SkyblockUtils.isInArea("Mineshaft")) send("Click here to call Queen Mismyla!", "mismyla");
        } else if (c.callLinkFred && t.startsWith("[NPC] Fred: ") && raw.contains("§k")) {
            Matcher m = FRED.matcher(t);
            if (m.matches()) send("Click here to redial " + m.group(1) + "!", m.group(1));
        } else if (c.callLinkTrevor && t.equals(TREVOR)) {
            send("Click here to call Trevor the Trapper!", "trevor");
        }
    }

    private static void send(String text, String who) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] ").append(Component.literal(text)
                    .withStyle(s -> s.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent.RunCommand("/call " + who))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("/call " + who))))));
        });
    }
}
