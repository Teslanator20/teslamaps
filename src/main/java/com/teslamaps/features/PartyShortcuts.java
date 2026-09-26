package com.teslamaps.features;

// Ported from SkyHanni's PartyCommands.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PartyShortcuts {

    private static final Pattern TRANSFER_BY = Pattern.compile(
            "^The party was transferred to (?:\\[[^]]*?] ?)?(\\w{1,16}) by (?:\\[[^]]*?] ?)?(\\w{1,16})$");

    private PartyShortcuts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        return cfg().partyShortcuts && cfg().section("Chat");
    }

    public static void register() {
        Feature.of("PartyShortcuts").section("Chat").when(() -> cfg().partyShortcuts && cfg().reversePtClickable)
                .serverChat(PartyShortcuts::onChat);
        ClientSendMessageEvents.MODIFY_COMMAND.register(PartyShortcuts::modify);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> { if (cfg().partyShortcuts) d.register(ClientCommands.literal("rpt")
                .executes(c -> {
                    reverseTransfer();
                    return 1;
                })
                .then(ClientCommands.literal("message")
                        .executes(c -> {
                            cfg().reversePtMessage = "";
                            TeslaMapsConfig.save();
                            msg("§fTransfer-back message cleared.");
                            return 1;
                        })
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(c -> {
                                    cfg().reversePtMessage = StringArgumentType.getString(c, "text").trim();
                                    TeslaMapsConfig.save();
                                    msg("§fTransfer-back message: §7" + cfg().reversePtMessage);
                                    return 1;
                                })))); });
    }

    static String modify(String command) {
        if (command == null || command.isEmpty() || !on()) return command;
        int sp = command.indexOf(' ');
        String first = (sp < 0 ? command : command.substring(0, sp)).toLowerCase(Locale.ROOT);
        String args = sp < 0 ? "" : command.substring(sp + 1).trim();
        switch (first) {
            case "pko": if (args.isEmpty()) return "party kickoffline"; break;
            case "pw": if (args.isEmpty()) return "party warp"; break;
            case "pp": if (!args.isEmpty()) return "party promote " + args; break;
            default: break;
        }
        if (!cfg().partyKickReason) return command;
        String low = command.toLowerCase(Locale.ROOT);
        String rest;
        if (low.startsWith("party kick ")) rest = command.substring(11).trim();
        else if (low.startsWith("p kick ")) rest = command.substring(7).trim();
        else return command;
        int s2 = rest.indexOf(' ');
        if (s2 < 0) return command;
        String name = rest.substring(0, s2);
        String reason = rest.substring(s2 + 1).trim();
        if (reason.isEmpty()) return command;
        TickScheduler.schedule(10, () -> Announce.command("party kick " + name));
        return "pc Kicking " + name + ": " + reason;
    }

    private static void reverseTransfer() {
        if (!on()) {
            msg("§cEnable Chat > Command Shortcuts > Party Shortcuts first.");
            return;
        }
        String prev = PartyTracker.prevLeader();
        if (prev == null) {
            msg("§cNo previous party leader known.");
            return;
        }
        Announce.command("party transfer " + prev);
        String m = cfg().reversePtMessage;
        if (m != null && !m.isBlank()) Announce.party(m);
    }

    private static void onChat(String raw) {
        String s = StringUtil.strip(raw).trim();
        if (!s.startsWith("The party was transferred to ")) return;
        Matcher m = TRANSFER_BY.matcher(s);
        if (!m.matches()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !m.group(1).equalsIgnoreCase(mc.player.getGameProfile().name())) return;
        String prev = m.group(2);
        Component line = Component.literal("§b[TeslaMaps] §eClick to transfer the party back to " + prev)
                .withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/rpt"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("/party transfer " + prev)
                                .withStyle(ChatFormatting.GRAY))));
        mc.execute(() -> { if (mc.player != null) mc.player.sendSystemMessage(line); });
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
