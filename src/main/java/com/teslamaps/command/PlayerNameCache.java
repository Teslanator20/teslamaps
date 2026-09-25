package com.teslamaps.command;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

/**
 * Names for the commands that take a player. Hypixel exposes neither the friend list nor
 * the guild roster to the client, so both come out of the chat answer to /g list and
 * /f list - the guild one by its line markers, the friend one from the click and hover
 * data on the names, which is what makes it exact rather than a guess at the wording.
 */
public class PlayerNameCache {

    private static final String GUILD_START = "§6Guild Name: ";
    private static final String GUILD_OFFLINE = "§eOffline Members: ";
    private static final String GUILD_END = "§b§m-----------------------------------------------------";
    private static final int MAX = 400;

    private static boolean inGuildMessage;
    private static final List<String> pendingGuild = new ArrayList<>();

    /** REGEX-TEST: \n§eClick here to view §bThrowpo§e's profile */
    private static final Pattern FRIEND_HOVER =
            Pattern.compile("\n§eClick here to view §.(?<name>.*)§e's profile");
    /** Fallback for when the hover text carries no § codes. */
    private static final Pattern FRIEND_HOVER_PLAIN =
            Pattern.compile("Click here to view (?<name>\\w{1,16})'s profile");

    /** REGEX-TEST: §aYou are now friends with §r§b[MVP§r§d+§r§b] Throwpo */
    private static final Pattern FRIEND_ADDED =
            Pattern.compile("§aYou are now friends with (?<name>.*)");
    /** REGEX-TEST: §r§eYou removed §r§b[MVP§r§d+§r§b] Throwpo§r§e from your friends list!§r§9§m */
    private static final Pattern FRIEND_REMOVED =
            Pattern.compile(".*\n§r§eYou removed §r(?<name>.*)§e from your friends list!§r§9§m\n.*");

    /** REGEX-TEST: §r§b[MVP§r§c+§r§b] hannibal2§r§a is now a best friend!§r§9§m */
    private static final Pattern BEST_ADDED = Pattern.compile("(?<name>[^\\n]*?)§a is now a best friend!");
    /** REGEX-TEST: §r§b[MVP§r§c+§r§b] hannibal2§r§e is no longer a best friend!§r§9§m */
    private static final Pattern BEST_REMOVED = Pattern.compile("(?<name>[^\\n]*?)§e is no longer a best friend!");

    public static void onChatMessage(Component chatComponent) {
        String message = ChatText.stripHypixelMessage(ChatText.formatted(chatComponent));
        guild(message);
        friends(chatComponent, message);
    }

    // GuildApi.kt: the roster sits between the name header and the closing rule, and the
    // run stops at "Offline Members", so only online members are kept
    private static void guild(String message) {
        if (message.startsWith(GUILD_START)) {
            inGuildMessage = true;
            pendingGuild.clear();
            return;
        }
        if (message.startsWith(GUILD_OFFLINE)) {
            inGuildMessage = false;
            pendingGuild.clear();
            return;
        }
        if (!inGuildMessage) return;

        if (message.equals(GUILD_END)) {
            inGuildMessage = false;
            List<String> guild = TeslaMapsConfig.get().guildNames;
            guild.clear();
            guild.addAll(pendingGuild);
            pendingGuild.clear();
            TeslaMapsConfig.save();
            return;
        }
        if (message.contains("●")) {
            for (String word : message.split("●")) {
                String name = ChatText.cleanPlayerName(word);
                if (!name.isEmpty() && !pendingGuild.contains(name)) pendingGuild.add(name);
            }
        }
    }

