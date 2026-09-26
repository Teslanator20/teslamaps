package com.teslamaps.features;

// Ported from Skyblocker's Reparty.java (member list via /p list + PartyTracker instead of the mod API packet)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Reparty {

    private static final Pattern DISBANDED = Pattern.compile("(?m)^(?:\\[[^]]*] )?(\\w{1,16}) has disbanded the party!$");
    private static final Pattern INVITED = Pattern.compile("(?m)^(?:\\[[^]]*] )?(\\w{1,16}) has invited you to join their party!$");
    private static final long ACCEPT_WINDOW_MS = 60_000L;

    private static long listSentAt = 0L;
    private static String disbander;
    private static long disbandedAt = 0L;
    private static String acceptName;
    private static long acceptAt = 0L;

    private Reparty() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            if (!cfg().repartyCommand) return;
            d.register(ClientCommands.literal("rp").executes(c -> run("rp")));
            d.register(ClientCommands.literal("reparty").executes(c -> run("reparty")));
        });
        Feature.of("Reparty").section("Party").when(() -> cfg().repartyCommand)
                .tickAlways(Reparty::tick);
        Feature.of("RepartyAccept").section("Party").legit().when(() -> cfg().repartyAutoAccept)
                .serverChat(Reparty::onChat)
                .tick(Reparty::acceptTick)
                .config("Party", b -> {
                    b.label("Reparty");
                    b.toggle("Reparty Command (/rp)", () -> cfg().repartyCommand, v -> cfg().repartyCommand = v)
                            .desc("/rp or /reparty: disbands your party and invites everyone again. Leader only. Off: the command goes to the server; switching needs a rejoin.");
                    b.toggle("Auto-Accept Reparty Invites", () -> cfg().repartyAutoAccept, v -> cfg().repartyAutoAccept = v)
                            .desc("After someone disbands, their invite within 60s is accepted for you.")
                            .cheat();
                });
    }

    private static int run(String typed) {
        Minecraft mc = Minecraft.getInstance();
        if (!cfg().repartyCommand || !cfg().section("Party")) {
            if (mc.getConnection() != null) mc.getConnection().send(new ServerboundChatCommandPacket(typed));
            return 1;
        }
        if (listSentAt != 0L) {
            feedback("§7already repartying");
            return 1;
        }
        listSentAt = System.currentTimeMillis();
        Announce.command("p list");
        return 1;
    }

    private static void tick() {
        if (listSentAt == 0L || System.currentTimeMillis() - listSentAt < 1500L) return;
        listSentAt = 0L;
        if (!PartyTracker.inParty() || PartyTracker.size() <= 1) { feedback("§cnot in a party"); return; }
        if (!PartyTracker.isLeader()) { feedback("§cyou are not the party leader"); return; }
        var p = Minecraft.getInstance().player;
        String me = p == null ? "" : p.getGameProfile().name();
        List<String> invite = new ArrayList<>();
        for (String m : PartyTracker.members()) if (!m.equalsIgnoreCase(me)) invite.add(m);
        if (invite.isEmpty()) { feedback("§cnobody to reinvite"); return; }
        feedback("§arepartying " + String.join(", ", invite));
        Announce.command("p disband");
        for (String m : invite) Announce.command("p invite " + m);
    }

    private static void onChat(String raw) {
        String s = StringUtil.strip(raw);
        if (!s.contains("party")) return;
        Matcher m = DISBANDED.matcher(s);
        if (m.find()) {
            var p = Minecraft.getInstance().player;
            if (p != null && p.getGameProfile().name().equalsIgnoreCase(m.group(1))) return;
            disbander = m.group(1);
            disbandedAt = System.currentTimeMillis();
            return;
        }
        m = INVITED.matcher(s);
        if (m.find() && disbander != null && m.group(1).equalsIgnoreCase(disbander)
                && System.currentTimeMillis() - disbandedAt < ACCEPT_WINDOW_MS) {
            acceptName = disbander;
            acceptAt = System.currentTimeMillis() + 600L + (long) (Math.random() * 600L);
            disbander = null;
        }
    }

    private static void acceptTick() {
        if (acceptName == null || System.currentTimeMillis() < acceptAt) return;
        String name = acceptName;
        acceptName = null;
        if (LegitMode.blocksCheats()) return;
        Announce.command("party accept " + name);
    }

    private static void feedback(String msg) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[Reparty] §r" + msg));
    }
}
