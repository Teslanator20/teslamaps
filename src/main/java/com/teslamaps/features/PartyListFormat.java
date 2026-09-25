package com.teslamaps.features;

// Ported from NoammAddons' PartyHelper.kt (handlePartyListParsing / formatPartyList), buttons after Skytils' PartyAddons.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PartyListFormat {

    private static final Pattern START = Pattern.compile("^Party Members \\((\\d+)\\)$");
    private static final Pattern NAME_END = Pattern.compile("(\\w{1,16})\\s*$");

    private record Member(String name, String rank, boolean online, int type) {}

    private static final List<Member> party = new ArrayList<>();
    private static int awaiting;
    private static long awaitingSince;
    private static Feature feature;

    private PartyListFormat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ClientSendMessageEvents.COMMAND.register(cmd -> {
            if (feature == null || !feature.active()) return;
            String c = cmd.trim().toLowerCase(Locale.ROOT);
            if (c.equals("pl") || c.equals("p list") || c.equals("party list") || c.equals("p l")) {
                awaiting = 2;
                awaitingSince = System.currentTimeMillis();
                party.clear();
            }
        });
        feature = Feature.of("PartyListFormat").section("Party").when(() -> cfg().partyListFormat)
                .config("Party", b -> {
                    b.label("Party List");
                    b.toggle("Compact /p list With Buttons", () -> cfg().partyListFormat, v -> cfg().partyListFormat = v)
                            .desc("Rewrites /p list: online dot, rank and name, and as leader click buttons for warp, kick, transfer and more.")
                            .tags("party list", "pl");
                });
    }

    /** From ChatMixin; true = drop the line. */
    public static boolean hide(Component message, String text) {
        if (awaiting <= 0) return false;
        if (feature == null || !feature.active() || System.currentTimeMillis() - awaitingSince > 5000L) {
            awaiting = 0;
            return false;
        }
        String formatted = ChatText.formatted(message);
        String[] plain = StringUtil.strip(text).split("\n", -1);
        String[] fmt = formatted.split("\n", -1);
        boolean any = false;
        for (int i = 0; i < plain.length; i++) {
            if (line(plain[i].trim(), i < fmt.length ? fmt[i] : plain[i])) any = true;
            if (awaiting <= 0) break;
        }
        return any;
    }

    private static boolean line(String u, String f) {
        if (u.isEmpty()) return true;
        if (START.matcher(u).matches()) { party.clear(); return true; }
        int type = u.startsWith("Party Leader: ") ? 0 : u.startsWith("Party Moderators: ") ? 1 : u.startsWith("Party Members: ") ? 2 : -1;
        if (type >= 0) {
            int at = f.indexOf(": ");
            parse(at < 0 ? f : f.substring(at + 2), type);
            return true;
        }
        if (u.startsWith("You are not currently in a party")) {
            party.clear();
            awaiting = 0;
            return false;
        }
        if (u.startsWith("-----")) {
            awaiting--;
            if (awaiting == 0 && !party.isEmpty()) print();
            return true;
        }
        return false;
    }

    private static void parse(String f, int type) {
        String s = f.replace("§r", "");
        for (String seg : s.split("●")) {
            String t = seg.stripTrailing();
            boolean online = true;
            if (t.length() >= 2 && t.charAt(t.length() - 2) == '§') {
                online = t.charAt(t.length() - 1) != 'c';
                t = t.substring(0, t.length() - 2).stripTrailing();
            }
            Matcher m = NAME_END.matcher(StringUtil.strip(t));
            if (!m.find()) continue;
            String name = m.group(1);
            String rank = t.endsWith(name) ? t.substring(0, t.length() - name.length()).trim() : "";
            party.add(new Member(name, rank.isEmpty() ? "§7" : rank + " ", online, type));
        }
    }

    private static void print() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String me = mc.player.getGameProfile().name();
        boolean leader = party.stream().anyMatch(m -> m.type == 0 && m.name.equalsIgnoreCase(me));
        party.sort((a, b) -> a.type - b.type);
        MutableComponent out = Component.literal("§9§m----------------------------------\n")
                .append(Component.literal("  §aParty Members §7(" + party.size() + ")"));
        if (leader) {
            out.append(Component.literal("\n  "));
            out.append(button("§9[Warp] ", "/p warp", "§7Warp the party"));
            out.append(button("§e[All Invite] ", "/p settings allinvite", "§7Toggle all invite"));
            out.append(button("§6[Mute] ", "/p mute", "§7Toggle party mute"));
            out.append(button("§c[Kick Offline] ", "/p kickoffline", "§7Kick everyone offline"));
            out.append(button("§4[Disband]", "/p disband", "§c§lDisbands the party"));
        }
        for (Member m : party) {
            MutableComponent l = Component.literal("\n  " + (m.online ? "§a" : "§c") + "● §r" + m.rank + m.name);
            if (m.type == 0) l.append(Component.literal(" §e(Leader)"));
            else if (m.type == 1) l.append(Component.literal(" §b(Mod)"));
            if (leader && !m.name.equalsIgnoreCase(me)) {
                if (m.type == 2) l.append(button(" §a[Promote]", "/p promote " + m.name, "§7Promote " + m.name + " to moderator"));
                else l.append(button(" §e[Demote]", "/p demote " + m.name, "§7Demote " + m.name + " to member"));
                l.append(button(" §c[Kick]", "/p kick " + m.name, "§7Kick " + m.name));
                l.append(button(" §6[Transfer]", "/p transfer " + m.name, "§7Transfer the party to " + m.name));
            }
            out.append(l);
        }
        out.append(Component.literal("\n§9§m----------------------------------"));
        mc.player.sendSystemMessage(out);
    }

    private static MutableComponent button(String text, String command, String hover) {
        return Component.literal(text).withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }
}