    // FriendApi.kt: every name in the list carries a /viewprofile click and a hover that
    // spells the name out, so nothing depends on how the surrounding line is worded
    private static void friends(Component chatComponent, String message) {
        Matcher added = FRIEND_ADDED.matcher(message);
        if (added.matches()) {
            add(TeslaMapsConfig.get().friendNames, ChatText.cleanPlayerName(added.group("name")));
            return;
        }
        Matcher best = BEST_ADDED.matcher(message);
        if (best.find()) {
            add(TeslaMapsConfig.get().bestFriendNames, ChatText.cleanPlayerName(lastLine(best.group("name"))));
            TeslaMapsConfig.save();
            return;
        }
        Matcher noBest = BEST_REMOVED.matcher(message);
        if (noBest.find()) {
            TeslaMapsConfig.get().bestFriendNames.remove(ChatText.cleanPlayerName(lastLine(noBest.group("name"))));
            TeslaMapsConfig.save();
            return;
        }
        Matcher removed = FRIEND_REMOVED.matcher(message);
        if (removed.matches()) {
            TeslaMapsConfig.get().friendNames.remove(ChatText.cleanPlayerName(removed.group("name")));
            TeslaMapsConfig.save();
            return;
        }

        if (!message.contains("Friends")) return;

        boolean found = false;
        for (Component sibling : chatComponent.getSiblings()) {
            Style style = sibling.getStyle();
            if (style.isEmpty()) continue;
            if (!(style.getClickEvent() instanceof ClickEvent.RunCommand run)) continue;
            if (!run.command().startsWith("/viewprofile")) continue;

            String name = hoverName(sibling);
            if (name == null || name.isEmpty()) continue;
            add(TeslaMapsConfig.get().friendNames, name);
            // SkyHanni FriendApi: best friends are bold in /f list
            String first = ChatText.formatted(sibling).trim().split(" ")[0];
            if (first.contains("§l") || style.isBold()) add(TeslaMapsConfig.get().bestFriendNames, name);
            else TeslaMapsConfig.get().bestFriendNames.remove(name);
            found = true;
        }
        if (found) TeslaMapsConfig.save();
    }

    private static String hoverName(Component sibling) {
        if (!(sibling.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover)) return null;
        for (Component line : hover.value().getSiblings()) {
            String raw = ChatText.formatted(line);
            Matcher m = FRIEND_HOVER.matcher(raw);
            if (m.matches()) return ChatText.cleanPlayerName(m.group("name"));
            Matcher plain = FRIEND_HOVER_PLAIN.matcher(StringUtil.strip(raw));
            if (plain.find()) return plain.group("name");
        }
        return null;
    }

    private static String lastLine(String s) {
        int nl = s.lastIndexOf('\n');
        return nl < 0 ? s : s.substring(nl + 1);
    }

    private static void add(List<String> target, String name) {
        if (name.isEmpty() || target.contains(name)) return;
        target.add(name);
        while (target.size() > MAX) target.remove(0);
    }

    /** Guild and friends first, then whoever we saw recently, then the players around us. */
    public static List<String> names() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Set<String> out = new LinkedHashSet<>();
        out.addAll(c.guildNames);
        out.addAll(c.tabCompleteBestFriends ? c.bestFriendNames : c.friendNames);
        out.addAll(c.recentPlayers);

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (var entry : mc.getConnection().getOnlinePlayers()) {
                String name = entry.getProfile().name(); // GameProfile is a record in 26.1.2
                // the tab list on Hypixel is padded with blank and header entries
                if (name != null && !name.isBlank() && !name.startsWith("!")) out.add(name);
            }
        }
        return new ArrayList<>(out);
    }

    /** Only the players on the tab list, i.e. this island. */
    public static List<String> islandPlayers() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return out;
        String self = mc.getUser().getName();
        for (var entry : mc.getConnection().getOnlinePlayers()) {
            String name = entry.getProfile().name();
            if (name != null && !name.isBlank() && !name.startsWith("!") && !name.equals(self)) out.add(name);
        }
        return out;
    }

    public static void clear() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        c.guildNames.clear();
        c.friendNames.clear();
        c.bestFriendNames.clear();
        TeslaMapsConfig.save();
    }

    public static String status() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        List<String> all = names();
        StringBuilder sample = new StringBuilder();
        for (int i = 0; i < Math.min(5, all.size()); i++) {
            if (i > 0) sample.append(", ");
            sample.append(all.get(i));
        }
        return c.guildNames.size() + " guild, " + c.friendNames.size() + " friends, "
                + c.recentPlayers.size() + " recent, " + all.size() + " suggested"
                + (sample.isEmpty() ? "" : " §7(" + sample + ", ...)§f");
    }
}
