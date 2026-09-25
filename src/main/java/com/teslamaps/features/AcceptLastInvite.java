package com.teslamaps.features;

// Ported from SkyHanni's commands/AcceptLastPartyInvite.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AcceptLastInvite {

    private static final Pattern INVITE = Pattern.compile("(?m)^(?:\\[[^\\]]+] )?(\\w{1,16}) has invited you to join their party!");
    private static final Pattern EXPIRED = Pattern.compile("^The party invite from (?:\\[[^\\]]+] )?(\\w{1,16}) has expired\\.");

    private static String lastInviter = "";

    private AcceptLastInvite() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(AcceptLastInvite::allow);
        Feature.of("AcceptLastInvite").section("Party").when(() -> cfg().acceptLastInvite)
                .serverChat(AcceptLastInvite::onChat)
                .config("Party", b -> b.toggle("Accept Last Party Invite", () -> cfg().acceptLastInvite, v -> cfg().acceptLastInvite = v)
                        .desc("/p accept or /party accept without a name accepts the last invite you got"));
    }

    private static void onChat(String raw) {
        String t = StringUtil.strip(raw);
        if (t.contains("has invited you to join their party!")) {
            Matcher m = INVITE.matcher(t.trim());
            if (m.find()) lastInviter = m.group(1);
            return;
        }
        Matcher m = EXPIRED.matcher(t.trim());
        if (m.find() && m.group(1).equalsIgnoreCase(lastInviter)) lastInviter = "";
    }

    private static boolean allow(String command) {
        if (!cfg().acceptLastInvite || !cfg().section("Party")) return true;
        String c = command.trim().toLowerCase(Locale.ROOT);
        if (!c.equals("p accept") && !c.equals("party accept")) return true;
        if (lastInviter.isEmpty()) {
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.literal("§a[TeslaMaps] §7There is no party invite to accept!"));
            return false;
        }
        String who = lastInviter;
        lastInviter = "";
        TickScheduler.schedule(1, () -> ChatQueue.command("party accept " + who));
        return false;
    }
}
